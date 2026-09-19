package com.fitcheck.outfit.support;

import com.fitcheck.common.taxonomy.enums.GarmentRole;
import com.fitcheck.outfit.domain.OutfitItemView;
import com.fitcheck.outfit.dto.OutfitResponse;
import com.fitcheck.outfit.entity.Outfit;
import com.fitcheck.outfit.service.OutfitItemQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutfitResponseAssemblerTest {

    @Mock
    private OutfitItemQueryService outfitItemQueryService;

    private OutfitResponseAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new OutfitResponseAssembler(outfitItemQueryService);
    }

    @Test
    void toResponse_buildsBreakdownFromPersistedScoreColumns() {
        Outfit outfit = Outfit.builder()
                .id(UUID.randomUUID())
                .colorScore(new BigDecimal("0.9100"))
                .layeringScore(new BigDecimal("0.8200"))
                .structuredScore(new BigDecimal("0.8650"))
                .embeddingScore(new BigDecimal("0.7300"))
                .compatibilityScore(new BigDecimal("0.7975"))
                .build();
        when(outfitItemQueryService.sumBasePrice(outfit.getId())).thenReturn(BigDecimal.ZERO);
        when(outfitItemQueryService.findItemViews(outfit.getId())).thenReturn(List.of());

        OutfitResponse response = assembler.toResponse(outfit);

        assertThat(response.compatibilityBreakdown().colorScore()).isEqualByComparingTo("0.9100");
        assertThat(response.compatibilityBreakdown().layeringScore()).isEqualByComparingTo("0.8200");
        assertThat(response.compatibilityBreakdown().structuredScore()).isEqualByComparingTo("0.8650");
        assertThat(response.compatibilityBreakdown().embeddingScore()).isEqualByComparingTo("0.7300");
        assertThat(response.compatibilityBreakdown().finalScore()).isEqualByComparingTo("0.7975");
    }

    @Test
    void toResponse_totalPriceAndItemsComeFromSingleOutfitLookups() {
        Outfit outfit = Outfit.builder().id(UUID.randomUUID()).build();
        List<OutfitItemView> items = List.of(itemView(), itemView());
        when(outfitItemQueryService.sumBasePrice(outfit.getId())).thenReturn(new BigDecimal("84.50"));
        when(outfitItemQueryService.findItemViews(outfit.getId())).thenReturn(items);

        OutfitResponse response = assembler.toResponse(outfit);

        assertThat(response.outfitId()).isEqualTo(outfit.getId());
        assertThat(response.totalPrice()).isEqualByComparingTo("84.50");
        assertThat(response.items()).isEqualTo(items);

        verify(outfitItemQueryService).sumBasePrice(outfit.getId());
        verify(outfitItemQueryService).findItemViews(outfit.getId());
        verify(outfitItemQueryService, never()).sumBasePriceForOutfits(anyList());
        verify(outfitItemQueryService, never()).findItemViewsForOutfits(anyList());
    }

    @Test
    void toResponses_emptyList_returnsEmptyListWithoutCallingBatchedLookups() {
        List<OutfitResponse> responses = assembler.toResponses(List.of());

        assertThat(responses).isEmpty();
        verify(outfitItemQueryService, never()).findItemViewsForOutfits(anyList());
        verify(outfitItemQueryService, never()).sumBasePriceForOutfits(anyList());
    }

    @Test
    void toResponses_multipleOutfits_usesOneBatchedItemQueryAndOnePriceQueryNotPerOutfit() {
        Outfit first = Outfit.builder().id(UUID.randomUUID()).build();
        Outfit second = Outfit.builder().id(UUID.randomUUID()).build();
        Outfit third = Outfit.builder().id(UUID.randomUUID()).build();
        List<UUID> outfitIds = List.of(first.getId(), second.getId(), third.getId());

        when(outfitItemQueryService.findItemViewsForOutfits(outfitIds)).thenReturn(Map.of(
                first.getId(), List.of(itemView()),
                second.getId(), List.of(itemView()),
                third.getId(), List.of(itemView())));
        when(outfitItemQueryService.sumBasePriceForOutfits(outfitIds)).thenReturn(Map.of(
                first.getId(), new BigDecimal("10.00"),
                second.getId(), new BigDecimal("20.00"),
                third.getId(), new BigDecimal("30.00")));

        List<OutfitResponse> responses = assembler.toResponses(List.of(first, second, third));

        assertThat(responses).hasSize(3);
        verify(outfitItemQueryService, times(1)).findItemViewsForOutfits(anyList());
        verify(outfitItemQueryService, times(1)).sumBasePriceForOutfits(anyList());
        verify(outfitItemQueryService, never()).findItemViews(any());
        verify(outfitItemQueryService, never()).sumBasePrice(any());
    }

    @Test
    void toResponses_preservesInputOrder() {
        Outfit first = Outfit.builder().id(UUID.randomUUID()).build();
        Outfit second = Outfit.builder().id(UUID.randomUUID()).build();
        Outfit third = Outfit.builder().id(UUID.randomUUID()).build();
        List<UUID> outfitIds = List.of(first.getId(), second.getId(), third.getId());

        when(outfitItemQueryService.findItemViewsForOutfits(outfitIds)).thenReturn(Map.of(
                first.getId(), List.of(),
                second.getId(), List.of(),
                third.getId(), List.of()));
        when(outfitItemQueryService.sumBasePriceForOutfits(outfitIds)).thenReturn(Map.of(
                first.getId(), new BigDecimal("10.00"),
                second.getId(), new BigDecimal("20.00"),
                third.getId(), new BigDecimal("30.00")));

        List<OutfitResponse> responses = assembler.toResponses(List.of(first, second, third));

        assertThat(responses).extracting(OutfitResponse::outfitId)
                .containsExactly(first.getId(), second.getId(), third.getId());
        assertThat(responses).extracting(OutfitResponse::totalPrice)
                .containsExactly(new BigDecimal("10.00"), new BigDecimal("20.00"), new BigDecimal("30.00"));
    }

    @Test
    void toResponses_outfitWithNoItems_stillProducesAResponseNotAnException() {
        Outfit outfit = Outfit.builder().id(UUID.randomUUID()).build();
        List<UUID> outfitIds = List.of(outfit.getId());

        when(outfitItemQueryService.findItemViewsForOutfits(outfitIds))
                .thenReturn(Map.of(outfit.getId(), List.of()));
        when(outfitItemQueryService.sumBasePriceForOutfits(outfitIds))
                .thenReturn(Map.of(outfit.getId(), BigDecimal.ZERO));

        List<OutfitResponse> responses = assembler.toResponses(List.of(outfit));

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).items()).isEmpty();
        assertThat(responses.get(0).totalPrice()).isEqualByComparingTo("0");
    }

    private OutfitItemView itemView() {
        return new OutfitItemView(UUID.randomUUID(), UUID.randomUUID(), "Product",
                "https://example.com/p.jpg", new BigDecimal("10.00"), GarmentRole.TOP);
    }
}