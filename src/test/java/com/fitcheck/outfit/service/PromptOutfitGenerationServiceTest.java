package com.fitcheck.outfit.service;

import com.fitcheck.catalog.entity.Product;
import com.fitcheck.catalog.service.ProductSearchService;
import com.fitcheck.common.exception.ExternalServiceException;
import com.fitcheck.common.taxonomy.GarmentRole;
import com.fitcheck.identity.entity.Sex;
import com.fitcheck.identity.entity.UserProfile;
import com.fitcheck.identity.service.UserProfileQueryService;
import com.fitcheck.outfit.config.OutfitDiversityProperties;
import com.fitcheck.outfit.config.OutfitGenerationProperties;
import com.fitcheck.outfit.config.OutfitPromptProperties;
import com.fitcheck.outfit.dto.CompatibilityScoreBreakdown;
import com.fitcheck.outfit.dto.OutfitBlueprint;
import com.fitcheck.outfit.dto.OutfitItemView;
import com.fitcheck.outfit.dto.OutfitResponse;
import com.fitcheck.outfit.dto.SlotDescription;
import com.fitcheck.outfit.dto.StructuredPromptQuery;
import com.fitcheck.outfit.entity.Outfit;
import com.fitcheck.outfit.entity.OutfitSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.SearchResult;
import org.springframework.data.domain.SearchResults;
import org.springframework.data.domain.Vector;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromptOutfitGenerationServiceTest {

    @Mock
    private PromptExtractionService promptExtractionService;

    @Mock
    private PromptQueryEmbeddingService promptQueryEmbeddingService;

    @Mock
    private ProductSearchService productSearchService;

    @Mock
    private OutfitCompatibilityScorer outfitCompatibilityScorer;

    @Mock
    private OutfitPersistenceService outfitPersistenceService;

    @Mock
    private OutfitItemQueryService outfitItemQueryService;

    @Mock
    private UserProfileQueryService userProfileQueryService;

    @Mock
    private AiPromptQueryService aiPromptQueryService;

    private OutfitGenderFilterResolver outfitGenderFilterResolver;
    private OutfitItemSetHasher outfitItemSetHasher;
    private BudgetCeilingResolver budgetCeilingResolver;
    private OutfitDiversityProperties diversityProperties;
    private PromptOutfitGenerationService service;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        outfitGenderFilterResolver = new OutfitGenderFilterResolver();
        outfitItemSetHasher = new OutfitItemSetHasher();
        OutfitGenerationProperties generationProperties = new OutfitGenerationProperties(
                50, 500, new BigDecimal("0.10"), 15, 3);
        budgetCeilingResolver = new BudgetCeilingResolver(generationProperties);
        OutfitPromptProperties promptProperties = new OutfitPromptProperties(2, 3, 200, 200, 50);
        diversityProperties = new OutfitDiversityProperties(3);

        service = new PromptOutfitGenerationService(
                promptExtractionService, promptQueryEmbeddingService, productSearchService,
                outfitCompatibilityScorer, outfitGenderFilterResolver, outfitItemSetHasher,
                outfitPersistenceService, outfitItemQueryService, userProfileQueryService,
                aiPromptQueryService, budgetCeilingResolver, promptProperties, diversityProperties);
    }

    @Test
    void generate_extractionFails_logsFailureAndRethrowsWithoutTouchingPersistence() {
        ExternalServiceException failure = new ExternalServiceException("Ollama Cloud prompt extraction failed: boom");
        when(promptExtractionService.extract("bad prompt")).thenThrow(failure);

        assertThatThrownBy(() -> service.generate(userId, "bad prompt", true)).isSameAs(failure);

        verify(aiPromptQueryService).logFailure(userId, "bad prompt", failure.getMessage());
        verify(aiPromptQueryService, never()).logSuccess(any(), any(), any(), anyBoolean(), any());
        verifyNoInteractions(outfitPersistenceService);
        verifyNoInteractions(userProfileQueryService);
    }

    @Test
    void generate_noCandidatesForAnySlot_logsFailureAndThrowsWithoutPersisting() {
        UserProfile profile = profileWith(Sex.OTHER, new BigDecimal("200"));
        when(userProfileQueryService.getById(userId)).thenReturn(profile);
        when(promptExtractionService.extract("nothing matches")).thenReturn(singleTopBottomFootwearQuery());
        when(promptQueryEmbeddingService.embed(any())).thenReturn(Vector.of(new float[]{1f, 0f, 0f}));
        when(productSearchService.findNearest(any(), any(), any(), any(), any(), any())).thenReturn(emptyResults());

        assertThatThrownBy(() -> service.generate(userId, "nothing matches", true))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("No viable product combination");

        verify(aiPromptQueryService).logFailure(eq(userId), eq("nothing matches"), any());
        verifyNoInteractions(outfitPersistenceService);
    }

    @Test
    void generate_successfulPrompt_persistsMatchingItemsAndLogsSuccess() {
        UserProfile profile = profileWith(Sex.MALE, new BigDecimal("200"));
        when(userProfileQueryService.getById(userId)).thenReturn(profile);

        StructuredPromptQuery query = singleTopBottomFootwearQuery();
        when(promptExtractionService.extract("smart casual")).thenReturn(query);
        when(promptQueryEmbeddingService.embed(any())).thenReturn(Vector.of(new float[]{1f, 0f, 0f}));

        Product top = productWithRole(GarmentRole.TOP);
        Product bottom = productWithRole(GarmentRole.BOTTOM);
        Product shoes = productWithRole(GarmentRole.FOOTWEAR);
        when(productSearchService.findNearest(eq(GarmentRole.TOP), any(), any(), any(), any(), any()))
                .thenReturn(resultsOf(top));
        when(productSearchService.findNearest(eq(GarmentRole.BOTTOM), any(), any(), any(), any(), any()))
                .thenReturn(resultsOf(bottom));
        when(productSearchService.findNearest(eq(GarmentRole.FOOTWEAR), any(), any(), any(), any(), any()))
                .thenReturn(resultsOf(shoes));
        when(outfitCompatibilityScorer.score(any())).thenReturn(breakdown("0.8"));

        UUID outfitId = UUID.randomUUID();
        Outfit outfit = Outfit.builder().id(outfitId).build();
        when(outfitPersistenceService.saveOrReuseBatch(any())).thenReturn(List.of(outfit));
        when(outfitItemQueryService.findItemViewsForOutfits(List.of(outfitId))).thenReturn(Map.of(outfitId, List.of()));
        when(outfitItemQueryService.sumBasePriceForOutfits(List.of(outfitId)))
                .thenReturn(Map.of(outfitId, new BigDecimal("120")));

        List<OutfitResponse> responses = service.generate(userId, "smart casual", true);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).outfitId()).isEqualTo(outfitId);
        assertThat(responses.get(0).totalPrice()).isEqualTo(new BigDecimal("120"));

        ArgumentCaptor<List<OutfitPersistenceService.PersistenceCandidate>> candidatesCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(outfitPersistenceService).saveOrReuseBatch(candidatesCaptor.capture());
        List<OutfitPersistenceService.PersistenceCandidate> candidates = candidatesCaptor.getValue();
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).products()).containsExactlyInAnyOrder(top, bottom, shoes);
        assertThat(candidates.get(0).products()).extracting(Product::getGarmentRole)
                .containsExactlyInAnyOrder(GarmentRole.TOP, GarmentRole.BOTTOM, GarmentRole.FOOTWEAR);
        assertThat(candidates.get(0).source()).isEqualTo(OutfitSource.AI_PROMPT);

        verify(aiPromptQueryService).logSuccess(userId, "smart casual", query, true, List.of(outfitId));
        verify(aiPromptQueryService, never()).logFailure(any(), any(), any());

        ArgumentCaptor<Set<String>> gendersCaptor = ArgumentCaptor.forClass(Set.class);
        verify(productSearchService).findNearest(eq(GarmentRole.TOP), gendersCaptor.capture(), any(), any(), any(), any());
        assertThat(gendersCaptor.getValue()).containsExactlyInAnyOrder("Men", "Unisex");
    }

    @Test
    void generate_respectsBudgetCeilingWhenQueryingCandidates() {
        UserProfile profile = profileWith(Sex.OTHER, new BigDecimal("100"));
        when(userProfileQueryService.getById(userId)).thenReturn(profile);
        when(promptExtractionService.extract(anyString())).thenReturn(singleTopBottomFootwearQuery());
        when(promptQueryEmbeddingService.embed(any())).thenReturn(Vector.of(new float[]{1f, 0f, 0f}));
        stubAllSlotCandidates();
        when(outfitCompatibilityScorer.score(any())).thenReturn(breakdown("0.7"));
        stubBatchPersistence();
        stubItemViewsAndPricesFor(BigDecimal.ZERO);

        service.generate(userId, "anything", true);

        ArgumentCaptor<BigDecimal> ceilingCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(productSearchService).findNearest(eq(GarmentRole.TOP), any(), ceilingCaptor.capture(), any(), any(), any());
        assertThat(ceilingCaptor.getValue()).isEqualByComparingTo(new BigDecimal("110.00"));
    }

    @Test
    void generate_multipleBlueprints_returnsBothRankedWithHighestScoreFirst() {
        UserProfile profile = profileWith(Sex.OTHER, new BigDecimal("300"));
        when(userProfileQueryService.getById(userId)).thenReturn(profile);

        OutfitBlueprint topBottomBlueprint = new OutfitBlueprint(List.of(
                new SlotDescription(GarmentRole.TOP, "a shirt"),
                new SlotDescription(GarmentRole.BOTTOM, "trousers"),
                new SlotDescription(GarmentRole.FOOTWEAR, "shoes")));
        OutfitBlueprint fullBodyBlueprint = new OutfitBlueprint(List.of(
                new SlotDescription(GarmentRole.FULL_BODY, "a dress"),
                new SlotDescription(GarmentRole.FOOTWEAR, "heels")));
        StructuredPromptQuery query = new StructuredPromptQuery(List.of(topBottomBlueprint, fullBodyBlueprint));
        when(promptExtractionService.extract(anyString())).thenReturn(query);
        when(promptQueryEmbeddingService.embed(any())).thenReturn(Vector.of(new float[]{1f, 0f, 0f}));

        Product top = productWithRole(GarmentRole.TOP);
        Product bottom = productWithRole(GarmentRole.BOTTOM);
        Product topBottomShoes = productWithRole(GarmentRole.FOOTWEAR);
        Product dress = productWithRole(GarmentRole.FULL_BODY);
        Product heels = productWithRole(GarmentRole.FOOTWEAR);

        when(productSearchService.findNearest(eq(GarmentRole.TOP), any(), any(), any(), any(), any()))
                .thenReturn(resultsOf(top));
        when(productSearchService.findNearest(eq(GarmentRole.BOTTOM), any(), any(), any(), any(), any()))
                .thenReturn(resultsOf(bottom));
        when(productSearchService.findNearest(eq(GarmentRole.FULL_BODY), any(), any(), any(), any(), any()))
                .thenReturn(resultsOf(dress));
        when(productSearchService.findNearest(eq(GarmentRole.FOOTWEAR), any(), any(), any(), any(), any()))
                .thenReturn(resultsOf(topBottomShoes))
                .thenReturn(resultsOf(heels));

        List<Product> topBottomCombination = List.of(top, bottom, topBottomShoes);
        List<Product> fullBodyCombination = List.of(dress, heels);
        when(outfitCompatibilityScorer.score(topBottomCombination)).thenReturn(breakdown("0.6"));
        when(outfitCompatibilityScorer.score(fullBodyCombination)).thenReturn(breakdown("0.9"));

        UUID topBottomOutfitId = UUID.randomUUID();
        UUID fullBodyOutfitId = UUID.randomUUID();
        when(outfitPersistenceService.saveOrReuseBatch(any())).thenAnswer(invocation -> {
            List<OutfitPersistenceService.PersistenceCandidate> candidates = invocation.getArgument(0);
            return candidates.stream()
                    .map(candidate -> candidate.products().contains(dress)
                            ? Outfit.builder().id(fullBodyOutfitId).build()
                            : Outfit.builder().id(topBottomOutfitId).build())
                    .toList();
        });
        when(outfitItemQueryService.findItemViewsForOutfits(any())).thenAnswer(invocation -> {
            List<UUID> ids = invocation.getArgument(0);
            Map<UUID, List<OutfitItemView>> result = new HashMap<>();
            for (UUID id : ids) {
                result.put(id, List.of());
            }
            return result;
        });
        when(outfitItemQueryService.sumBasePriceForOutfits(any())).thenAnswer(invocation -> {
            List<UUID> ids = invocation.getArgument(0);
            Map<UUID, BigDecimal> result = new HashMap<>();
            for (UUID id : ids) {
                result.put(id, new BigDecimal("200"));
            }
            return result;
        });

        List<OutfitResponse> responses = service.generate(userId, "give me two options", true);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).outfitId()).isEqualTo(fullBodyOutfitId);
        assertThat(responses.get(1).outfitId()).isEqualTo(topBottomOutfitId);
        verify(aiPromptQueryService).logSuccess(userId, "give me two options", query, true,
                List.of(fullBodyOutfitId, topBottomOutfitId));

        ArgumentCaptor<List<OutfitPersistenceService.PersistenceCandidate>> candidatesCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(outfitPersistenceService).saveOrReuseBatch(candidatesCaptor.capture());
        assertThat(candidatesCaptor.getValue()).extracting(OutfitPersistenceService.PersistenceCandidate::products)
                .containsExactly(fullBodyCombination, topBottomCombination);
    }

    @Test
    void generate_moreCombinationsThanBatchSize_returnsOnlyTopBatchSize() {
        UserProfile profile = profileWith(Sex.OTHER, new BigDecimal("300"));
        when(userProfileQueryService.getById(userId)).thenReturn(profile);
        OutfitPromptProperties smallBatchProperties = new OutfitPromptProperties(2, 3, 200, 200, 1);
        service = new PromptOutfitGenerationService(
                promptExtractionService, promptQueryEmbeddingService, productSearchService,
                outfitCompatibilityScorer, outfitGenderFilterResolver, outfitItemSetHasher,
                outfitPersistenceService, outfitItemQueryService, userProfileQueryService,
                aiPromptQueryService, budgetCeilingResolver, smallBatchProperties, diversityProperties);

        when(promptExtractionService.extract(anyString())).thenReturn(singleTopBottomFootwearQuery());
        when(promptQueryEmbeddingService.embed(any())).thenReturn(Vector.of(new float[]{1f, 0f, 0f}));

        Product topA = productWithRole(GarmentRole.TOP);
        Product topB = productWithRole(GarmentRole.TOP);
        Product bottom = productWithRole(GarmentRole.BOTTOM);
        Product shoes = productWithRole(GarmentRole.FOOTWEAR);
        when(productSearchService.findNearest(eq(GarmentRole.TOP), any(), any(), any(), any(), any()))
                .thenReturn(resultsOf(topA, topB));
        when(productSearchService.findNearest(eq(GarmentRole.BOTTOM), any(), any(), any(), any(), any()))
                .thenReturn(resultsOf(bottom));
        when(productSearchService.findNearest(eq(GarmentRole.FOOTWEAR), any(), any(), any(), any(), any()))
                .thenReturn(resultsOf(shoes));
        when(outfitCompatibilityScorer.score(any())).thenReturn(breakdown("0.5"));
        stubBatchPersistence();
        stubItemViewsAndPricesFor(BigDecimal.ZERO);

        List<OutfitResponse> responses = service.generate(userId, "two tops to choose from", true);

        assertThat(responses).hasSize(1);
        ArgumentCaptor<List<OutfitPersistenceService.PersistenceCandidate>> candidatesCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(outfitPersistenceService).saveOrReuseBatch(candidatesCaptor.capture());
        assertThat(candidatesCaptor.getValue()).hasSize(1);
    }

    @Test
    void generate_moreScoredCombinationsThanDiversityCapAllows_neverExceedsMaxRepetitionsPerProduct() {
        UserProfile profile = profileWith(Sex.OTHER, new BigDecimal("300"));
        when(userProfileQueryService.getById(userId)).thenReturn(profile);
        when(promptExtractionService.extract(anyString())).thenReturn(singleTopBottomFootwearQuery());
        when(promptQueryEmbeddingService.embed(any())).thenReturn(Vector.of(new float[]{1f, 0f, 0f}));

        Product topA = productWithRole(GarmentRole.TOP);
        Product topB = productWithRole(GarmentRole.TOP);
        Product bottomA = productWithRole(GarmentRole.BOTTOM);
        Product bottomB = productWithRole(GarmentRole.BOTTOM);
        Product shoes = productWithRole(GarmentRole.FOOTWEAR);
        when(productSearchService.findNearest(eq(GarmentRole.TOP), any(), any(), any(), any(), any()))
                .thenReturn(resultsOf(topA, topB));
        when(productSearchService.findNearest(eq(GarmentRole.BOTTOM), any(), any(), any(), any(), any()))
                .thenReturn(resultsOf(bottomA, bottomB));
        when(productSearchService.findNearest(eq(GarmentRole.FOOTWEAR), any(), any(), any(), any(), any()))
                .thenReturn(resultsOf(shoes));
        when(outfitCompatibilityScorer.score(any())).thenReturn(breakdown("0.5"));
        stubBatchPersistence();
        stubItemViewsAndPricesFor(BigDecimal.ZERO);

        service.generate(userId, "two tops two bottoms", true);

        // "shoes" is the only footwear candidate - if all 4 top x bottom combinations were kept,
        // it would be used 4 times. The diversity cap (3) must stop that.
        ArgumentCaptor<List<OutfitPersistenceService.PersistenceCandidate>> candidatesCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(outfitPersistenceService).saveOrReuseBatch(candidatesCaptor.capture());

        long shoesUsageCount = candidatesCaptor.getValue().stream()
                .map(OutfitPersistenceService.PersistenceCandidate::products)
                .flatMap(List::stream)
                .filter(product -> product.getId().equals(shoes.getId()))
                .count();

        assertThat(shoesUsageCount).isLessThanOrEqualTo(3);
    }

    private StructuredPromptQuery singleTopBottomFootwearQuery() {
        return new StructuredPromptQuery(List.of(new OutfitBlueprint(List.of(
                new SlotDescription(GarmentRole.TOP, "a shirt"),
                new SlotDescription(GarmentRole.BOTTOM, "trousers"),
                new SlotDescription(GarmentRole.FOOTWEAR, "shoes")))));
    }

    private void stubAllSlotCandidates() {
        when(productSearchService.findNearest(eq(GarmentRole.TOP), any(), any(), any(), any(), any()))
                .thenReturn(resultsOf(productWithRole(GarmentRole.TOP)));
        when(productSearchService.findNearest(eq(GarmentRole.BOTTOM), any(), any(), any(), any(), any()))
                .thenReturn(resultsOf(productWithRole(GarmentRole.BOTTOM)));
        when(productSearchService.findNearest(eq(GarmentRole.FOOTWEAR), any(), any(), any(), any(), any()))
                .thenReturn(resultsOf(productWithRole(GarmentRole.FOOTWEAR)));
    }

    private void stubBatchPersistence() {
        when(outfitPersistenceService.saveOrReuseBatch(any())).thenAnswer(invocation -> {
            List<OutfitPersistenceService.PersistenceCandidate> candidates = invocation.getArgument(0);
            return candidates.stream().map(candidate -> Outfit.builder().id(UUID.randomUUID()).build()).toList();
        });
    }

    private void stubItemViewsAndPricesFor(BigDecimal totalPrice) {
        lenient().when(outfitItemQueryService.findItemViewsForOutfits(any())).thenAnswer(invocation -> {
            List<UUID> ids = invocation.getArgument(0);
            Map<UUID, List<OutfitItemView>> result = new HashMap<>();
            for (UUID id : ids) {
                result.put(id, List.of());
            }
            return result;
        });
        lenient().when(outfitItemQueryService.sumBasePriceForOutfits(any())).thenAnswer(invocation -> {
            List<UUID> ids = invocation.getArgument(0);
            Map<UUID, BigDecimal> result = new HashMap<>();
            for (UUID id : ids) {
                result.put(id, totalPrice);
            }
            return result;
        });
    }

    private UserProfile profileWith(Sex sex, BigDecimal budget) {
        return UserProfile.builder().userId(userId).sex(sex).averageBudgetPerOutfit(budget).build();
    }

    private Product productWithRole(GarmentRole role) {
        return Product.builder()
                .id(UUID.randomUUID())
                .garmentRole(role)
                .basePrice(new BigDecimal("40"))
                .productDisplayName("Product")
                .imageUrl("https://example.com/product.jpg")
                .build();
    }

    private SearchResults<Product> resultsOf(Product... products) {
        return new SearchResults<>(List.of(products).stream().map(p -> new SearchResult<>(p, 1.0)).toList());
    }

    private SearchResults<Product> emptyResults() {
        return new SearchResults<>(List.of());
    }

    private CompatibilityScoreBreakdown breakdown(String finalScore) {
        BigDecimal score = new BigDecimal(finalScore);
        return new CompatibilityScoreBreakdown(score, score, score, score, score);
    }
}