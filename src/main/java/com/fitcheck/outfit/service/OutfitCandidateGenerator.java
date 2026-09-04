package com.fitcheck.outfit.service;

import com.fitcheck.catalog.entity.Product;
import com.fitcheck.catalog.service.ProductSearchService;
import com.fitcheck.catalog.service.ProductStyleTagQueryService;
import com.fitcheck.common.taxonomy.GarmentRole;
import com.fitcheck.identity.entity.Sex;
import com.fitcheck.identity.entity.UserProfile;
import com.fitcheck.identity.service.UserStylePreferenceQueryService;
import com.fitcheck.outfit.dto.CompatibilityScoreBreakdown;
import com.fitcheck.outfit.entity.Outfit;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScoringFunction;
import org.springframework.data.domain.SearchResult;
import org.springframework.data.domain.SearchResults;
import org.springframework.data.domain.Vector;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
@EnableConfigurationProperties(OutfitGenerationProperties.class)
public class OutfitCandidateGenerator {

    private static final String HASH_ALGORITHM = "SHA-256";
    private static final BigDecimal UNLIMITED_PRICE_CEILING = new BigDecimal("1000000");
    private static final double TOP_BOTTOM_PROBABILITY = 0.7;
    private static final double PREFERRED_ANCHOR_PROBABILITY = 0.8;

    private static final Set<String> ALL_GENDERS = Set.of("Men", "Women", "Boys", "Girls", "Unisex");
    private static final Map<Sex, Set<String>> GENDER_FILTERS = Map.of(
            Sex.MALE, Set.of("Men", "Unisex"),
            Sex.FEMALE, Set.of("Women", "Unisex"),
            Sex.OTHER, ALL_GENDERS
    );
    private static final Set<Month> OUTERWEAR_MONTHS = EnumSet.of(
            Month.OCTOBER, Month.NOVEMBER, Month.DECEMBER, Month.JANUARY, Month.FEBRUARY, Month.MARCH);

    private final ProductSearchService productSearchService;
    private final ProductStyleTagQueryService productStyleTagQueryService;
    private final UserStylePreferenceQueryService userStylePreferenceQueryService;
    private final OutfitPersistenceService outfitPersistenceService;
    private final OutfitCompatibilityScorer compatibilityScorer;
    private final OutfitGenerationProperties properties;

    private final Random random;
    private final Clock clock;

    public List<Outfit> generate(UserProfile profile) {
        Set<String> genders = resolveGenders(profile.getSex());
        BigDecimal priceCeiling = resolvePriceCeiling(profile.getAverageBudgetPerOutfit());

        List<Product> topPool = productSearchService.findEligible(GarmentRole.TOP, genders, priceCeiling);
        List<Product> fullBodyPool = productSearchService.findEligible(GarmentRole.FULL_BODY, genders, priceCeiling);
        Set<UUID> preferredProductIds = resolvePreferredProductIds(profile.getUserId());

        GenerationContext context = new GenerationContext(
                genders, priceCeiling, topPool, fullBodyPool, preferredProductIds, new HashSet<>());

        List<Outfit> results = new ArrayList<>();
        int attempts = 0;
        while (results.size() < properties.batchSize() && attempts < properties.maxGenerationAttempts()) {
            attempts++;
            runCycle(context).ifPresent(results::add);
        }

        if (attempts >= properties.maxGenerationAttempts() && results.size() < properties.batchSize()) {
            log.warn("Reached max generation attempts ({}) with {}/{} outfits collected for user {}",
                    properties.maxGenerationAttempts(), results.size(), properties.batchSize(), profile.getUserId());
        }

        return results;
    }

    private Set<UUID> resolvePreferredProductIds(UUID userId) {
        Set<UUID> preferredStyleTagIds = userStylePreferenceQueryService.findPreferredStyleTagIds(userId);
        if (preferredStyleTagIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(productStyleTagQueryService.findProductIdsByStyleTagIds(preferredStyleTagIds));
    }

    private Set<String> resolveGenders(Sex sex) {
        return sex == null ? ALL_GENDERS : GENDER_FILTERS.get(sex);
    }

    private BigDecimal resolvePriceCeiling(BigDecimal averageBudgetPerOutfit) {
        if (averageBudgetPerOutfit == null) {
            return UNLIMITED_PRICE_CEILING;
        }
        return averageBudgetPerOutfit.multiply(BigDecimal.ONE.add(properties.budgetTolerance()));
    }

    private Optional<Outfit> runCycle(GenerationContext context) {
        List<Product> availableTop = filterUnused(context.topPool(), context.usedAnchorIds());
        List<Product> availableFullBody = filterUnused(context.fullBodyPool(), context.usedAnchorIds());

        CoreShape coreShape = rollCoreShape(availableTop, availableFullBody);
        if (coreShape == null) {
            return Optional.empty();
        }

        List<Product> anchorPool = coreShape == CoreShape.TOP_BOTTOM ? availableTop : availableFullBody;
        Product anchor = sampleAnchor(anchorPool, context.preferredProductIds());
        context.usedAnchorIds().add(anchor.getId());
        String anchorOccasion = anchor.getOccasion();

        List<BeamPath> beams = List.of(new BeamPath(List.of(anchor), BigDecimal.ZERO, new LinkedHashMap<>()));

        if (coreShape == CoreShape.TOP_BOTTOM) {
            beams = expandBeams(beams, GarmentRole.BOTTOM, anchorOccasion, context, false);
            if (beams.isEmpty()) {
                return Optional.empty();
            }
        }

        beams = expandBeams(beams, GarmentRole.FOOTWEAR, anchorOccasion, context, false);
        if (beams.isEmpty()) {
            return Optional.empty();
        }

        if (isOuterwearSeason()) {
            beams = expandBeams(beams, GarmentRole.OUTERWEAR, anchorOccasion, context, true);
        }

        beams = expandAccessoryIfAffordable(beams, anchorOccasion, context);

        BeamPath winner = beams.stream().max(Comparator.comparing(BeamPath::score)).orElseThrow();
        List<Product> polished = polish(winner);

        CompatibilityScoreBreakdown breakdown = compatibilityScorer.score(polished);
        return Optional.of(persistOrReuse(polished, breakdown));
    }

    private List<BeamPath> expandBeams(List<BeamPath> beams, GarmentRole role, String anchorOccasion,
                                       GenerationContext context, boolean optional) {
        List<BeamPath> nextGeneration = new ArrayList<>();
        for (BeamPath beam : beams) {
            Vector reference = computeCentroid(beam.selected());
            List<Product> candidates = fetchSlotCandidates(
                    role, context.genders(), context.priceCeiling(), reference, anchorOccasion);
            if (candidates.isEmpty()) {
                if (optional) {
                    nextGeneration.add(beam);
                }
                continue;
            }
            for (Product candidate : candidates) {
                nextGeneration.add(expandWith(beam, role, candidate, candidates));
            }
        }
        return topBeams(nextGeneration, properties.beamWidth());
    }

    private List<BeamPath> expandAccessoryIfAffordable(List<BeamPath> beams, String anchorOccasion, GenerationContext context) {
        List<BeamPath> affordable = new ArrayList<>();
        List<BeamPath> notAffordable = new ArrayList<>();
        for (BeamPath beam : beams) {
            if (context.priceCeiling().subtract(requiredSpend(beam)).compareTo(BigDecimal.ZERO) > 0) {
                affordable.add(beam);
            } else {
                notAffordable.add(beam);
            }
        }
        if (affordable.isEmpty()) {
            return beams;
        }
        List<BeamPath> expanded = expandBeams(affordable, GarmentRole.ACCESSORY, anchorOccasion, context, true);
        List<BeamPath> combined = new ArrayList<>(expanded);
        combined.addAll(notAffordable);
        return topBeams(combined, properties.beamWidth());
    }

    private BeamPath expandWith(BeamPath beam, GarmentRole role, Product candidate, List<Product> slotCandidates) {
        List<Product> selected = new ArrayList<>(beam.selected());
        selected.add(candidate);
        BigDecimal score = compatibilityScorer.score(selected).finalScore();

        Map<GarmentRole, List<Product>> slotCandidatesByRole = new LinkedHashMap<>(beam.slotCandidates());
        slotCandidatesByRole.put(role, slotCandidates);

        return new BeamPath(selected, score, slotCandidatesByRole);
    }

    private List<BeamPath> topBeams(List<BeamPath> candidates, int width) {
        return candidates.stream()
                .sorted(Comparator.comparing(BeamPath::score).reversed())
                .limit(width)
                .toList();
    }

    private BigDecimal requiredSpend(BeamPath beam) {
        return beam.selected().stream()
                .filter(p -> p.getGarmentRole() != GarmentRole.OUTERWEAR)
                .map(Product::getBasePrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<Product> fetchSlotCandidates(GarmentRole role, Set<String> genders, BigDecimal priceCeiling,
                                              Vector referenceVector, String occasion) {
        Limit limit = Limit.of(properties.topKPerSlot());

        SearchResults<Product> withOccasion = productSearchService.findNearestByOccasion(
                role, genders, priceCeiling, occasion, referenceVector, ScoringFunction.cosine(), limit);
        List<Product> candidates = extractProducts(withOccasion);
        if (!candidates.isEmpty()) {
            return candidates;
        }

        SearchResults<Product> withoutOccasion = productSearchService.findNearest(
                role, genders, priceCeiling, referenceVector, ScoringFunction.cosine(), limit);
        return extractProducts(withoutOccasion);
    }

    private List<Product> extractProducts(SearchResults<Product> results) {
        return results.getContent().stream().map(SearchResult::getContent).toList();
    }

    private Vector computeCentroid(List<Product> selected) {
        int dimensions = selected.get(0).getTextEmbedding().length;
        float[] centroid = new float[dimensions];
        for (Product product : selected) {
            float[] embedding = product.getTextEmbedding();
            for (int i = 0; i < dimensions; i++) {
                centroid[i] += embedding[i];
            }
        }
        for (int i = 0; i < dimensions; i++) {
            centroid[i] /= selected.size();
        }
        return Vector.of(centroid);
    }

    private List<Product> polish(BeamPath winner) {
        List<Product> current = new ArrayList<>(winner.selected());
        BigDecimal currentScore = winner.score();

        for (Map.Entry<GarmentRole, List<Product>> entry : winner.slotCandidates().entrySet()) {
            int index = indexOfRole(current, entry.getKey());
            if (index < 0) {
                continue;
            }
            UUID currentPickId = current.get(index).getId();
            for (Product candidate : entry.getValue()) {
                if (candidate.getId().equals(currentPickId)) {
                    continue;
                }
                List<Product> trial = new ArrayList<>(current);
                trial.set(index, candidate);
                BigDecimal trialScore = compatibilityScorer.score(trial).finalScore();
                if (trialScore.compareTo(currentScore) > 0) {
                    current = trial;
                    currentScore = trialScore;
                    currentPickId = candidate.getId();
                }
            }
        }
        return current;
    }

    private int indexOfRole(List<Product> products, GarmentRole role) {
        for (int i = 0; i < products.size(); i++) {
            if (products.get(i).getGarmentRole() == role) {
                return i;
            }
        }
        return -1;
    }

    private Product sampleAnchor(List<Product> pool, Set<UUID> preferredProductIds) {
        List<Product> preferredSubset = pool.stream()
                .filter(p -> preferredProductIds.contains(p.getId()))
                .toList();
        boolean drawFromPreferred = !preferredSubset.isEmpty() && random.nextDouble() < PREFERRED_ANCHOR_PROBABILITY;
        List<Product> drawPool = drawFromPreferred ? preferredSubset : pool;
        return drawPool.get(random.nextInt(drawPool.size()));
    }

    private CoreShape rollCoreShape(List<Product> availableTop, List<Product> availableFullBody) {
        if (availableTop.isEmpty() && availableFullBody.isEmpty()) {
            return null;
        }
        boolean rollTopBottom = random.nextDouble() < TOP_BOTTOM_PROBABILITY;
        if (rollTopBottom && !availableTop.isEmpty()) {
            return CoreShape.TOP_BOTTOM;
        }
        if (!rollTopBottom && !availableFullBody.isEmpty()) {
            return CoreShape.FULL_BODY;
        }
        return availableTop.isEmpty() ? CoreShape.FULL_BODY : CoreShape.TOP_BOTTOM;
    }

    private boolean isOuterwearSeason() {
        return OUTERWEAR_MONTHS.contains(LocalDate.now(clock).getMonth());
    }

    private List<Product> filterUnused(List<Product> pool, Set<UUID> usedIds) {
        return pool.stream().filter(p -> !usedIds.contains(p.getId())).toList();
    }

    private Outfit persistOrReuse(List<Product> selected, CompatibilityScoreBreakdown breakdown) {
        String itemSetHash = computeItemSetHash(selected);

        Optional<Outfit> existing = outfitPersistenceService.findExisting(itemSetHash);
        if (existing.isPresent()) {
            return existing.get();
        }

        try {
            return outfitPersistenceService.saveNew(selected, breakdown, itemSetHash);
        } catch (DataIntegrityViolationException e) {
            return outfitPersistenceService.findExisting(itemSetHash)
                    .orElseThrow(() -> new IllegalStateException(
                            "Outfit insert failed on unique constraint but no existing row found for hash "
                                    + itemSetHash, e));
        }
    }

    private String computeItemSetHash(List<Product> selected) {
        String sortedIds = selected.stream()
                .map(p -> p.getId().toString())
                .sorted()
                .collect(Collectors.joining(","));
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hashBytes = digest.digest(sortedIds.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(HASH_ALGORITHM + " is not available on this JVM", e);
        }
    }

    private enum CoreShape {
        TOP_BOTTOM, FULL_BODY
    }

    private record BeamPath(List<Product> selected, BigDecimal score, Map<GarmentRole, List<Product>> slotCandidates) {
    }

    private record GenerationContext(
            Set<String> genders,
            BigDecimal priceCeiling,
            List<Product> topPool,
            List<Product> fullBodyPool,
            Set<UUID> preferredProductIds,
            Set<UUID> usedAnchorIds
    ) {
    }
}