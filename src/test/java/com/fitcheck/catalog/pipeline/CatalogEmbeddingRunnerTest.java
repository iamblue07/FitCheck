// test/catalog/pipeline/CatalogEmbeddingRunnerTest.java
package com.fitcheck.catalog.pipeline;

import com.fitcheck.catalog.config.CatalogEmbeddingProperties;
import com.fitcheck.catalog.entity.Product;
import com.fitcheck.catalog.service.CatalogEmbeddingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogEmbeddingRunnerTest {

    @Mock
    private CatalogEmbeddingService catalogEmbeddingService;

    @Test
    void run_batchDisabled_doesNothing() {
        CatalogEmbeddingRunner runner = runnerWithProperties(false);

        runner.run();

        verifyNoInteractions(catalogEmbeddingService);
    }

    @Test
    void run_multipleChunks_repeatsUntilEmpty() {
        CatalogEmbeddingRunner runner = runnerWithProperties(true);
        Product first = productWithId();
        Product second = productWithId();

        when(catalogEmbeddingService.nextChunk(anySet()))
                .thenReturn(List.of(first))
                .thenReturn(List.of(second))
                .thenReturn(List.of());

        runner.run();

        verify(catalogEmbeddingService, times(3)).nextChunk(anySet());
        verify(catalogEmbeddingService).embedChunk(List.of(first));
        verify(catalogEmbeddingService).embedChunk(List.of(second));
    }

    @Test
    void run_chunkFailsAsBatch_retriesItemByItemAndIsolatesOnlyTheFailingOne() {
        CatalogEmbeddingRunner runner = runnerWithProperties(true);
        Product good = productWithId();
        Product bad = productWithId();
        List<Product> chunk = List.of(good, bad);

        when(catalogEmbeddingService.nextChunk(anySet()))
                .thenReturn(chunk)
                .thenReturn(List.of());
        doThrow(new RuntimeException("batch call failed")).when(catalogEmbeddingService).embedChunk(chunk);
        doThrow(new RuntimeException("still bad")).when(catalogEmbeddingService).embedChunk(List.of(bad));

        runner.run();

        verify(catalogEmbeddingService).embedChunk(List.of(good));
        verify(catalogEmbeddingService).embedChunk(List.of(bad));
    }

    private CatalogEmbeddingRunner runnerWithProperties(boolean batchEnabled) {
        return new CatalogEmbeddingRunner(
                new CatalogEmbeddingProperties(batchEnabled, 0, false, 500),
                catalogEmbeddingService);
    }

    private Product productWithId() {
        return Product.builder().id(UUID.randomUUID()).build();
    }
}