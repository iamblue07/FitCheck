package com.sewlect.outfit.service;

import com.sewlect.catalog.entity.Product;
import com.sewlect.common.taxonomy.enums.GarmentRole;
import com.sewlect.outfit.domain.CompatibilityScoreBreakdown;
import com.sewlect.outfit.entity.Outfit;
import com.sewlect.outfit.entity.OutfitItem;
import com.sewlect.outfit.enums.OutfitSource;
import com.sewlect.outfit.repository.OutfitItemRepository;
import com.sewlect.outfit.repository.OutfitRepository;
import com.sewlect.outfit.support.OutfitItemAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutfitPersistenceServiceTest {

    @Mock
    private OutfitRepository outfitRepository;

    @Mock
    private OutfitItemRepository outfitItemRepository;

    @Mock
    private OutfitInsertService outfitInsertService;

    private OutfitPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new OutfitPersistenceService(
                outfitRepository, outfitItemRepository, outfitInsertService, new OutfitItemAssembler());
    }

    @Test
    void findExisting_delegatesToRepository() {
        Outfit outfit = Outfit.builder().id(UUID.randomUUID()).build();
        when(outfitRepository.findByItemSetHash("hash")).thenReturn(Optional.of(outfit));

        assertThat(service.findExisting("hash")).contains(outfit);
    }

    @Test
    void saveNew_delegatesRowAndItemsWholesaleToTheIsolatedInsertService() {
        Product top = Product.builder().id(UUID.randomUUID()).garmentRole(GarmentRole.TOP).build();
        Product footwear = Product.builder().id(UUID.randomUUID()).garmentRole(GarmentRole.FOOTWEAR).build();
        List<Product> products = List.of(top, footwear);
        CompatibilityScoreBreakdown breakdown = new CompatibilityScoreBreakdown(
                new BigDecimal("0.9"), new BigDecimal("0.8"), new BigDecimal("0.85"),
                new BigDecimal("0.9"), new BigDecimal("0.8765"));
        Outfit inserted = outfitWithHash("abc123");
        when(outfitInsertService.insert(products, breakdown, "abc123", OutfitSource.MANUAL_SWAP))
                .thenReturn(inserted);

        Outfit result = service.saveNew(products, breakdown, "abc123", OutfitSource.MANUAL_SWAP);

        assertThat(result).isSameAs(inserted);
        verify(outfitInsertService).insert(products, breakdown, "abc123", OutfitSource.MANUAL_SWAP);
        verify(outfitRepository, never()).saveAndFlush(any());
        verify(outfitItemRepository, never()).saveAll(any());
    }

    @Test
    void saveOrReuse_existingHash_returnsExistingWithoutInserting() {
        Product product = Product.builder().id(UUID.randomUUID()).garmentRole(GarmentRole.TOP).build();
        Outfit existing = outfitWithHash("hash");
        when(outfitRepository.findByItemSetHash("hash")).thenReturn(Optional.of(existing));

        Outfit result = service.saveOrReuse(List.of(product), breakdownOf("0.5"), "hash", OutfitSource.MANUAL_SWAP);

        assertThat(result).isSameAs(existing);
        verifyNoInteractions(outfitInsertService);
        verify(outfitItemRepository, never()).saveAll(any());
    }

    @Test
    void saveOrReuse_raceOnInsert_fallsBackToExistingWithoutPropagatingException() {
        Product product = Product.builder().id(UUID.randomUUID()).garmentRole(GarmentRole.TOP).build();
        Outfit wonByOtherTransaction = outfitWithHash("hash");

        when(outfitRepository.findByItemSetHash("hash"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(wonByOtherTransaction));
        when(outfitInsertService.insert(any(), any(), eq("hash"), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates uq item_set_hash"));

        Outfit result = service.saveOrReuse(List.of(product), breakdownOf("0.5"), "hash", OutfitSource.MANUAL_SWAP);

        assertThat(result).isSameAs(wonByOtherTransaction);
        verify(outfitInsertService, times(1)).insert(any(), any(), eq("hash"), any());
        verify(outfitRepository, times(2)).findByItemSetHash("hash");
        verify(outfitItemRepository, never()).saveAll(any());
    }

    @Test
    void saveOrReuse_raceOnInsertButStillNotFoundOnRetry_throwsIllegalStateException() {
        Product product = Product.builder().id(UUID.randomUUID()).garmentRole(GarmentRole.TOP).build();
        DataIntegrityViolationException cause =
                new DataIntegrityViolationException("duplicate key value violates uq item_set_hash");

        when(outfitRepository.findByItemSetHash("hash")).thenReturn(Optional.empty());
        when(outfitInsertService.insert(any(), any(), eq("hash"), any())).thenThrow(cause);

        assertThatThrownBy(() ->
                service.saveOrReuse(List.of(product), breakdownOf("0.5"), "hash", OutfitSource.MANUAL_SWAP))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hash")
                .hasCause(cause);

        verify(outfitInsertService, times(1)).insert(any(), any(), eq("hash"), any());
        verify(outfitRepository, times(2)).findByItemSetHash("hash");
    }

    @Test
    void saveOrReuseBatch_returnsExistingOutfitsWithoutPersistingNewRows() {
        Product product = Product.builder().id(UUID.randomUUID()).garmentRole(GarmentRole.TOP).build();
        Outfit existing = outfitWithHash("hash-1");
        when(outfitRepository.findByItemSetHashIn(List.of("hash-1"))).thenReturn(List.of(existing));

        List<Outfit> results = service.saveOrReuseBatch(List.of(
                candidate(List.of(product), "hash-1")));

        assertThat(results).containsExactly(existing);
        verify(outfitRepository, never()).saveAndFlush(any());
        verify(outfitItemRepository).saveAll(List.of());
        verifyNoInteractions(outfitInsertService);
    }

    @Test
    void saveOrReuseBatch_neverRoutesThroughTheRequiresNewInsertService() {
        Product product = Product.builder().id(UUID.randomUUID()).garmentRole(GarmentRole.TOP).build();
        when(outfitRepository.findByItemSetHashIn(List.of("hash-new"))).thenReturn(List.of());
        when(outfitRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.saveOrReuseBatch(List.of(candidate(List.of(product), "hash-new")));

        verifyNoInteractions(outfitInsertService);
    }

    @Test
    void saveOrReuseBatch_persistsNewRowsInlineAndAllItemsInOneBatchSaveAll() {
        Product topA = Product.builder().id(UUID.randomUUID()).garmentRole(GarmentRole.TOP).build();
        Product bottomA = Product.builder().id(UUID.randomUUID()).garmentRole(GarmentRole.BOTTOM).build();
        Product topB = Product.builder().id(UUID.randomUUID()).garmentRole(GarmentRole.TOP).build();
        Outfit existing = outfitWithHash("hash-existing");

        when(outfitRepository.findByItemSetHashIn(List.of("hash-existing", "hash-new-1", "hash-new-2")))
                .thenReturn(List.of(existing));
        when(outfitRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<Outfit> results = service.saveOrReuseBatch(List.of(
                candidate(List.of(topA), "hash-existing"),
                candidate(List.of(topA, bottomA), "hash-new-1"),
                candidate(List.of(topB), "hash-new-2")));

        assertThat(results).extracting(Outfit::getItemSetHash)
                .containsExactly("hash-existing", "hash-new-1", "hash-new-2");
        assertThat(results.get(0)).isSameAs(existing);
        verify(outfitRepository, times(2)).saveAndFlush(any());

        List<OutfitItem> items = captureSavedItems();
        assertThat(items).hasSize(3);
        assertThat(items).extracting(outfitItem -> outfitItem.getOutfit().getItemSetHash())
                .containsExactly("hash-new-1", "hash-new-1", "hash-new-2");
        assertThat(items).extracting(OutfitItem::getProduct).containsExactly(topA, bottomA, topB);
    }

    @Test
    void saveOrReuseBatch_raceOnOneCandidate_propagatesSoTheWholeTransactionRollsBackAndTheCallerCanRetry() {
        Product product = Product.builder().id(UUID.randomUUID()).garmentRole(GarmentRole.TOP).build();
        DataIntegrityViolationException violation = new DataIntegrityViolationException("duplicate key");

        when(outfitRepository.findByItemSetHashIn(List.of("hash-racy", "hash-clean"))).thenReturn(List.of());
        when(outfitRepository.saveAndFlush(any())).thenThrow(violation);

        assertThatThrownBy(() -> service.saveOrReuseBatch(List.of(
                candidate(List.of(product), "hash-racy"),
                candidate(List.of(product), "hash-clean"))))
                .isSameAs(violation);

        verify(outfitItemRepository, never()).saveAll(any());
        verify(outfitRepository, never()).findByItemSetHash(any());
    }

    @Test
    void saveOrReuseBatch_raceOnCandidateThreeOfFifty_abortsTheWholeBatchRatherThanSwallowingIt() {
        Product product = Product.builder().id(UUID.randomUUID()).garmentRole(GarmentRole.TOP).build();
        DataIntegrityViolationException violation = new DataIntegrityViolationException("duplicate key");

        List<OutfitPersistenceService.PersistenceCandidate> candidates = new java.util.ArrayList<>();
        List<String> hashes = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) {
            String hash = "hash-" + i;
            hashes.add(hash);
            candidates.add(candidate(List.of(product), hash));
        }

        when(outfitRepository.findByItemSetHashIn(hashes)).thenReturn(List.of());
        when(outfitRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0))
                .thenAnswer(invocation -> invocation.getArgument(0))
                .thenThrow(violation);

        assertThatThrownBy(() -> service.saveOrReuseBatch(candidates)).isSameAs(violation);

        verify(outfitRepository, times(3)).saveAndFlush(any());
        verify(outfitItemRepository, never()).saveAll(any());
    }

    private OutfitPersistenceService.PersistenceCandidate candidate(List<Product> products, String hash) {
        return new OutfitPersistenceService.PersistenceCandidate(
                products, breakdownOf("0.5"), hash, OutfitSource.AI_PROMPT);
    }

    private Outfit outfitWithHash(String hash) {
        return Outfit.builder().id(UUID.randomUUID()).itemSetHash(hash).build();
    }

    @SuppressWarnings("unchecked")
    private List<OutfitItem> captureSavedItems() {
        ArgumentCaptor<List<OutfitItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(outfitItemRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private CompatibilityScoreBreakdown breakdownOf(String score) {
        BigDecimal value = new BigDecimal(score);
        return new CompatibilityScoreBreakdown(value, value, value, value, value);
    }
}