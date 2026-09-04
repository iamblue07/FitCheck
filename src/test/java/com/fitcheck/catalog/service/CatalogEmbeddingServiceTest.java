// test/catalog/service/CatalogEmbeddingServiceTest.java
package com.fitcheck.catalog.service;

import com.fitcheck.catalog.entity.Product;
import com.fitcheck.catalog.config.CatalogEmbeddingProperties;
import com.fitcheck.catalog.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogEmbeddingServiceTest {

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private ProductRepository productRepository;

    @Test
    void nextChunk_returnsUnembeddedProducts() {
        CatalogEmbeddingService service = serviceWithProperties(false, 0);
        Product product = productWithDescription("a red dress");
        when(productRepository.findAllByDescriptionIsNotNullAndTextEmbeddingIsNull(any()))
                .thenReturn(List.of(product));

        List<Product> chunk = service.nextChunk(Set.of());

        assertThat(chunk).containsExactly(product);
    }

    @Test
    void nextChunk_excludesGivenIds() {
        CatalogEmbeddingService service = serviceWithProperties(false, 0);
        UUID excludedId = UUID.randomUUID();
        when(productRepository.findAllByDescriptionIsNotNullAndTextEmbeddingIsNullAndIdNotIn(eq(Set.of(excludedId)), any()))
                .thenReturn(List.of());

        List<Product> chunk = service.nextChunk(Set.of(excludedId));

        assertThat(chunk).isEmpty();
    }

    @Test
    void nextChunk_limitReached_returnsEmptyWithoutQuerying() {
        CatalogEmbeddingService service = serviceWithProperties(true, 10);
        when(productRepository.countByTextEmbeddingIsNotNull()).thenReturn(10L);

        List<Product> chunk = service.nextChunk(Set.of());

        assertThat(chunk).isEmpty();
        verify(productRepository, never()).findAllByDescriptionIsNotNullAndTextEmbeddingIsNull(any());
    }

    @Test
    void embedChunk_assignsReturnedVectorToMatchingProduct() {
        CatalogEmbeddingService service = serviceWithProperties(false, 0);
        Product product = productWithDescription("a cotton t-shirt");
        float[] vector = new float[]{0.1f, 0.2f, 0.3f};
        when(embeddingService.embed(List.of("a cotton t-shirt"))).thenReturn(List.of(vector));

        service.embedChunk(List.of(product));

        assertThat(product.getTextEmbedding()).isEqualTo(vector);
        verify(productRepository).saveAll(List.of(product));
    }

    private CatalogEmbeddingService serviceWithProperties(boolean limitEnabled, int maxItems) {
        return new CatalogEmbeddingService(
                new CatalogEmbeddingProperties(true, maxItems, limitEnabled, 500),
                embeddingService,
                productRepository);
    }

    private Product productWithDescription(String description) {
        return Product.builder().id(UUID.randomUUID()).description(description).build();
    }
}