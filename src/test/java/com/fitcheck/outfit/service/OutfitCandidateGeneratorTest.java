package com.fitcheck.outfit.service;

import com.fitcheck.catalog.entity.Product;
import com.fitcheck.catalog.repository.ProductRepository;
import com.fitcheck.catalog.repository.ProductStyleTagRepository;
import com.fitcheck.common.taxonomy.GarmentRole;
import com.fitcheck.identity.entity.Sex;
import com.fitcheck.identity.entity.UserProfile;
import com.fitcheck.identity.repository.UserStylePreferenceRepository;
import com.fitcheck.outfit.entity.Outfit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.SearchResult;
import org.springframework.data.domain.SearchResults;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutfitCandidateGeneratorTest {

    // July - outside the outerwear window, so required-slot-only tests don't need an OUTERWEAR stub
    private static final Clock SUMMER_CLOCK = Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductStyleTagRepository productStyleTagRepository;

    @Mock
    private UserStylePreferenceRepository userStylePreferenceRepository;

    @Mock
    private OutfitPersistenceService outfitPersistenceService;

    private OutfitCompatibilityScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = new OutfitCompatibilityScorer(
                new OutfitCompatibilityProperties(new BigDecimal("0.5"), new BigDecimal("0.5")));
        lenient().when(userStylePreferenceRepository.findAllByUserId(any())).thenReturn(List.of());
        lenient().when(outfitPersistenceService.saveNew(any(), any(), any()))
                .thenAnswer(invocation -> Outfit.builder()
                        .id(UUID.randomUUID())
                        .itemSetHash(invocation.getArgument(2))
                        .build());
        stubSlotCandidates(GarmentRole.OUTERWEAR, List.of());
        stubSlotCandidates(GarmentRole.ACCESSORY, List.of());
    }

    @Test
    void generate_bothAnchorPoolsEmpty_returnsEmptyListWithoutThrowing() {
        UserProfile profile = profileWith(Sex.OTHER, new BigDecimal("200"));
        stubAnchorPools(List.of(), List.of());

        OutfitCandidateGenerator generator = generatorWithProperties(50, 20, 1);

        assertThat(generator.generate(profile)).isEmpty();
    }

    @Test
    void generate_requiredSlotNeverHasCandidates_everyCycleFailsAndReturnsEmpty() {
        UserProfile profile = profileWith(Sex.OTHER, new BigDecimal("200"));
        stubAnchorPools(List.of(productWithRole(GarmentRole.TOP)), List.of());
        stubSlotCandidates(GarmentRole.BOTTOM, List.of());
        stubSlotCandidates(GarmentRole.FOOTWEAR, List.of());

        OutfitCandidateGenerator generator = generatorWithProperties(5, 20, 1);

        assertThat(generator.generate(profile)).isEmpty();
        verify(outfitPersistenceService, never()).saveNew(any(), any(), any());
    }

    @Test
    void generate_sufficientSupply_returnsExactlyBatchSize() {
        UserProfile profile = profileWith(Sex.OTHER, new BigDecimal("200"));
        List<Product> anchors = List.of(
                productWithRole(GarmentRole.TOP), productWithRole(GarmentRole.TOP), productWithRole(GarmentRole.TOP));
        stubAnchorPools(anchors, List.of());
        stubSlotCandidates(GarmentRole.BOTTOM, List.of(productWithRole(GarmentRole.BOTTOM)));
        stubSlotCandidates(GarmentRole.FOOTWEAR, List.of(productWithRole(GarmentRole.FOOTWEAR)));

        OutfitCandidateGenerator generator = generatorWithProperties(3, 50, 1);

        assertThat(generator.generate(profile)).hasSize(3);
    }

    @Test
    void generate_moreAttemptsThanAnchorsAvailable_stopsWhenAnchorsExhaustedRatherThanLooping() {
        UserProfile profile = profileWith(Sex.OTHER, new BigDecimal("200"));
        List<Product> anchors = List.of(productWithRole(GarmentRole.TOP), productWithRole(GarmentRole.TOP));
        stubAnchorPools(anchors, List.of());
        stubSlotCandidates(GarmentRole.BOTTOM, List.of(productWithRole(GarmentRole.BOTTOM)));
        stubSlotCandidates(GarmentRole.FOOTWEAR, List.of(productWithRole(GarmentRole.FOOTWEAR)));

        // batchSize of 10 can never be reached - only 2 distinct anchors exist
        OutfitCandidateGenerator generator = generatorWithProperties(10, 100, 1);

        assertThat(generator.generate(profile)).hasSize(2);
    }

    @Test
    void generate_usedAnchors_neverRepeatWithinOneInvocation() {
        UserProfile profile = profileWith(Sex.OTHER, new BigDecimal("200"));
        Product anchor1 = productWithRole(GarmentRole.TOP);
        Product anchor2 = productWithRole(GarmentRole.TOP);
        stubAnchorPools(List.of(anchor1, anchor2), List.of());
        stubSlotCandidates(GarmentRole.BOTTOM, List.of(productWithRole(GarmentRole.BOTTOM)));
        stubSlotCandidates(GarmentRole.FOOTWEAR, List.of(productWithRole(GarmentRole.FOOTWEAR)));

        OutfitCandidateGenerator generator = generatorWithProperties(2, 50, 1);
        generator.generate(profile);

        ArgumentCaptor<List<Product>> selectedCaptor = ArgumentCaptor.forClass(List.class);
        verify(outfitPersistenceService, times(2)).saveNew(selectedCaptor.capture(), any(), any());
        List<UUID> anchorsUsed = selectedCaptor.getAllValues().stream()
                .map(selected -> selected.get(0).getId())
                .toList();
        assertThat(anchorsUsed).containsExactlyInAnyOrder(anchor1.getId(), anchor2.getId());
    }

    @Test
    void generate_existingOutfitWithSameHash_isReusedWithoutSavingANewOne() {
        UserProfile profile = profileWith(Sex.OTHER, new BigDecimal("200"));
        stubAnchorPools(List.of(productWithRole(GarmentRole.TOP)), List.of());
        stubSlotCandidates(GarmentRole.BOTTOM, List.of(productWithRole(GarmentRole.BOTTOM)));
        stubSlotCandidates(GarmentRole.FOOTWEAR, List.of(productWithRole(GarmentRole.FOOTWEAR)));
        Outfit existing = Outfit.builder().id(UUID.randomUUID()).build();
        when(outfitPersistenceService.findExisting(any())).thenReturn(Optional.of(existing));

        OutfitCandidateGenerator generator = generatorWithProperties(1, 10, 1);

        assertThat(generator.generate(profile)).containsExactly(existing);
        verify(outfitPersistenceService, never()).saveNew(any(), any(), any());
    }

    @Test
    void generate_concurrentInsertRace_reQueriesAndReusesTheWinner() {
        UserProfile profile = profileWith(Sex.OTHER, new BigDecimal("200"));
        stubAnchorPools(List.of(productWithRole(GarmentRole.TOP)), List.of());
        stubSlotCandidates(GarmentRole.BOTTOM, List.of(productWithRole(GarmentRole.BOTTOM)));
        stubSlotCandidates(GarmentRole.FOOTWEAR, List.of(productWithRole(GarmentRole.FOOTWEAR)));
        Outfit wonByOtherInvocation = Outfit.builder().id(UUID.randomUUID()).build();
        when(outfitPersistenceService.findExisting(any()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(wonByOtherInvocation));
        when(outfitPersistenceService.saveNew(any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate item_set_hash"));

        OutfitCandidateGenerator generator = generatorWithProperties(1, 10, 1);

        assertThat(generator.generate(profile)).containsExactly(wonByOtherInvocation);
    }

    @Test
    void generate_nullBudget_appliesAnEffectivelyUnlimitedPriceCeiling() {
        UserProfile profile = profileWith(Sex.OTHER, null);
        stubAnchorPools(List.of(productWithRole(GarmentRole.TOP)), List.of());
        stubSlotCandidates(GarmentRole.BOTTOM, List.of(productWithRole(GarmentRole.BOTTOM)));
        stubSlotCandidates(GarmentRole.FOOTWEAR, List.of(productWithRole(GarmentRole.FOOTWEAR)));

        OutfitCandidateGenerator generator = generatorWithProperties(1, 10, 1);
        generator.generate(profile);

        ArgumentCaptor<BigDecimal> priceCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(productRepository).findByGarmentRoleAndGenderInAndBasePriceLessThanEqual(
                eq(GarmentRole.TOP), any(), priceCaptor.capture());
        assertThat(priceCaptor.getValue()).isGreaterThan(new BigDecimal("100000"));
    }

    @Test
    void generate_maleSex_resolvesToMenAndUnisexGenders() {
        UserProfile profile = profileWith(Sex.MALE, new BigDecimal("200"));
        stubAnchorPools(List.of(productWithRole(GarmentRole.TOP)), List.of());
        stubSlotCandidates(GarmentRole.BOTTOM, List.of(productWithRole(GarmentRole.BOTTOM)));
        stubSlotCandidates(GarmentRole.FOOTWEAR, List.of(productWithRole(GarmentRole.FOOTWEAR)));

        OutfitCandidateGenerator generator = generatorWithProperties(1, 10, 1);
        generator.generate(profile);

        ArgumentCaptor<Set<String>> gendersCaptor = ArgumentCaptor.forClass(Set.class);
        verify(productRepository).findByGarmentRoleAndGenderInAndBasePriceLessThanEqual(
                eq(GarmentRole.TOP), gendersCaptor.capture(), any());
        assertThat(gendersCaptor.getValue()).containsExactlyInAnyOrder("Men", "Unisex");
    }

    private OutfitCandidateGenerator generatorWithProperties(int batchSize, int maxAttempts, int topKPerSlot) {
        OutfitGenerationProperties properties = new OutfitGenerationProperties(
                batchSize, maxAttempts, new BigDecimal("0.10"), topKPerSlot, 2);
        return new OutfitCandidateGenerator(
                productRepository, productStyleTagRepository, userStylePreferenceRepository,
                outfitPersistenceService, scorer, properties, new Random(42), SUMMER_CLOCK);
    }

    private void stubAnchorPools(List<Product> topPool, List<Product> fullBodyPool) {
        lenient().when(productRepository.findByGarmentRoleAndGenderInAndBasePriceLessThanEqual(eq(GarmentRole.TOP), any(), any()))
                .thenReturn(topPool);
        lenient().when(productRepository.findByGarmentRoleAndGenderInAndBasePriceLessThanEqual(eq(GarmentRole.FULL_BODY), any(), any()))
                .thenReturn(fullBodyPool);
    }

    private void stubSlotCandidates(GarmentRole role, List<Product> candidates) {
        SearchResults<Product> results = new SearchResults<>(
                candidates.stream().map(p -> new SearchResult<>(p, 1.0)).toList());
        lenient().when(productRepository.searchByGarmentRoleAndGenderInAndBasePriceLessThanEqualAndOccasionAndTextEmbeddingNear(
                        eq(role), any(), any(), any(), any(), any(), any()))
                .thenReturn(results);
        lenient().when(productRepository.searchByGarmentRoleAndGenderInAndBasePriceLessThanEqualAndTextEmbeddingNear(
                        eq(role), any(), any(), any(), any(), any()))
                .thenReturn(results);
    }

    private UserProfile profileWith(Sex sex, BigDecimal budget) {
        return UserProfile.builder().userId(UUID.randomUUID()).sex(sex).averageBudgetPerOutfit(budget).build();
    }

    private Product productWithRole(GarmentRole role) {
        return Product.builder()
                .id(UUID.randomUUID())
                .garmentRole(role)
                .primaryColor("black")
                .basePrice(new BigDecimal("50"))
                .occasion("casual")
                .textEmbedding(new float[]{1f, 0f, 0f})
                .build();
    }
}