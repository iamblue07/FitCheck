package com.fitcheck.outfit.service;

import com.fitcheck.catalog.entity.Product;
import com.fitcheck.catalog.service.ProductSearchService;
import com.fitcheck.common.taxonomy.GarmentRole;
import com.fitcheck.identity.entity.Sex;
import com.fitcheck.identity.entity.UserProfile;
import com.fitcheck.identity.service.UserProfileQueryService;
import com.fitcheck.outfit.config.OutfitGenerationProperties;
import com.fitcheck.outfit.config.OutfitPromptProperties;
import com.fitcheck.outfit.dto.AlternativeCandidateResponse;
import com.fitcheck.outfit.dto.CompatibilityScoreBreakdown;
import com.fitcheck.outfit.entity.OutfitItem;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromptRefinementServiceTest {

    @Mock
    private PromptExtractionService promptExtractionService;

    @Mock
    private PromptQueryEmbeddingService promptQueryEmbeddingService;

    @Mock
    private ProductSearchService productSearchService;

    @Mock
    private OutfitCompatibilityScorer outfitCompatibilityScorer;

    @Mock
    private OutfitItemQueryService outfitItemQueryService;

    @Mock
    private UserProfileQueryService userProfileQueryService;

    private BudgetCeilingResolver budgetCeilingResolver;
    private OutfitGenderFilterResolver outfitGenderFilterResolver;
    private PromptRefinementService service;

    private final UUID outfitId = UUID.randomUUID();
    private final UUID itemId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        outfitGenderFilterResolver = new OutfitGenderFilterResolver();
        OutfitGenerationProperties generationProperties = new OutfitGenerationProperties(
                50, 500, new BigDecimal("0.10"), 15, 3);
        budgetCeilingResolver = new BudgetCeilingResolver(generationProperties);
        OutfitPromptProperties promptProperties = new OutfitPromptProperties(2, 3, 200, 200, 50);

        service = new PromptRefinementService(
                promptExtractionService, promptQueryEmbeddingService, productSearchService,
                outfitCompatibilityScorer, outfitGenderFilterResolver, outfitItemQueryService,
                userProfileQueryService, budgetCeilingResolver, promptProperties);
    }

    @Test
    void refine_returnsOnlyCandidatesSharingTheTargetsGarmentRole() {
        Product target = productWith(GarmentRole.TOP, "Tshirts", "Men", new BigDecimal("40"));
        Product other = productWith(GarmentRole.BOTTOM, "Jeans", "Men", new BigDecimal("60"));
        stubContext(target, other);
        when(promptExtractionService.extractSingleSlot("make it dressier", GarmentRole.TOP))
                .thenReturn("a sharper blazer");
        when(promptQueryEmbeddingService.embed("a sharper blazer")).thenReturn(Vector.of(new float[]{1f, 0f, 0f}));
        when(userProfileQueryService.getById(userId)).thenReturn(profileWith(Sex.MALE, null));
        when(outfitItemQueryService.sumBasePrice(outfitId)).thenReturn(new BigDecimal("100"));

        Product differentArticleTypeSameRole = productWith(GarmentRole.TOP, "Blazers", "Men", new BigDecimal("55"));
        when(productSearchService.findNearest(eq(GarmentRole.TOP), any(), any(), any(), any(), any()))
                .thenReturn(resultsOf(differentArticleTypeSameRole));
        when(outfitCompatibilityScorer.score(any())).thenReturn(breakdown("0.7"));

        List<AlternativeCandidateResponse> results = service.refine(outfitId, itemId, userId, "make it dressier");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).productId()).isEqualTo(differentArticleTypeSameRole.getId());
    }

    @Test
    void refine_candidateSameAsTargetProduct_isExcludedFromResults() {
        Product target = productWith(GarmentRole.TOP, "Tshirts", "Men", new BigDecimal("40"));
        Product other = productWith(GarmentRole.BOTTOM, "Jeans", "Men", new BigDecimal("60"));
        stubContext(target, other);
        when(promptExtractionService.extractSingleSlot(any(), eq(GarmentRole.TOP))).thenReturn("something");
        when(promptQueryEmbeddingService.embed(any())).thenReturn(Vector.of(new float[]{1f, 0f, 0f}));
        when(userProfileQueryService.getById(userId)).thenReturn(profileWith(Sex.MALE, null));
        when(outfitItemQueryService.sumBasePrice(outfitId)).thenReturn(new BigDecimal("100"));
        when(productSearchService.findNearest(eq(GarmentRole.TOP), any(), any(), any(), any(), any()))
                .thenReturn(resultsOf(target));

        List<AlternativeCandidateResponse> results = service.refine(outfitId, itemId, userId, "something");

        assertThat(results).isEmpty();
    }

    @Test
    void refine_candidateExceedingBudget_isExcludedFromResults() {
        Product target = productWith(GarmentRole.TOP, "Tshirts", "Men", new BigDecimal("40"));
        Product other = productWith(GarmentRole.BOTTOM, "Jeans", "Men", new BigDecimal("60"));
        stubContext(target, other);
        when(promptExtractionService.extractSingleSlot(any(), eq(GarmentRole.TOP))).thenReturn("something pricier");
        when(promptQueryEmbeddingService.embed(any())).thenReturn(Vector.of(new float[]{1f, 0f, 0f}));
        when(userProfileQueryService.getById(userId)).thenReturn(profileWith(Sex.MALE, new BigDecimal("100")));
        when(outfitItemQueryService.sumBasePrice(outfitId)).thenReturn(new BigDecimal("100"));

        Product tooExpensive = productWith(GarmentRole.TOP, "Blazers", "Men", new BigDecimal("51"));
        when(productSearchService.findNearest(eq(GarmentRole.TOP), any(), any(), any(), any(), any()))
                .thenReturn(resultsOf(tooExpensive));

        List<AlternativeCandidateResponse> results = service.refine(outfitId, itemId, userId, "something pricier");

        assertThat(results).isEmpty();
    }

    @Test
    void refine_queriesUsingGenderCompatibleWithTargetItemNotViewerProfile() {
        Product target = productWith(GarmentRole.TOP, "Tshirts", "Men", new BigDecimal("40"));
        Product other = productWith(GarmentRole.BOTTOM, "Jeans", "Men", new BigDecimal("60"));
        stubContext(target, other);
        when(promptExtractionService.extractSingleSlot(any(), eq(GarmentRole.TOP))).thenReturn("something");
        when(promptQueryEmbeddingService.embed(any())).thenReturn(Vector.of(new float[]{1f, 0f, 0f}));
        when(userProfileQueryService.getById(userId)).thenReturn(profileWith(Sex.FEMALE, null));
        when(outfitItemQueryService.sumBasePrice(outfitId)).thenReturn(new BigDecimal("100"));
        when(productSearchService.findNearest(any(), any(), any(), any(), any(), any())).thenReturn(resultsOf());

        service.refine(outfitId, itemId, userId, "something");

        ArgumentCaptor<Set<String>> gendersCaptor = ArgumentCaptor.forClass(Set.class);
        verify(productSearchService).findNearest(eq(GarmentRole.TOP), gendersCaptor.capture(), any(), any(), any(), any());
        assertThat(gendersCaptor.getValue()).containsExactlyInAnyOrder("Men", "Unisex");
    }

    @Test
    void refine_sortsResultsByProjectedFinalScoreDescending() {
        Product target = productWith(GarmentRole.TOP, "Tshirts", "Men", new BigDecimal("40"));
        Product other = productWith(GarmentRole.BOTTOM, "Jeans", "Men", new BigDecimal("60"));
        stubContext(target, other);
        when(promptExtractionService.extractSingleSlot(any(), eq(GarmentRole.TOP))).thenReturn("something");
        when(promptQueryEmbeddingService.embed(any())).thenReturn(Vector.of(new float[]{1f, 0f, 0f}));
        when(userProfileQueryService.getById(userId)).thenReturn(profileWith(Sex.MALE, null));
        when(outfitItemQueryService.sumBasePrice(outfitId)).thenReturn(new BigDecimal("100"));

        Product lowScore = productWith(GarmentRole.TOP, "Tshirts", "Men", new BigDecimal("35"));
        Product highScore = productWith(GarmentRole.TOP, "Tshirts", "Men", new BigDecimal("38"));
        when(productSearchService.findNearest(eq(GarmentRole.TOP), any(), any(), any(), any(), any()))
                .thenReturn(resultsOf(lowScore, highScore));
        when(outfitCompatibilityScorer.score(any()))
                .thenReturn(breakdown("0.4"))
                .thenReturn(breakdown("0.9"));

        List<AlternativeCandidateResponse> results = service.refine(outfitId, itemId, userId, "something");

        assertThat(results).extracting(AlternativeCandidateResponse::productId)
                .containsExactly(highScore.getId(), lowScore.getId());
    }

    private void stubContext(Product target, Product... others) {
        OutfitItem targetItem = OutfitItem.builder().id(itemId).product(target).slot(target.getGarmentRole()).build();
        when(outfitItemQueryService.loadContext(outfitId, itemId))
                .thenReturn(new OutfitItemQueryService.OutfitItemContext(targetItem, List.of(others)));
    }

    private UserProfile profileWith(Sex sex, BigDecimal budget) {
        return UserProfile.builder().userId(userId).sex(sex).averageBudgetPerOutfit(budget).build();
    }

    private Product productWith(GarmentRole role, String articleType, String gender, BigDecimal basePrice) {
        return Product.builder()
                .id(UUID.randomUUID())
                .articleType(articleType)
                .gender(gender)
                .basePrice(basePrice)
                .garmentRole(role)
                .productDisplayName("Product")
                .imageUrl("https://example.com/product.jpg")
                .build();
    }

    private SearchResults<Product> resultsOf(Product... products) {
        return new SearchResults<>(List.of(products).stream().map(p -> new SearchResult<>(p, 1.0)).toList());
    }

    private CompatibilityScoreBreakdown breakdown(String finalScore) {
        BigDecimal score = new BigDecimal(finalScore);
        return new CompatibilityScoreBreakdown(score, score, score, score, score);
    }
}