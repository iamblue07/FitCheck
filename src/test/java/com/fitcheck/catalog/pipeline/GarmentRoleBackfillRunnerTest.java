package com.fitcheck.catalog.pipeline;

import com.fitcheck.catalog.entity.Product;
import com.fitcheck.catalog.repository.ProductRepository;
import com.fitcheck.common.taxonomy.GarmentRole;
import com.fitcheck.common.taxonomy.GarmentRoleResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GarmentRoleBackfillRunnerTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private GarmentRoleResolver garmentRoleResolver;

    @Test
    void run_backfillDisabled_doesNothing() {
        GarmentRoleBackfillRunner runner = runnerWithProperties(false);

        runner.run();

        verifyNoInteractions(productRepository, garmentRoleResolver);
    }

    @Test
    void run_resolvableProduct_assignsRoleAndSavesOnlyThatProduct() {
        GarmentRoleBackfillRunner runner = runnerWithProperties(true);
        Product product = productWithArticleType("Tshirts");

        when(productRepository.findAllByGarmentRoleIsNull(any()))
                .thenReturn(List.of(product))
                .thenReturn(List.of());
        when(garmentRoleResolver.resolve("Tshirts")).thenReturn(Optional.of(GarmentRole.TOP));

        runner.run();

        assertThat(product.getGarmentRole()).isEqualTo(GarmentRole.TOP);
        verify(productRepository).saveAll(List.of(product));
    }

    @Test
    void run_unresolvableProduct_isNotSavedAndIsExcludedFromNextPage() {
        GarmentRoleBackfillRunner runner = runnerWithProperties(true);
        Product unresolved = productWithArticleType("Socks");

        when(productRepository.findAllByGarmentRoleIsNull(any()))
                .thenReturn(List.of(unresolved));
        when(productRepository.findAllByGarmentRoleIsNullAndIdNotIn(eq(Set.of(unresolved.getId())), any()))
                .thenReturn(List.of());
        when(garmentRoleResolver.resolve("Socks")).thenReturn(Optional.empty());

        runner.run();

        assertThat(unresolved.getGarmentRole()).isNull();
        verify(productRepository).saveAll(List.of());
    }

    @Test
    void run_mixOfResolvableAndUnresolvable_savesOnlyResolvedAcrossMultiplePages() {
        GarmentRoleBackfillRunner runner = runnerWithProperties(true);
        Product resolved = productWithArticleType("Jeans");
        Product unresolved = productWithArticleType("Socks");

        when(productRepository.findAllByGarmentRoleIsNull(any()))
                .thenReturn(List.of(resolved, unresolved));
        when(productRepository.findAllByGarmentRoleIsNullAndIdNotIn(anySet(), any()))
                .thenReturn(List.of());
        when(garmentRoleResolver.resolve("Jeans")).thenReturn(Optional.of(GarmentRole.BOTTOM));
        when(garmentRoleResolver.resolve("Socks")).thenReturn(Optional.empty());

        runner.run();

        assertThat(resolved.getGarmentRole()).isEqualTo(GarmentRole.BOTTOM);
        assertThat(unresolved.getGarmentRole()).isNull();
        verify(productRepository).saveAll(List.of(resolved));
    }

    private GarmentRoleBackfillRunner runnerWithProperties(boolean enabled) {
        return new GarmentRoleBackfillRunner(
                new GarmentRoleBackfillProperties(enabled), productRepository, garmentRoleResolver);
    }

    private Product productWithArticleType(String articleType) {
        return Product.builder().id(UUID.randomUUID()).articleType(articleType).build();
    }
}