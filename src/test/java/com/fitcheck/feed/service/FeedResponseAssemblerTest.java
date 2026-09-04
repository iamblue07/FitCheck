package com.fitcheck.feed.service;

import com.fitcheck.common.taxonomy.GarmentRole;
import com.fitcheck.feed.dto.FeedItemResponse;
import com.fitcheck.feed.entity.FeedEntry;
import com.fitcheck.outfit.dto.OutfitItemView;
import com.fitcheck.outfit.entity.Outfit;
import com.fitcheck.outfit.service.OutfitItemQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedResponseAssemblerTest {

    @Mock
    private OutfitItemQueryService outfitItemQueryService;

    private FeedResponseAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new FeedResponseAssembler(outfitItemQueryService);
    }

    @Test
    void toResponse_mapsOutfitBreakdownFieldsOneToOne() {
        Outfit outfit = Outfit.builder()
                .id(UUID.randomUUID())
                .colorScore(new BigDecimal("0.9000"))
                .layeringScore(new BigDecimal("0.8000"))
                .structuredScore(new BigDecimal("0.85"))
                .embeddingScore(new BigDecimal("0.7000"))
                .compatibilityScore(new BigDecimal("0.8200"))
                .build();
        FeedEntry entry = FeedEntry.builder().outfit(outfit).rankScore(new BigDecimal("0.9500")).build();
        when(outfitItemQueryService.findItemViews(outfit.getId())).thenReturn(List.of());

        FeedItemResponse response = assembler.toResponse(entry);

        assertThat(response.compatibilityBreakdown().colorScore()).isEqualByComparingTo("0.9000");
        assertThat(response.compatibilityBreakdown().layeringScore()).isEqualByComparingTo("0.8000");
        assertThat(response.compatibilityBreakdown().structuredScore()).isEqualByComparingTo("0.85");
        assertThat(response.compatibilityBreakdown().embeddingScore()).isEqualByComparingTo("0.7000");
        assertThat(response.compatibilityBreakdown().finalScore()).isEqualByComparingTo("0.8200");
    }

    @Test
    void toResponse_rankScoreComesFromEntryNotOutfit_neverConflatedWithCompatibilityScore() {
        Outfit outfit = Outfit.builder()
                .id(UUID.randomUUID())
                .colorScore(BigDecimal.ONE).layeringScore(BigDecimal.ONE)
                .structuredScore(BigDecimal.ONE).embeddingScore(BigDecimal.ONE)
                .compatibilityScore(new BigDecimal("0.5000")) // deliberately different from rankScore
                .build();
        FeedEntry entry = FeedEntry.builder().outfit(outfit).rankScore(new BigDecimal("0.9999")).build();
        when(outfitItemQueryService.findItemViews(outfit.getId())).thenReturn(List.of());

        FeedItemResponse response = assembler.toResponse(entry);

        assertThat(response.rankScore()).isEqualByComparingTo("0.9999");
        assertThat(response.compatibilityBreakdown().finalScore()).isEqualByComparingTo("0.5000");
    }

    @Test
    void toResponse_mapsEachItemViewToAFeedOutfitItemResponsePreservingOrder() {
        Outfit outfit = fullyScoredOutfit();
        OutfitItemView top = new OutfitItemView(UUID.randomUUID(), "Blue Shirt", "http://img/1.jpg",
                new BigDecimal("40.00"), GarmentRole.TOP);
        OutfitItemView bottom = new OutfitItemView(UUID.randomUUID(), "Black Jeans", "http://img/2.jpg",
                new BigDecimal("60.00"), GarmentRole.BOTTOM);
        FeedEntry entry = FeedEntry.builder().outfit(outfit).rankScore(BigDecimal.ONE).build();
        when(outfitItemQueryService.findItemViews(outfit.getId())).thenReturn(List.of(top, bottom));

        FeedItemResponse response = assembler.toResponse(entry);

        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).productId()).isEqualTo(top.productId());
        assertThat(response.items().get(0).slot()).isEqualTo(GarmentRole.TOP);
        assertThat(response.items().get(1).productId()).isEqualTo(bottom.productId());
        assertThat(response.items().get(1).slot()).isEqualTo(GarmentRole.BOTTOM);
    }

    @Test
    void toResponse_totalPrice_isSummedFromItemsInMemory_neverCallsSumBasePrice() {
        Outfit outfit = fullyScoredOutfit();
        OutfitItemView a = new OutfitItemView(UUID.randomUUID(), "A", "url", new BigDecimal("30.00"), GarmentRole.TOP);
        OutfitItemView b = new OutfitItemView(UUID.randomUUID(), "B", "url", new BigDecimal("45.50"), GarmentRole.BOTTOM);
        OutfitItemView c = new OutfitItemView(UUID.randomUUID(), "C", "url", new BigDecimal("24.99"), GarmentRole.FOOTWEAR);
        FeedEntry entry = FeedEntry.builder().outfit(outfit).rankScore(BigDecimal.ONE).build();
        when(outfitItemQueryService.findItemViews(outfit.getId())).thenReturn(List.of(a, b, c));

        FeedItemResponse response = assembler.toResponse(entry);

        assertThat(response.totalPrice()).isEqualByComparingTo("100.49");
        // the whole point of summing in-memory: never issue a second, redundant SUM(...) query
        verify(outfitItemQueryService, never()).sumBasePrice(any());
    }

    @Test
    void toResponse_noItems_totalPriceIsZeroNotNullOrException() {
        Outfit outfit = fullyScoredOutfit();
        FeedEntry entry = FeedEntry.builder().outfit(outfit).rankScore(BigDecimal.ONE).build();
        when(outfitItemQueryService.findItemViews(outfit.getId())).thenReturn(List.of());

        FeedItemResponse response = assembler.toResponse(entry);

        assertThat(response.totalPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.items()).isEmpty();
    }

    @Test
    void toResponse_outfitId_isTheOutfitsOwnIdNotTheFeedEntrysId() {
        Outfit outfit = fullyScoredOutfit();
        FeedEntry entry = FeedEntry.builder().id(UUID.randomUUID()).outfit(outfit).rankScore(BigDecimal.ONE).build();
        when(outfitItemQueryService.findItemViews(outfit.getId())).thenReturn(List.of());

        FeedItemResponse response = assembler.toResponse(entry);

        assertThat(response.outfitId()).isEqualTo(outfit.getId());
        assertThat(response.outfitId()).isNotEqualTo(entry.getId());
    }

    private Outfit fullyScoredOutfit() {
        return Outfit.builder()
                .id(UUID.randomUUID())
                .colorScore(BigDecimal.ONE).layeringScore(BigDecimal.ONE)
                .structuredScore(BigDecimal.ONE).embeddingScore(BigDecimal.ONE)
                .compatibilityScore(BigDecimal.ONE)
                .build();
    }
}