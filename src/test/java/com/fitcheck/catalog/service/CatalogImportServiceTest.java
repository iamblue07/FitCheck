package com.fitcheck.catalog.service;

import com.fitcheck.catalog.entity.Product;
import com.fitcheck.catalog.entity.ProductVariant;
import com.fitcheck.catalog.pipeline.CatalogCsvReader;
import com.fitcheck.catalog.pipeline.StyleCsvRecord;
import com.fitcheck.catalog.repository.ProductRepository;
import com.fitcheck.catalog.repository.ProductVariantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogImportServiceTest {

    @Mock
    private CatalogCsvReader catalogCsvReader;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @InjectMocks
    private CatalogImportService catalogImportService;

    private final Path stylesPath = Path.of("styles.csv");
    private final Path imagesPath = Path.of("images.csv");

    @Test
    void importCatalog_savesProduct_withAllFieldsMapped() {
        StyleCsvRecord record = sampleRecord("15970");

        when(catalogCsvReader.readStyles(stylesPath)).thenReturn(List.of(record));
        when(catalogCsvReader.readImageLinks(imagesPath))
                .thenReturn(Map.of("15970.jpg", "http://example.com/15970.jpg"));
        when(productRepository.findAllExternalIds()).thenReturn(List.of());
        when(productRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        catalogImportService.importCatalog(stylesPath, imagesPath);

        ArgumentCaptor<List<Product>> captor = ArgumentCaptor.captor();
        verify(productRepository).saveAll(captor.capture());

        Product saved = captor.getValue().getFirst();
        assertThat(saved.getExternalId()).isEqualTo("15970");
        assertThat(saved.getGender()).isEqualTo("Men");
        assertThat(saved.getMasterCategory()).isEqualTo("Apparel");
        assertThat(saved.getSubCategory()).isEqualTo("Topwear");
        assertThat(saved.getArticleType()).isEqualTo("Shirts");
        assertThat(saved.getBaseColour()).isEqualTo("Navy Blue");
        assertThat(saved.getSeason()).isEqualTo("Fall");
        assertThat(saved.getYear()).isEqualTo(2011);
        assertThat(saved.getUsage()).isEqualTo("Casual");
        assertThat(saved.getProductDisplayName()).isEqualTo("Turtle Check Men Navy Blue Shirt");
        assertThat(saved.getImageUrl()).isEqualTo("http://example.com/15970.jpg");
        assertThat(saved.getFit()).isNull();
        assertThat(saved.getSilhouette()).isNull();
        assertThat(saved.getPattern()).isNull();
        assertThat(saved.getMaterialGuess()).isNull();
        assertThat(saved.getFormality()).isNull();
        assertThat(saved.getDescription()).isNull();
        assertThat(saved.getBasePrice()).isNull();
        assertThat(saved.getCurrency()).isNull();
    }

    @Test
    void importCatalog_generatesFourVariants_perProduct() {
        StyleCsvRecord record = sampleRecord("15970");

        when(catalogCsvReader.readStyles(stylesPath)).thenReturn(List.of(record));
        when(catalogCsvReader.readImageLinks(imagesPath))
                .thenReturn(Map.of("15970.jpg", "http://example.com/15970.jpg"));
        when(productRepository.findAllExternalIds()).thenReturn(List.of());
        when(productRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        catalogImportService.importCatalog(stylesPath, imagesPath);

        ArgumentCaptor<List<Product>> productCaptor = ArgumentCaptor.captor();
        verify(productRepository).saveAll(productCaptor.capture());
        Product savedProduct = productCaptor.getValue().getFirst();

        ArgumentCaptor<List<ProductVariant>> variantCaptor = ArgumentCaptor.captor();
        verify(productVariantRepository).saveAll(variantCaptor.capture());
        List<ProductVariant> variants = variantCaptor.getValue();

        assertThat(variants).hasSize(4);
        assertThat(variants).extracting(ProductVariant::getSize).containsExactlyInAnyOrder("S", "M", "L", "XL");
        assertThat(variants).allSatisfy(variant -> {
            assertThat(variant.getProduct()).isEqualTo(savedProduct);
            assertThat(variant.getStockQuantity()).isBetween(0, 50);
        });
    }

    @Test
    void importCatalog_skipsRow_withNoMatchingImage() {
        StyleCsvRecord record = sampleRecord("15970");

        when(catalogCsvReader.readStyles(stylesPath)).thenReturn(List.of(record));
        when(catalogCsvReader.readImageLinks(imagesPath)).thenReturn(Map.of());
        when(productRepository.findAllExternalIds()).thenReturn(List.of());

        catalogImportService.importCatalog(stylesPath, imagesPath);

        verify(productRepository, never()).saveAll(any());
        verify(productVariantRepository, never()).saveAll(any());
    }

    @Test
    void importCatalog_skipsRow_withExternalIdAlreadyInDatabase() {
        StyleCsvRecord record = sampleRecord("15970");

        when(catalogCsvReader.readStyles(stylesPath)).thenReturn(List.of(record));
        when(catalogCsvReader.readImageLinks(imagesPath))
                .thenReturn(Map.of("15970.jpg", "http://example.com/15970.jpg"));
        when(productRepository.findAllExternalIds()).thenReturn(List.of("15970"));

        catalogImportService.importCatalog(stylesPath, imagesPath);

        verify(productRepository, never()).saveAll(any());
        verify(productVariantRepository, never()).saveAll(any());
    }

    @Test
    void importCatalog_skipsSecondOccurrence_ofDuplicateIdWithinSameFile() {
        StyleCsvRecord first = sampleRecord("15970");
        StyleCsvRecord duplicate = new StyleCsvRecord("15970", "Women", "Apparel", "Topwear", "Shirts",
                "Red", "Summer", 2015, "Casual", "A different display name");

        when(catalogCsvReader.readStyles(stylesPath)).thenReturn(List.of(first, duplicate));
        when(catalogCsvReader.readImageLinks(imagesPath))
                .thenReturn(Map.of("15970.jpg", "http://example.com/15970.jpg"));
        when(productRepository.findAllExternalIds()).thenReturn(List.of());
        when(productRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        catalogImportService.importCatalog(stylesPath, imagesPath);

        ArgumentCaptor<List<Product>> captor = ArgumentCaptor.captor();
        verify(productRepository).saveAll(captor.capture());

        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().getFirst().getProductDisplayName())
                .isEqualTo("Turtle Check Men Navy Blue Shirt");
    }

    @Test
    void importCatalog_savesInSingleChunk_whenUnderBatchSize() {
        List<StyleCsvRecord> records = List.of(sampleRecord("1"), sampleRecord("2"), sampleRecord("3"));
        Map<String, String> imageLinks = Map.of(
                "1.jpg", "http://example.com/1.jpg",
                "2.jpg", "http://example.com/2.jpg",
                "3.jpg", "http://example.com/3.jpg");

        when(catalogCsvReader.readStyles(stylesPath)).thenReturn(records);
        when(catalogCsvReader.readImageLinks(imagesPath)).thenReturn(imageLinks);
        when(productRepository.findAllExternalIds()).thenReturn(List.of());
        when(productRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        catalogImportService.importCatalog(stylesPath, imagesPath);

        ArgumentCaptor<List<Product>> captor = ArgumentCaptor.captor();
        verify(productRepository, times(1)).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(3);
    }

    @Test
    void importCatalog_savesInTwoChunks_whenOverBatchSize() {
        List<StyleCsvRecord> records = new ArrayList<>();
        Map<String, String> imageLinks = new HashMap<>();
        for (int i = 0; i < 501; i++) {
            String id = "id-" + i;
            records.add(sampleRecord(id));
            imageLinks.put(id + ".jpg", "http://example.com/" + id + ".jpg");
        }

        List<List<Product>> capturedChunks = new ArrayList<>();
        when(catalogCsvReader.readStyles(stylesPath)).thenReturn(records);
        when(catalogCsvReader.readImageLinks(imagesPath)).thenReturn(imageLinks);
        when(productRepository.findAllExternalIds()).thenReturn(List.of());
        when(productRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<Product> chunk = invocation.getArgument(0);
            capturedChunks.add(new ArrayList<>(chunk)); // defensive copy — the real list gets cleared right after this call returns
            return chunk;
        });

        catalogImportService.importCatalog(stylesPath, imagesPath);

        verify(productRepository, times(2)).saveAll(anyList());
        assertThat(capturedChunks).hasSize(2);
        assertThat(capturedChunks.get(0)).hasSize(500);
        assertThat(capturedChunks.get(1)).hasSize(1);
    }

    @Test
    void importCatalog_savesNothing_whenAllRowsSkipped() {
        StyleCsvRecord noImageRecord = sampleRecord("no-image-id");
        StyleCsvRecord duplicateRecord = sampleRecord("already-exists-id");

        when(catalogCsvReader.readStyles(stylesPath)).thenReturn(List.of(noImageRecord, duplicateRecord));
        when(catalogCsvReader.readImageLinks(imagesPath))
                .thenReturn(Map.of("already-exists-id.jpg", "http://example.com/already-exists-id.jpg"));
        when(productRepository.findAllExternalIds()).thenReturn(List.of("already-exists-id"));

        catalogImportService.importCatalog(stylesPath, imagesPath);

        verify(productRepository, never()).saveAll(any());
        verify(productVariantRepository, never()).saveAll(any());
    }

    @Test
    void importCatalog_savesNothing_onEmptyInput() {
        when(catalogCsvReader.readStyles(stylesPath)).thenReturn(List.of());
        when(catalogCsvReader.readImageLinks(imagesPath)).thenReturn(Map.of());
        when(productRepository.findAllExternalIds()).thenReturn(List.of());

        catalogImportService.importCatalog(stylesPath, imagesPath);

        verify(productRepository, never()).saveAll(any());
        verify(productVariantRepository, never()).saveAll(any());
    }

    @Test
    void importCatalog_propagatesException_whenReaderFails() {
        when(catalogCsvReader.readImageLinks(imagesPath)).thenReturn(Map.of());
        when(catalogCsvReader.readStyles(stylesPath))
                .thenThrow(new UncheckedIOException(new IOException("disk error")));

        assertThatThrownBy(() -> catalogImportService.importCatalog(stylesPath, imagesPath))
                .isInstanceOf(UncheckedIOException.class);

        verify(productRepository, never()).findAllExternalIds();
        verify(productRepository, never()).saveAll(any());
        verify(productVariantRepository, never()).saveAll(any());
    }

    private StyleCsvRecord sampleRecord(String id) {
        return new StyleCsvRecord(id, "Men", "Apparel", "Topwear", "Shirts", "Navy Blue", "Fall", 2011, "Casual",
                "Turtle Check Men Navy Blue Shirt");
    }
}