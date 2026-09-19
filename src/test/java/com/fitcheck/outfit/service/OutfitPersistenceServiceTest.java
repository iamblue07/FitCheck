package com.fitcheck.outfit.service;

import com.fitcheck.catalog.entity.Product;
import com.fitcheck.common.taxonomy.enums.GarmentRole;
import com.fitcheck.outfit.domain.CompatibilityScoreBreakdown;
import com.fitcheck.outfit.entity.Outfit;
import com.fitcheck.outfit.entity.OutfitItem;
import com.fitcheck.outfit.enums.OutfitSource;
import com.fitcheck.outfit.repository.OutfitItemRepository;
import com.fitcheck.outfit.repository.OutfitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.ArrayList;
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
        service = new OutfitPersistenceService(outfitRepository, outfitItemRepository, outfitInsertService);
    }

    @Test
    void findExisting_delegatesToRepository() {
        Outfit outfit = Outfit.builder().id(UUID.randomUUID()).build();
        when(outfitRepository.findByItemSetHash("hash")).thenReturn(Optional.of(outfit));

        assertThat(service.findExisting("hash")).contains(outfit);
    }

    @Test
    void saveNew_delegatesTheRowInsertToTheIsolatedInsertServiceWithGivenBreakdownHashAndSource() {
        Product product = Product.builder().id(UUID.randomUUID()).garmentRole(GarmentRole.TOP).build();
        CompatibilityScoreBreakdown breakdown = new CompatibilityScoreBreakdown(
                new BigDecimal("0.9"), new BigDecimal("0.8"), new BigDecimal("0.85"),
                new BigDecimal("0.9"), new BigDecimal("0.8765"));
        Outfit inserted = outfitWithHash("abc123");
        when(outfitInsertService.insert(breakdown, "abc123", OutfitSource.MANUAL_SWAP)).thenReturn(inserted);

        Outfit result = service.saveNew(List.of(product), breakdown, "abc123", OutfitSource.MANUAL_SWAP);

        assertThat(result).isSameAs(inserted);
        verify(outfitInsertService).insert(breakdown, "abc123", OutfitSource.MANUAL_SWAP);
        verify(outfitRepository, never()).saveAndFlush(any());
    }

    @Test
    void saveNew_createsOneOutfitItemPerProductWithSlotSnapshottedFromGarmentRole() {
        Product top = Product.builder().id(UUID.randomUUID()).garmentRole(GarmentRole.TOP).build();
        Product footwear = Product.builder().id(UUID.randomUUID()).garmentRole(GarmentRole.FOOTWEAR).build();
        Outfit inserted = outfitWithHash("hash");
        when(outfitInsertService.insert(any(), eq("hash"), eq(OutfitSource.PROFILE_GENERATED))).thenReturn(inserted);

        service.saveNew(List.of(top, footwear), breakdownOf("0.5"), "hash", OutfitSource.PROFILE_GENERATED);

        List<OutfitItem> items = captureSavedItems();
        assertThat(items).hasSize(2);
        assertThat(items.get(0).getOutfit()).isSameAs(inserted);
        assertThat(items.get(0).getProduct()).isEqualTo(top);
        assertThat(items.get(0).getSlot()).isEqualTo(GarmentRole.TOP);
        assertThat(items.get(1).getProduct()).isEqualTo(footwear);
        assertThat(items.get(1).getSlot()).isEqualTo(GarmentRole.FOOTWEAR);
    }

    @Test
    void saveOrReuse_existingHash_returnsExistingWithoutInserting() {
        Product product = Product.builder().id(UUID.randomUUID()).garmentRole(GarmentRole.TOP).build();
        Outfit existing = outfitWithHash("hash");
        when(outfitRepository.findByItemSetHash("hash")).thenReturn(Optional.of(existing));

        Outfit result = service.saveOrReuse(List.of(product), breakdownOf("0.5"), "hash", OutfitSource.MANUAL_SWAP);

        assertThat(result).isSameAs(existing);
        verify(outfitInsertService, never()).insert(any(), any(), any());
        verify(outfitItemRepository, never()).saveAll(any());
    }

    @Test
    void saveOrReuse_raceOnInsert_fallsBackToExistingWithoutPropagatingException() {
        Product product = Product.builder().id(UUID.randomUUID()).garmentRole(GarmentRole.TOP).build();
        Outfit wonByOtherTransaction = outfitWithHash("hash");

        when(outfitRepository.findByItemSetHash("hash"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(wonByOtherTransaction));
        when(outfitInsertService.insert(any(), eq("hash"), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates uq item_set_hash"));

        Outfit result = service.saveOrReuse(List.of(product), breakdownOf("0.5"), "hash", OutfitSource.MANUAL_SWAP);

        assertThat(result).isSameAs(wonByOtherTransaction);
        verify(outfitInsertService, times(1)).insert(any(), eq("hash"), any());
        verify(outfitRepository, times(2)).findByItemSetHash("hash");
        verify(outfitItemRepository, never()).saveAll(any());
    }

    @Test
    void saveOrReuse_raceOnInsertButStillNotFoundOnRetry_throwsIllegalStateException() {
        Product product = Product.builder().id(UUID.randomUUID()).garmentRole(GarmentRole.TOP).build();
        DataIntegrityViolationException cause =
                new DataIntegrityViolationException("duplicate key value violates uq item_set_hash");

        when(outfitRepository.findByItemSetHash("hash")).thenReturn(Optional.empty());
        when(outfitInsertService.insert(any(), eq("hash"), any())).thenThrow(cause);

        assertThatThrownBy(() ->
                service.saveOrReuse(List.of(product), breakdownOf("0.5"), "hash", OutfitSource.MANUAL_SWAP))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hash")
                .hasCause(cause);

        verify(outfitInsertService, times(1)).insert(any(), eq("hash"), any());
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
        verify(outfitInsertService, never()).insert(any(), any(), any());
        verify(outfitItemRepository).saveAll(List.of());
    }

    @Test
    void saveOrReuseBatch_persistsNewRowsThroughTheInsertServiceAndItemsInOneBatchSaveAll() {
        Product topA = Product.builder().id(UUID.randomUUID()).garmentRole(GarmentRole.TOP).build();
        Product bottomA = Product.builder().id(UUID.randomUUID()).garmentRole(GarmentRole.BOTTOM).build();
        Product topB = Product.builder().id(UUID.randomUUID()).garmentRole(GarmentRole.TOP).build();
        Outfit existing = outfitWithHash("hash-existing");
        Outfit new1 = outfitWithHash("hash-new-1");
        Outfit new2 = outfitWithHash("hash-new-2");

        when(outfitRepository.findByItemSetHashIn(List.of("hash-existing", "hash-new-1", "hash-new-2")))
                .thenReturn(List.of(existing));
        when(outfitInsertService.insert(any(), eq("hash-new-1"), any())).thenReturn(new1);
        when(outfitInsertService.insert(any(), eq("hash-new-2"), any())).thenReturn(new2);

        List<Outfit> results = service.saveOrReuseBatch(List.of(
                candidate(List.of(topA), "hash-existing"),
                candidate(List.of(topA, bottomA), "hash-new-1"),
                candidate(List.of(topB), "hash-new-2")));

        assertThat(results).containsExactly(existing, new1, new2);
        verify(outfitInsertService, times(2)).insert(any(), any(), any());

        List<OutfitItem> items = captureSavedItems();
        assertThat(items).hasSize(3);
        assertThat(items).extracting(OutfitItem::getOutfit).containsExactly(new1, new1, new2);
    }

    @Test
    void saveOrReuseBatch_raceOnOneCandidate_fallsBackToExistingWithoutAbortingRestOfBatch() {
        Product product = Product.builder().id(UUID.randomUUID()).garmentRole(GarmentRole.TOP).build();
        Outfit wonByOtherTransaction = outfitWithHash("hash-racy");
        Outfit clean = outfitWithHash("hash-clean");

        when(outfitRepository.findByItemSetHashIn(List.of("hash-racy", "hash-clean"))).thenReturn(List.of());
        when(outfitInsertService.insert(any(), eq("hash-racy"), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(outfitInsertService.insert(any(), eq("hash-clean"), any())).thenReturn(clean);
        when(outfitRepository.findByItemSetHash("hash-racy")).thenReturn(Optional.of(wonByOtherTransaction));

        List<Outfit> results = service.saveOrReuseBatch(List.of(
                candidate(List.of(product), "hash-racy"),
                candidate(List.of(product), "hash-clean")));

        assertThat(results).containsExactly(wonByOtherTransaction, clean);
    }

    @Test
    void saveOrReuseBatch_raceOnCandidateThreeOfFifty_leavesTheOtherFortyNineAndAllTheirItemsPersisted() {
        Product product = Product.builder().id(UUID.randomUUID()).garmentRole(GarmentRole.TOP).build();

        List<OutfitPersistenceService.PersistenceCandidate> candidates = new ArrayList<>();
        List<String> hashes = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            String hash = "hash-" + i;
            hashes.add(hash);
            candidates.add(candidate(List.of(product), hash));
        }

        when(outfitRepository.findByItemSetHashIn(hashes)).thenReturn(List.of());
        for (int i = 0; i < 50; i++) {
            String hash = "hash-" + i;
            if (i == 2) {
                when(outfitInsertService.insert(any(), eq(hash), any()))
                        .thenThrow(new DataIntegrityViolationException("duplicate key"));
            } else {
                when(outfitInsertService.insert(any(), eq(hash), any())).thenReturn(outfitWithHash(hash));
            }
        }
        Outfit wonByOtherTransaction = outfitWithHash("hash-2");
        when(outfitRepository.findByItemSetHash("hash-2")).thenReturn(Optional.of(wonByOtherTransaction));

        List<Outfit> results = service.saveOrReuseBatch(candidates);

        assertThat(results).hasSize(50);
        assertThat(results.get(2)).isSameAs(wonByOtherTransaction);
        assertThat(results).extracting(Outfit::getItemSetHash).containsExactlyElementsOf(hashes);

        List<OutfitItem> items = captureSavedItems();
        assertThat(items).hasSize(49);
        assertThat(items).extracting(outfitItem -> outfitItem.getOutfit().getItemSetHash())
                .doesNotContain("hash-2");
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