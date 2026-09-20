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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutfitInsertServiceTest {

    @Mock
    private OutfitRepository outfitRepository;

    @Mock
    private OutfitItemRepository outfitItemRepository;

    private OutfitInsertService service;

    @BeforeEach
    void setUp() {
        service = new OutfitInsertService(outfitRepository, outfitItemRepository, new OutfitItemAssembler());
    }

    @Test
    void insert_buildsTheRowFromEveryBreakdownComponentPlusHashAndSource() {
        when(outfitRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CompatibilityScoreBreakdown breakdown = new CompatibilityScoreBreakdown(
                new BigDecimal("0.9100"), new BigDecimal("0.8200"), new BigDecimal("0.8650"),
                new BigDecimal("0.7300"), new BigDecimal("0.7975"));

        Outfit result = service.insert(List.of(product(GarmentRole.TOP)), breakdown, "hash-abc",
                OutfitSource.MANUAL_SWAP);

        ArgumentCaptor<Outfit> captor = ArgumentCaptor.forClass(Outfit.class);
        verify(outfitRepository).saveAndFlush(captor.capture());
        Outfit persisted = captor.getValue();

        assertThat(result).isSameAs(persisted);
        assertThat(persisted.getSource()).isEqualTo(OutfitSource.MANUAL_SWAP);
        assertThat(persisted.getItemSetHash()).isEqualTo("hash-abc");
        assertThat(persisted.getColorScore()).isEqualByComparingTo("0.9100");
        assertThat(persisted.getLayeringScore()).isEqualByComparingTo("0.8200");
        assertThat(persisted.getStructuredScore()).isEqualByComparingTo("0.8650");
        assertThat(persisted.getEmbeddingScore()).isEqualByComparingTo("0.7300");
        assertThat(persisted.getCompatibilityScore()).isEqualByComparingTo("0.7975");
    }

    @Test
    void insert_writesOneOutfitItemPerProductWithSlotSnapshottedFromGarmentRole() {
        when(outfitRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Product top = product(GarmentRole.TOP);
        Product footwear = product(GarmentRole.FOOTWEAR);

        Outfit result = service.insert(List.of(top, footwear), breakdownOf("0.5"), "hash-abc",
                OutfitSource.PROFILE_GENERATED);

        List<OutfitItem> items = captureSavedItems();
        assertThat(items).hasSize(2);
        assertThat(items.get(0).getOutfit()).isSameAs(result);
        assertThat(items.get(0).getProduct()).isEqualTo(top);
        assertThat(items.get(0).getSlot()).isEqualTo(GarmentRole.TOP);
        assertThat(items.get(1).getOutfit()).isSameAs(result);
        assertThat(items.get(1).getProduct()).isEqualTo(footwear);
        assertThat(items.get(1).getSlot()).isEqualTo(GarmentRole.FOOTWEAR);
    }

    @Test
    void insert_attachesItemsToTheFlushedEntityNotTheBuiltOne() {
        Outfit flushed = Outfit.builder().id(UUID.randomUUID()).itemSetHash("hash-abc").build();
        when(outfitRepository.saveAndFlush(any())).thenReturn(flushed);

        Outfit result = service.insert(List.of(product(GarmentRole.TOP)), breakdownOf("0.5"), "hash-abc",
                OutfitSource.AI_PROMPT);

        assertThat(result).isSameAs(flushed);
        assertThat(captureSavedItems()).extracting(OutfitItem::getOutfit).containsExactly(flushed);
    }

    @Test
    void insert_neverLeavesAnyOfTheFourNotNullScoreColumnsUnset() {
        when(outfitRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Outfit result = service.insert(List.of(product(GarmentRole.TOP)), breakdownOf("0.5"), "hash-abc",
                OutfitSource.PROFILE_GENERATED);

        assertThat(result.getColorScore()).isNotNull();
        assertThat(result.getLayeringScore()).isNotNull();
        assertThat(result.getStructuredScore()).isNotNull();
        assertThat(result.getEmbeddingScore()).isNotNull();
    }

    @Test
    void insert_uniqueConstraintViolationOnTheRow_propagatesAndNeverWritesItems() {
        DataIntegrityViolationException violation =
                new DataIntegrityViolationException("duplicate key value violates uq item_set_hash");
        when(outfitRepository.saveAndFlush(any())).thenThrow(violation);

        assertThatThrownBy(() -> service.insert(
                List.of(product(GarmentRole.TOP)), breakdownOf("0.5"), "hash-abc", OutfitSource.AI_PROMPT))
                .isSameAs(violation);

        verify(outfitRepository, times(1)).saveAndFlush(any());
        verify(outfitItemRepository, never()).saveAll(any());
    }

    @SuppressWarnings("unchecked")
    private List<OutfitItem> captureSavedItems() {
        ArgumentCaptor<List<OutfitItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(outfitItemRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private Product product(GarmentRole role) {
        return Product.builder().id(UUID.randomUUID()).garmentRole(role).build();
    }

    private CompatibilityScoreBreakdown breakdownOf(String score) {
        BigDecimal value = new BigDecimal(score);
        return new CompatibilityScoreBreakdown(value, value, value, value, value);
    }
}