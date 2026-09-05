package com.fitcheck.outfit.service;

import com.fitcheck.catalog.entity.Product;
import com.fitcheck.catalog.service.ProductSearchService;
import com.fitcheck.common.exception.BadRequestException;
import com.fitcheck.common.exception.ResourceNotFoundException;
import com.fitcheck.common.taxonomy.GarmentRole;
import com.fitcheck.identity.entity.Sex;
import com.fitcheck.identity.entity.UserProfile;
import com.fitcheck.identity.service.UserProfileQueryService;
import com.fitcheck.outfit.config.OutfitGenerationProperties;
import com.fitcheck.outfit.config.OutfitSwapProperties;
import com.fitcheck.outfit.dto.AlternativeCandidateResponse;
import com.fitcheck.outfit.dto.CompatibilityScoreBreakdown;
import com.fitcheck.outfit.dto.OutfitItemView;
import com.fitcheck.outfit.dto.OutfitResponse;
import com.fitcheck.outfit.entity.Outfit;
import com.fitcheck.outfit.entity.OutfitItem;
import com.fitcheck.outfit.entity.OutfitSource;
import com.fitcheck.outfit.repository.OutfitItemRepository;
import com.fitcheck.outfit.repository.OutfitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GarmentSwapServiceTest {

    @Mock
    private OutfitItemRepository outfitItemRepository;

    @Mock
    private OutfitRepository outfitRepository;

    @Mock
    private OutfitItemQueryService outfitItemQueryService;

    @Mock
    private ProductSearchService productSearchService;

    @Mock
    private OutfitCompatibilityScorer compatibilityScorer;

    @Mock
    private OutfitPersistenceService outfitPersistenceService;

    @Mock
    private UserProfileQueryService userProfileQueryService;

    private OutfitGenderFilterResolver genderFilterResolver;
    private OutfitItemSetHasher itemSetHasher;
    private OutfitGenerationProperties generationProperties;
    private OutfitSwapProperties swapProperties;

    private GarmentSwapService service;

    @BeforeEach
    void setUp() {
        genderFilterResolver = new OutfitGenderFilterResolver();
        itemSetHasher = new OutfitItemSetHasher();
        generationProperties = new OutfitGenerationProperties(50, 500, new BigDecimal("0.10"), 15, 3);
        swapProperties = new OutfitSwapProperties(20);
        service = new GarmentSwapService(
                outfitItemRepository, outfitRepository, outfitItemQueryService, productSearchService,
                compatibilityScorer, genderFilterResolver, itemSetHasher, outfitPersistenceService,
                userProfileQueryService, generationProperties, swapProperties);
    }

    // ---------- listAlternatives ----------

    @Test
    void listAlternatives_outfitNotFound_throwsResourceNotFoundException() {
        UUID outfitId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(outfitRepository.findById(outfitId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listAlternatives(outfitId, itemId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listAlternatives_itemNotPartOfOutfit_throwsResourceNotFoundException() {
        UUID outfitId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(outfitRepository.findById(outfitId)).thenReturn(Optional.of(Outfit.builder().id(outfitId).build()));
        when(outfitItemRepository.findByOutfitId(outfitId)).thenReturn(List.of(
                outfitItem(UUID.randomUUID(), productWith(UUID.randomUUID(), "Jeans", "Men", new BigDecimal("60"), GarmentRole.BOTTOM))));

        assertThatThrownBy(() -> service.listAlternatives(outfitId, itemId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listAlternatives_sortsResultsByProjectedFinalScoreDescending() {
        UUID outfitId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Product target = productWith(UUID.randomUUID(), "Tshirts", "Men", new BigDecimal("40"), GarmentRole.TOP);
        Product other = productWith(UUID.randomUUID(), "Jeans", "Men", new BigDecimal("60"), GarmentRole.BOTTOM);
        stubOutfitContext(outfitId, itemId, target, other);

        Product lowScoreCandidate = productWith(UUID.randomUUID(), "Tshirts", "Men", new BigDecimal("35"), GarmentRole.TOP);
        Product highScoreCandidate = productWith(UUID.randomUUID(), "Tshirts", "Men", new BigDecimal("38"), GarmentRole.TOP);

        when(userProfileQueryService.getById(userId)).thenReturn(profileWith(userId, Sex.MALE, null));
        when(productSearchService.findAlternatives(eq("Tshirts"), any(), eq(target.getId()), any()))
                .thenReturn(List.of(lowScoreCandidate, highScoreCandidate));
        when(outfitItemQueryService.sumBasePrice(outfitId)).thenReturn(new BigDecimal("100"));
        when(compatibilityScorer.score(anyList()))
                .thenReturn(breakdownWithFinalScore("0.4"))
                .thenReturn(breakdownWithFinalScore("0.9"));

        List<AlternativeCandidateResponse> results = service.listAlternatives(outfitId, itemId, userId);

        assertThat(results).extracting(AlternativeCandidateResponse::productId)
                .containsExactly(highScoreCandidate.getId(), lowScoreCandidate.getId());
    }

    @Test
    void listAlternatives_candidateExceedingBudgetCeiling_isExcludedFromResultsAndNeverScored() {
        UUID outfitId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Product target = productWith(UUID.randomUUID(), "Tshirts", "Men", new BigDecimal("40"), GarmentRole.TOP);
        Product other = productWith(UUID.randomUUID(), "Jeans", "Men", new BigDecimal("60"), GarmentRole.BOTTOM);
        stubOutfitContext(outfitId, itemId, target, other);

        // outfitTotal=100, budget=100, tolerance=0.10 -> ceiling=110
        Product atCeiling = productWith(UUID.randomUUID(), "Tshirts", "Men", new BigDecimal("50"), GarmentRole.TOP); // 100-40+50=110, allowed
        Product overCeiling = productWith(UUID.randomUUID(), "Tshirts", "Men", new BigDecimal("51"), GarmentRole.TOP); // 111, excluded

        when(userProfileQueryService.getById(userId)).thenReturn(profileWith(userId, Sex.MALE, new BigDecimal("100")));
        when(productSearchService.findAlternatives(any(), any(), any(), any())).thenReturn(List.of(atCeiling, overCeiling));
        when(outfitItemQueryService.sumBasePrice(outfitId)).thenReturn(new BigDecimal("100"));
        when(compatibilityScorer.score(anyList())).thenReturn(breakdownWithFinalScore("0.5"));

        List<AlternativeCandidateResponse> results = service.listAlternatives(outfitId, itemId, userId);

        assertThat(results).extracting(AlternativeCandidateResponse::productId).containsExactly(atCeiling.getId());
        verify(compatibilityScorer, org.mockito.Mockito.times(1)).score(anyList());
    }

    @Test
    void listAlternatives_nullBudget_neverExcludesCandidatesOnPrice() {
        UUID outfitId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Product target = productWith(UUID.randomUUID(), "Tshirts", "Men", new BigDecimal("40"), GarmentRole.TOP);
        Product other = productWith(UUID.randomUUID(), "Jeans", "Men", new BigDecimal("60"), GarmentRole.BOTTOM);
        stubOutfitContext(outfitId, itemId, target, other);

        Product expensiveCandidate = productWith(UUID.randomUUID(), "Tshirts", "Men", new BigDecimal("99999"), GarmentRole.TOP);

        when(userProfileQueryService.getById(userId)).thenReturn(profileWith(userId, Sex.MALE, null));
        when(productSearchService.findAlternatives(any(), any(), any(), any())).thenReturn(List.of(expensiveCandidate));
        when(outfitItemQueryService.sumBasePrice(outfitId)).thenReturn(new BigDecimal("100"));
        when(compatibilityScorer.score(anyList())).thenReturn(breakdownWithFinalScore("0.5"));

        List<AlternativeCandidateResponse> results = service.listAlternatives(outfitId, itemId, userId);

        assertThat(results).extracting(AlternativeCandidateResponse::productId).containsExactly(expensiveCandidate.getId());
    }

    @Test
    void listAlternatives_noCandidatesFromSearch_returnsEmptyListWithoutError() {
        UUID outfitId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Product target = productWith(UUID.randomUUID(), "Tshirts", "Men", new BigDecimal("40"), GarmentRole.TOP);
        Product other = productWith(UUID.randomUUID(), "Jeans", "Men", new BigDecimal("60"), GarmentRole.BOTTOM);
        stubOutfitContext(outfitId, itemId, target, other);

        when(userProfileQueryService.getById(userId)).thenReturn(profileWith(userId, Sex.MALE, null));
        when(productSearchService.findAlternatives(any(), any(), any(), any())).thenReturn(List.of());
        when(outfitItemQueryService.sumBasePrice(outfitId)).thenReturn(new BigDecimal("100"));

        assertThat(service.listAlternatives(outfitId, itemId, userId)).isEmpty();
    }

    @Test
    void listAlternatives_queriesByTargetArticleTypeResolvedGendersAndExcludesTargetProductId() {
        UUID outfitId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Product target = productWith(UUID.randomUUID(), "Tshirts", "Men", new BigDecimal("40"), GarmentRole.TOP);
        Product other = productWith(UUID.randomUUID(), "Jeans", "Men", new BigDecimal("60"), GarmentRole.BOTTOM);
        stubOutfitContext(outfitId, itemId, target, other);

        when(userProfileQueryService.getById(userId)).thenReturn(profileWith(userId, Sex.FEMALE, null));
        when(productSearchService.findAlternatives(any(), any(), any(), any())).thenReturn(List.of());
        when(outfitItemQueryService.sumBasePrice(outfitId)).thenReturn(BigDecimal.ZERO);

        service.listAlternatives(outfitId, itemId, userId);

        ArgumentCaptor<Limit> limitCaptor = ArgumentCaptor.forClass(Limit.class);
        verify(productSearchService).findAlternatives(eq("Tshirts"), eq(java.util.Set.of("Women", "Unisex")),
                eq(target.getId()), limitCaptor.capture());
        assertThat(limitCaptor.getValue().max()).isEqualTo(20);
    }

    @Test
    void listAlternatives_scoresCandidateAgainstOtherItemsOnly_neverIncludingTheOutgoingTargetItem() {
        UUID outfitId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Product target = productWith(UUID.randomUUID(), "Tshirts", "Men", new BigDecimal("40"), GarmentRole.TOP);
        Product other = productWith(UUID.randomUUID(), "Jeans", "Men", new BigDecimal("60"), GarmentRole.BOTTOM);
        stubOutfitContext(outfitId, itemId, target, other);

        Product candidate = productWith(UUID.randomUUID(), "Tshirts", "Men", new BigDecimal("45"), GarmentRole.TOP);
        when(userProfileQueryService.getById(userId)).thenReturn(profileWith(userId, Sex.MALE, null));
        when(productSearchService.findAlternatives(any(), any(), any(), any())).thenReturn(List.of(candidate));
        when(outfitItemQueryService.sumBasePrice(outfitId)).thenReturn(new BigDecimal("100"));
        when(compatibilityScorer.score(anyList())).thenReturn(breakdownWithFinalScore("0.5"));

        service.listAlternatives(outfitId, itemId, userId);

        ArgumentCaptor<List<Product>> trialCaptor = ArgumentCaptor.forClass(List.class);
        verify(compatibilityScorer).score(trialCaptor.capture());
        assertThat(trialCaptor.getValue()).containsExactlyInAnyOrder(other, candidate);
        assertThat(trialCaptor.getValue()).doesNotContain(target);
    }

    // ---------- swap ----------

    @Test
    void swap_outfitNotFound_throwsResourceNotFoundException() {
        UUID outfitId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(outfitRepository.findById(outfitId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.swap(outfitId, itemId, productId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void swap_itemNotInOutfit_throwsResourceNotFoundException() {
        UUID outfitId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(outfitRepository.findById(outfitId)).thenReturn(Optional.of(Outfit.builder().id(outfitId).build()));
        when(outfitItemRepository.findByOutfitId(outfitId)).thenReturn(List.of(
                outfitItem(UUID.randomUUID(), productWith(UUID.randomUUID(), "Jeans", "Men", new BigDecimal("60"), GarmentRole.BOTTOM))));

        assertThatThrownBy(() -> service.swap(outfitId, itemId, productId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void swap_productNotFound_throwsResourceNotFoundException() {
        UUID outfitId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Product target = productWith(UUID.randomUUID(), "Tshirts", "Men", new BigDecimal("40"), GarmentRole.TOP);
        Product other = productWith(UUID.randomUUID(), "Jeans", "Men", new BigDecimal("60"), GarmentRole.BOTTOM);
        stubOutfitContext(outfitId, itemId, target, other);
        when(productSearchService.findById(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.swap(outfitId, itemId, productId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void swap_candidateArticleTypeMismatch_throwsBadRequestExceptionAndNeverPersists() {
        UUID outfitId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Product target = productWith(UUID.randomUUID(), "Tshirts", "Men", new BigDecimal("40"), GarmentRole.TOP);
        Product other = productWith(UUID.randomUUID(), "Jeans", "Men", new BigDecimal("60"), GarmentRole.BOTTOM);
        stubOutfitContext(outfitId, itemId, target, other);

        Product wrongArticleType = productWith(UUID.randomUUID(), "Sweaters", "Men", new BigDecimal("45"), GarmentRole.TOP);
        when(productSearchService.findById(wrongArticleType.getId())).thenReturn(Optional.of(wrongArticleType));
        when(userProfileQueryService.getById(userId)).thenReturn(profileWith(userId, Sex.MALE, null));
        when(outfitItemQueryService.sumBasePrice(outfitId)).thenReturn(new BigDecimal("100"));

        assertThatThrownBy(() -> service.swap(outfitId, itemId, wrongArticleType.getId(), userId))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(outfitPersistenceService);
    }

    @Test
    void swap_candidateGenderNotInAllowedPool_throwsBadRequestExceptionAndNeverPersists() {
        UUID outfitId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Product target = productWith(UUID.randomUUID(), "Tshirts", "Men", new BigDecimal("40"), GarmentRole.TOP);
        Product other = productWith(UUID.randomUUID(), "Jeans", "Men", new BigDecimal("60"), GarmentRole.BOTTOM);
        stubOutfitContext(outfitId, itemId, target, other);

        Product wrongGender = productWith(UUID.randomUUID(), "Tshirts", "Women", new BigDecimal("45"), GarmentRole.TOP);
        when(productSearchService.findById(wrongGender.getId())).thenReturn(Optional.of(wrongGender));
        when(userProfileQueryService.getById(userId)).thenReturn(profileWith(userId, Sex.MALE, null));
        when(outfitItemQueryService.sumBasePrice(outfitId)).thenReturn(new BigDecimal("100"));

        assertThatThrownBy(() -> service.swap(outfitId, itemId, wrongGender.getId(), userId))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(outfitPersistenceService);
    }

    @Test
    void swap_candidateExceedsBudgetCeiling_throwsBadRequestExceptionAndNeverPersists() {
        UUID outfitId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Product target = productWith(UUID.randomUUID(), "Tshirts", "Men", new BigDecimal("40"), GarmentRole.TOP);
        Product other = productWith(UUID.randomUUID(), "Jeans", "Men", new BigDecimal("60"), GarmentRole.BOTTOM);
        stubOutfitContext(outfitId, itemId, target, other);

        // outfitTotal=100, budget=100, tolerance=0.10 -> ceiling=110; projected = 100-40+51=111 > 110
        Product tooExpensive = productWith(UUID.randomUUID(), "Tshirts", "Men", new BigDecimal("51"), GarmentRole.TOP);
        when(productSearchService.findById(tooExpensive.getId())).thenReturn(Optional.of(tooExpensive));
        when(userProfileQueryService.getById(userId)).thenReturn(profileWith(userId, Sex.MALE, new BigDecimal("100")));
        when(outfitItemQueryService.sumBasePrice(outfitId)).thenReturn(new BigDecimal("100"));

        assertThatThrownBy(() -> service.swap(outfitId, itemId, tooExpensive.getId(), userId))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(outfitPersistenceService);
    }

    @Test
    void swap_nullBudget_neverRejectsOnPrice() {
        UUID outfitId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Product target = productWith(UUID.randomUUID(), "Tshirts", "Men", new BigDecimal("40"), GarmentRole.TOP);
        Product other = productWith(UUID.randomUUID(), "Jeans", "Men", new BigDecimal("60"), GarmentRole.BOTTOM);
        stubOutfitContext(outfitId, itemId, target, other);

        Product expensive = productWith(UUID.randomUUID(), "Tshirts", "Men", new BigDecimal("99999"), GarmentRole.TOP);
        when(productSearchService.findById(expensive.getId())).thenReturn(Optional.of(expensive));
        when(userProfileQueryService.getById(userId)).thenReturn(profileWith(userId, Sex.MALE, null));
        when(outfitItemQueryService.sumBasePrice(outfitId)).thenReturn(new BigDecimal("100"));
        when(compatibilityScorer.score(anyList())).thenReturn(breakdownWithFinalScore("0.5"));
        when(outfitPersistenceService.findExisting(any())).thenReturn(Optional.empty());

        Outfit saved = Outfit.builder().id(UUID.randomUUID()).build();
        when(outfitPersistenceService.saveNew(any(), any(), any(), eq(OutfitSource.MANUAL_SWAP))).thenReturn(saved);
        when(outfitItemQueryService.findItemViews(saved.getId())).thenReturn(List.of());
        when(outfitItemQueryService.sumBasePrice(saved.getId())).thenReturn(new BigDecimal("100045"));

        OutfitResponse response = service.swap(outfitId, itemId, expensive.getId(), userId);

        assertThat(response.outfitId()).isEqualTo(saved.getId());
    }

    @Test
    void swap_newItemSetHash_savesNewOutfitWithManualSwapSourceAndCorrectProductList() {
        UUID outfitId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Product target = productWith(UUID.randomUUID(), "Tshirts", "Men", new BigDecimal("40"), GarmentRole.TOP);
        Product other = productWith(UUID.randomUUID(), "Jeans", "Men", new BigDecimal("60"), GarmentRole.BOTTOM);
        stubOutfitContext(outfitId, itemId, target, other);

        Product candidate = productWith(UUID.randomUUID(), "Tshirts", "Men", new BigDecimal("45"), GarmentRole.TOP);
        when(productSearchService.findById(candidate.getId())).thenReturn(Optional.of(candidate));
        when(userProfileQueryService.getById(userId)).thenReturn(profileWith(userId, Sex.MALE, new BigDecimal("200")));
        when(outfitItemQueryService.sumBasePrice(outfitId)).thenReturn(new BigDecimal("100"));

        CompatibilityScoreBreakdown breakdown = breakdownWithFinalScore("0.77");
        when(compatibilityScorer.score(anyList())).thenReturn(breakdown);
        when(outfitPersistenceService.findExisting(any())).thenReturn(Optional.empty());

        Outfit saved = Outfit.builder().id(UUID.randomUUID()).build();
        when(outfitPersistenceService.saveNew(any(), eq(breakdown), any(), eq(OutfitSource.MANUAL_SWAP))).thenReturn(saved);

        List<OutfitItemView> mappedItems = List.of(
                new OutfitItemView(other.getId(), other.getProductDisplayName(), other.getImageUrl(), other.getBasePrice(), other.getGarmentRole()),
                new OutfitItemView(candidate.getId(), candidate.getProductDisplayName(), candidate.getImageUrl(), candidate.getBasePrice(), candidate.getGarmentRole()));
        when(outfitItemQueryService.findItemViews(saved.getId())).thenReturn(mappedItems);
        when(outfitItemQueryService.sumBasePrice(saved.getId())).thenReturn(new BigDecimal("105"));

        OutfitResponse response = service.swap(outfitId, itemId, candidate.getId(), userId);

        ArgumentCaptor<List<Product>> productsCaptor = ArgumentCaptor.forClass(List.class);
        verify(outfitPersistenceService).saveNew(productsCaptor.capture(), eq(breakdown), any(), eq(OutfitSource.MANUAL_SWAP));
        assertThat(productsCaptor.getValue()).containsExactlyInAnyOrder(other, candidate);
        assertThat(productsCaptor.getValue()).doesNotContain(target);

        assertThat(response.outfitId()).isEqualTo(saved.getId());
        assertThat(response.compatibilityBreakdown()).isEqualTo(breakdown);
        assertThat(response.totalPrice()).isEqualByComparingTo("105");
        assertThat(response.items()).isEqualTo(mappedItems);
    }

    @Test
    void swap_itemSetHashAlreadyExists_reusesExistingOutfitWithoutCallingSaveNew() {
        UUID outfitId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Product target = productWith(UUID.randomUUID(), "Tshirts", "Men", new BigDecimal("40"), GarmentRole.TOP);
        Product other = productWith(UUID.randomUUID(), "Jeans", "Men", new BigDecimal("60"), GarmentRole.BOTTOM);
        stubOutfitContext(outfitId, itemId, target, other);

        Product candidate = productWith(UUID.randomUUID(), "Tshirts", "Men", new BigDecimal("45"), GarmentRole.TOP);
        when(productSearchService.findById(candidate.getId())).thenReturn(Optional.of(candidate));
        when(userProfileQueryService.getById(userId)).thenReturn(profileWith(userId, Sex.MALE, null));
        when(outfitItemQueryService.sumBasePrice(outfitId)).thenReturn(new BigDecimal("100"));
        when(compatibilityScorer.score(anyList())).thenReturn(breakdownWithFinalScore("0.6"));

        Outfit existing = Outfit.builder().id(UUID.randomUUID()).build();
        when(outfitPersistenceService.findExisting(any())).thenReturn(Optional.of(existing));
        when(outfitItemQueryService.findItemViews(existing.getId())).thenReturn(List.of());
        when(outfitItemQueryService.sumBasePrice(existing.getId())).thenReturn(new BigDecimal("105"));

        OutfitResponse response = service.swap(outfitId, itemId, candidate.getId(), userId);

        assertThat(response.outfitId()).isEqualTo(existing.getId());
        verify(outfitPersistenceService, never()).saveNew(any(), any(), any(), any());
    }

    @Test
    void swap_concurrentInsertRace_reQueriesAndReusesTheWinner() {
        UUID outfitId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Product target = productWith(UUID.randomUUID(), "Tshirts", "Men", new BigDecimal("40"), GarmentRole.TOP);
        Product other = productWith(UUID.randomUUID(), "Jeans", "Men", new BigDecimal("60"), GarmentRole.BOTTOM);
        stubOutfitContext(outfitId, itemId, target, other);

        Product candidate = productWith(UUID.randomUUID(), "Tshirts", "Men", new BigDecimal("45"), GarmentRole.TOP);
        when(productSearchService.findById(candidate.getId())).thenReturn(Optional.of(candidate));
        when(userProfileQueryService.getById(userId)).thenReturn(profileWith(userId, Sex.MALE, null));
        when(outfitItemQueryService.sumBasePrice(outfitId)).thenReturn(new BigDecimal("100"));
        when(compatibilityScorer.score(anyList())).thenReturn(breakdownWithFinalScore("0.6"));

        Outfit wonByOtherRequest = Outfit.builder().id(UUID.randomUUID()).build();
        when(outfitPersistenceService.findExisting(any()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(wonByOtherRequest));
        when(outfitPersistenceService.saveNew(any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate item_set_hash"));
        when(outfitItemQueryService.findItemViews(wonByOtherRequest.getId())).thenReturn(List.of());
        when(outfitItemQueryService.sumBasePrice(wonByOtherRequest.getId())).thenReturn(new BigDecimal("105"));

        OutfitResponse response = service.swap(outfitId, itemId, candidate.getId(), userId);

        assertThat(response.outfitId()).isEqualTo(wonByOtherRequest.getId());
    }

    // ---------- fixtures ----------

    private void stubOutfitContext(UUID outfitId, UUID itemId, Product target, Product other) {
        OutfitItem targetItem = outfitItem(itemId, target);
        OutfitItem otherItem = outfitItem(UUID.randomUUID(), other);
        when(outfitRepository.findById(outfitId)).thenReturn(Optional.of(Outfit.builder().id(outfitId).build()));
        when(outfitItemRepository.findByOutfitId(outfitId)).thenReturn(List.of(targetItem, otherItem));
    }

    private Product productWith(UUID id, String articleType, String gender, BigDecimal basePrice, GarmentRole role) {
        return Product.builder()
                .id(id)
                .articleType(articleType)
                .gender(gender)
                .basePrice(basePrice)
                .garmentRole(role)
                .productDisplayName("Product " + id)
                .imageUrl("https://example.com/" + id + ".jpg")
                .build();
    }

    private OutfitItem outfitItem(UUID id, Product product) {
        return OutfitItem.builder().id(id).product(product).slot(product.getGarmentRole()).build();
    }

    private UserProfile profileWith(UUID userId, Sex sex, BigDecimal budget) {
        return UserProfile.builder().userId(userId).sex(sex).averageBudgetPerOutfit(budget).build();
    }

    private CompatibilityScoreBreakdown breakdownWithFinalScore(String score) {
        BigDecimal value = new BigDecimal(score);
        return new CompatibilityScoreBreakdown(value, value, value, value, value);
    }
}