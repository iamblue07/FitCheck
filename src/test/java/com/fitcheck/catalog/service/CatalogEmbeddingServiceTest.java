package com.fitcheck.catalog.service;

import com.fitcheck.catalog.entity.Product;
import com.fitcheck.catalog.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogEmbeddingServiceTest {

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CatalogEmbeddingService catalogEmbeddingService;

    @Test
    void embedPending_multipleChunks_repeatsUntilEmpty() {
        Product first = productWithDescription("a red dress");
        Product second = productWithDescription("blue jeans");

        when(productRepository.findAllByDescriptionIsNotNullAndTextEmbeddingIsNull(any()))
                .thenReturn(List.of(first))
                .thenReturn(List.of(second))
                .thenReturn(List.of());
        when(embeddingService.embed(any())).thenReturn(List.of(new float[]{0.1f}));

        catalogEmbeddingService.embedPending();

        verify(productRepository, times(3)).findAllByDescriptionIsNotNullAndTextEmbeddingIsNull(any());
        verify(productRepository, times(2)).saveAll(any());
    }

    @Test
    void embedPending_assignsReturnedVectorToMatchingProduct() {
        Product product = productWithDescription("a cotton t-shirt");
        float[] vector = new float[]{0.1f, 0.2f, 0.3f};

        when(productRepository.findAllByDescriptionIsNotNullAndTextEmbeddingIsNull(any()))
                .thenReturn(List.of(product))
                .thenReturn(List.of());
        when(embeddingService.embed(List.of("a cotton t-shirt"))).thenReturn(List.of(vector));

        catalogEmbeddingService.embedPending();

        assertThat(product.getTextEmbedding()).isEqualTo(vector);
        verify(productRepository).saveAll(List.of(product));
    }

    @Test
    void embedPending_nothingPending_noOp() {
        when(productRepository.findAllByDescriptionIsNotNullAndTextEmbeddingIsNull(any())).thenReturn(List.of());

        catalogEmbeddingService.embedPending();

        verify(productRepository, never()).saveAll(any());
        verify(embeddingService, never()).embed(any());
    }

    private Product productWithDescription(String description) {
        return Product.builder().id(UUID.randomUUID()).description(description).build();
    }
}