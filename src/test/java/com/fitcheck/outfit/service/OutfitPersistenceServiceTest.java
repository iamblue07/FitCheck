package com.fitcheck.outfit.service;

import com.fitcheck.catalog.entity.Product;
import com.fitcheck.common.taxonomy.GarmentRole;
import com.fitcheck.outfit.dto.CompatibilityScoreBreakdown;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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

    private OutfitPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new OutfitPersistenceService(outfitRepository, outfitItemRepository);
    }

    @Test
    void findExisting_delegatesToRepository() {
        Outfit outfit = Outfit.builder().id(UUID.randomUUID()).build();
        when(outfitRepository.findByItemSetHash("hash")).thenReturn(Optional.of(outfit));

        assertThat(service.findExisting("hash")).contains(outfit);
    }

    @Test
    void saveNew_buildsOutfitWithGivenSourceScoreAndHash() {
        Product product = Product.builder().id(UUID.randomUUID()).garmentRole(GarmentRole.TOP).build();
        when(outfitRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CompatibilityScoreBreakdown breakdown = new CompatibilityScoreBreakdown(
                new BigDecimal("0.9"), new BigDecimal("0.8"), new BigDecimal("0.85"),
                new BigDecimal("0.9"), new BigDecimal("0.8765"));

        Outfit result = service.saveNew(List.of(product), breakdown, "abc123", OutfitSource.MANUAL_SWAP);

        assertThat(result.getSource()).isEqualTo(OutfitSource.MANUAL_SWAP);
        assertThat(result.getCompatibilityScore()).isEqualByComparingTo("0.8765");
        assertThat(result.getColorScore()).isEqualByComparingTo("0.9");
        assertThat(result.getLayeringScore()).isEqualByComparingTo("0.8");
        assertThat(result.getStructuredScore()).isEqualByComparingTo("0.85");
        assertThat(result.getEmbeddingScore()).isEqualByComparingTo("0.9");
        assertThat(result.getItemSetHash()).isEqualTo("abc123");
    }

    @Test
    void saveNew_createsOneOutfitItemPerProductWithSlotSnapshottedFromGarmentRole() {
        Product top = Product.builder().id(UUID.randomUUID()).garmentRole(GarmentRole.TOP).build();
        Product footwear = Product.builder().id(UUID.randomUUID()).garmentRole(GarmentRole.FOOTWEAR).build();
        when(outfitRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CompatibilityScoreBreakdown breakdown = new CompatibilityScoreBreakdown(
                new BigDecimal("0.5"), new BigDecimal("0.5"), new BigDecimal("0.5"),
                new BigDecimal("0.5"), new BigDecimal("0.5"));

        service.saveNew(List.of(top, footwear), breakdown, "hash", OutfitSource.PROFILE_GENERATED);

        ArgumentCaptor<List<OutfitItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(outfitItemRepository).saveAll(captor.capture());
        List<OutfitItem> items = captor.getValue();
        assertThat(items).hasSize(2);
        assertThat(items.get(0).getProduct()).isEqualTo(top);
        assertThat(items.get(0).getSlot()).isEqualTo(GarmentRole.TOP);
        assertThat(items.get(1).getProduct()).isEqualTo(footwear);
        assertThat(items.get(1).getSlot()).isEqualTo(GarmentRole.FOOTWEAR);
    }

    @Test
    void saveOrReuseBatch_returnsExistingOutfitsWithoutPersistingNewRows() {
        Product product = Product.builder().id(UUID.randomUUID()).garmentRole(GarmentRole.TOP).build();
        CompatibilityScoreBreakdown breakdown = new CompatibilityScoreBreakdown(
                new BigDecimal("0.5"), new BigDecimal("0.5"), new BigDecimal("0.5"),
                new BigDecimal("0.5"), new BigDecimal("0.5"));
        Outfit existing = Outfit.builder().id(UUID.randomUUID()).itemSetHash("hash-1").build();
        when(outfitRepository.findByItemSetHashIn(List.of("hash-1"))).thenReturn(List.of(existing));

        OutfitPersistenceService.PersistenceCandidate candidate = new OutfitPersistenceService.PersistenceCandidate(
                List.of(product), breakdown, "hash-1", OutfitSource.AI_PROMPT);

        List<Outfit> results = service.saveOrReuseBatch(List.of(candidate));

        assertThat(results).containsExactly(existing);
        verify(outfitRepository, never()).saveAndFlush(any());
        verify(outfitItemRepository).saveAll(List.of());
    }

    @Test
    void saveOrReuseBatch_persistsNewOutfitsIndividuallyAndItemsInOneBatchSaveAll() {
        Product topA = Product.builder().id(UUID.randomUUID()).garmentRole(GarmentRole.TOP).build();
        Product bottomA = Product.builder().id(UUID.randomUUID()).garmentRole(GarmentRole.BOTTOM).build();
        Product topB = Product.builder().id(UUID.randomUUID()).garmentRole(GarmentRole.TOP).build();
        CompatibilityScoreBreakdown breakdown = new CompatibilityScoreBreakdown(
                new BigDecimal("0.5"), new BigDecimal("0.5"), new BigDecimal("0.5"),
                new BigDecimal("0.5"), new BigDecimal("0.5"));
        Outfit existing = Outfit.builder().id(UUID.randomUUID()).itemSetHash("hash-existing").build();
        when(outfitRepository.findByItemSetHashIn(List.of("hash-existing", "hash-new-1", "hash-new-2")))
                .thenReturn(List.of(existing));
        when(outfitRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OutfitPersistenceService.PersistenceCandidate existingCandidate = new OutfitPersistenceService.PersistenceCandidate(
                List.of(topA), breakdown, "hash-existing", OutfitSource.AI_PROMPT);
        OutfitPersistenceService.PersistenceCandidate newCandidate1 = new OutfitPersistenceService.PersistenceCandidate(
                List.of(topA, bottomA), breakdown, "hash-new-1", OutfitSource.AI_PROMPT);
        OutfitPersistenceService.PersistenceCandidate newCandidate2 = new OutfitPersistenceService.PersistenceCandidate(
                List.of(topB), breakdown, "hash-new-2", OutfitSource.AI_PROMPT);

        List<Outfit> results = service.saveOrReuseBatch(List.of(existingCandidate, newCandidate1, newCandidate2));

        assertThat(results).hasSize(3);
        assertThat(results.get(0)).isEqualTo(existing);
        assertThat(results.get(1).getItemSetHash()).isEqualTo("hash-new-1");
        assertThat(results.get(2).getItemSetHash()).isEqualTo("hash-new-2");

        verify(outfitRepository, times(2)).saveAndFlush(any());

        ArgumentCaptor<List<OutfitItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(outfitItemRepository).saveAll(itemsCaptor.capture());
        assertThat(itemsCaptor.getValue()).hasSize(3);
        assertThat(itemsCaptor.getValue()).extracting(OutfitItem::getOutfit)
                .containsExactly(results.get(1), results.get(1), results.get(2));
    }

    @Test
    void saveOrReuseBatch_raceOnOneCandidateFallsBackToExistingWithoutAbortingRestOfBatch() {
        Product product = Product.builder().id(UUID.randomUUID()).garmentRole(GarmentRole.TOP).build();
        CompatibilityScoreBreakdown breakdown = new CompatibilityScoreBreakdown(
                new BigDecimal("0.5"), new BigDecimal("0.5"), new BigDecimal("0.5"),
                new BigDecimal("0.5"), new BigDecimal("0.5"));
        when(outfitRepository.findByItemSetHashIn(List.of("hash-racy", "hash-clean"))).thenReturn(List.of());

        Outfit wonByOtherTransaction = Outfit.builder().id(UUID.randomUUID()).itemSetHash("hash-racy").build();
        when(outfitRepository.saveAndFlush(argThat(o -> o != null && "hash-racy".equals(o.getItemSetHash()))))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(outfitRepository.saveAndFlush(argThat(o -> o != null && "hash-clean".equals(o.getItemSetHash()))))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(outfitRepository.findByItemSetHash("hash-racy")).thenReturn(Optional.of(wonByOtherTransaction));

        OutfitPersistenceService.PersistenceCandidate racyCandidate = new OutfitPersistenceService.PersistenceCandidate(
                List.of(product), breakdown, "hash-racy", OutfitSource.AI_PROMPT);
        OutfitPersistenceService.PersistenceCandidate cleanCandidate = new OutfitPersistenceService.PersistenceCandidate(
                List.of(product), breakdown, "hash-clean", OutfitSource.AI_PROMPT);

        List<Outfit> results = service.saveOrReuseBatch(List.of(racyCandidate, cleanCandidate));

        assertThat(results.get(0)).isEqualTo(wonByOtherTransaction);
        assertThat(results.get(1).getItemSetHash()).isEqualTo("hash-clean");
    }
}