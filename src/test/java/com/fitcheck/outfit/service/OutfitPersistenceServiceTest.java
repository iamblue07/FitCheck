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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
}