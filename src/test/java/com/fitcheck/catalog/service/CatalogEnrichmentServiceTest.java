package com.fitcheck.catalog.service;

import com.fitcheck.catalog.entity.Product;
import com.fitcheck.catalog.entity.ProductStyleTag;
import com.fitcheck.catalog.pipeline.CatalogEnrichmentProperties;
import com.fitcheck.catalog.pipeline.ProductEnrichmentResult;
import com.fitcheck.catalog.repository.ProductRepository;
import com.fitcheck.catalog.repository.ProductStyleTagRepository;
import com.fitcheck.common.taxonomy.StyleTag;
import com.fitcheck.common.taxonomy.StyleTagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogEnrichmentServiceTest {

    @Mock
    private EnrichmentService enrichmentService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductStyleTagRepository productStyleTagRepository;

    @Mock
    private StyleTagRepository styleTagRepository;

    @Mock
    private CatalogEnrichmentProperties properties;

    @InjectMocks
    private CatalogEnrichmentService catalogEnrichmentService;

    @Test
    void enrichNext_limitReached_returnsEmpty() {
        when(properties.limitEnabled()).thenReturn(true);
        when(properties.maxItems()).thenReturn(100L);
        when(productRepository.countByDescriptionIsNotNull()).thenReturn(100L);

        Optional<Product> result = catalogEnrichmentService.enrichNext();

        assertThat(result).isEmpty();
        verify(productRepository, never()).findFirstByDescriptionIsNullOrderByCreatedAtAsc();
    }

    @Test
    void enrichNext_noUnenrichedProducts_returnsEmpty() {
        when(properties.limitEnabled()).thenReturn(false);
        when(productRepository.findFirstByDescriptionIsNullOrderByCreatedAtAsc()).thenReturn(Optional.empty());

        Optional<Product> result = catalogEnrichmentService.enrichNext();

        assertThat(result).isEmpty();
        verify(enrichmentService, never()).enrich(any());
    }

    @Test
    void enrichNext_success_mapsAndSavesEveryField() {
        Product product = Product.builder().id(UUID.randomUUID()).build();
        StyleTag minimalist = StyleTag.builder().id(UUID.randomUUID()).name("minimalist").build();
        ProductEnrichmentResult result = new ProductEnrichmentResult(
                "slim", "straight", "solid", "cotton", "casual",
                "A minimalist cotton top.", "work",
                "black", "white", "base", List.of("minimalist"), BigDecimal.valueOf(49.99));

        when(properties.limitEnabled()).thenReturn(false);
        when(productRepository.findFirstByDescriptionIsNullOrderByCreatedAtAsc()).thenReturn(Optional.of(product));
        when(enrichmentService.enrich(product)).thenReturn(result);
        when(styleTagRepository.findAllByNameIn(List.of("minimalist"))).thenReturn(List.of(minimalist));

        Optional<Product> enriched = catalogEnrichmentService.enrichNext();

        assertThat(enriched).isPresent();
        assertThat(product.getFit()).isEqualTo("slim");
        assertThat(product.getSilhouette()).isEqualTo("straight");
        assertThat(product.getPattern()).isEqualTo("solid");
        assertThat(product.getMaterialGuess()).isEqualTo("cotton");
        assertThat(product.getFormality()).isEqualTo("casual");
        assertThat(product.getDescription()).isEqualTo("A minimalist cotton top.");
        assertThat(product.getOccasion()).isEqualTo("work");
        assertThat(product.getPrimaryColor()).isEqualTo("black");
        assertThat(product.getSecondaryColor()).isEqualTo("white");
        assertThat(product.getLayeringRole()).isEqualTo("base");
        assertThat(product.getBasePrice()).isEqualByComparingTo(BigDecimal.valueOf(49.99));
        assertThat(product.getCurrency()).isEqualTo("EUR");

        verify(productRepository).save(product);

        ArgumentCaptor<List<ProductStyleTag>> captor = ArgumentCaptor.captor();
        verify(productStyleTagRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().getFirst().getProduct()).isEqualTo(product);
        assertThat(captor.getValue().getFirst().getStyleTag()).isEqualTo(minimalist);
    }

    @Test
    void enrichNext_unrecognizedStyleTagName_skippedNotFatal() {
        Product product = Product.builder().id(UUID.randomUUID()).build();
        StyleTag minimalist = StyleTag.builder().id(UUID.randomUUID()).name("minimalist").build();
        ProductEnrichmentResult result = new ProductEnrichmentResult(
                "slim", "straight", "solid", "cotton", "casual",
                "A minimalist cotton top.", "date",
                "work", "black", "base", List.of("minimalist", "not-a-real-tag"), BigDecimal.valueOf(49.99));

        when(properties.limitEnabled()).thenReturn(false);
        when(productRepository.findFirstByDescriptionIsNullOrderByCreatedAtAsc()).thenReturn(Optional.of(product));
        when(enrichmentService.enrich(product)).thenReturn(result);
        when(styleTagRepository.findAllByNameIn(List.of("minimalist", "not-a-real-tag")))
                .thenReturn(List.of(minimalist));

        Optional<Product> enriched = catalogEnrichmentService.enrichNext();

        assertThat(enriched).isPresent();
        verify(productRepository).save(product);

        ArgumentCaptor<List<ProductStyleTag>> captor = ArgumentCaptor.captor();
        verify(productStyleTagRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().getFirst().getStyleTag()).isEqualTo(minimalist);
    }
}