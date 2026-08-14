package com.fitcheck.catalog.pipeline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogCsvReaderTest {

    @TempDir
    private Path tempDir;

    private final CatalogCsvReader reader = new CatalogCsvReader();

    // ---- readStyles ----

    @Test
    void readStyles_returnsAllRows_whenCsvIsWellFormed() throws IOException {
        String csv = """
                id,gender,masterCategory,subCategory,articleType,baseColour,season,year,usage,productDisplayName
                15970,Men,Apparel,Topwear,Shirts,Navy Blue,Fall,2011,Casual,Turtle Check Men Navy Blue Shirt
                39386,Men,Apparel,Bottomwear,Jeans,Blue,Summer,2012,Casual,Peter England Men Party Blue Jeans
                59263,Women,Accessories,Watches,Watches,Silver,Winter,2016,Casual,Titan Women Silver Watch
                """;
        Path csvPath = writeCsv("styles.csv", csv);

        List<StyleCsvRecord> records = reader.readStyles(csvPath);

        assertThat(records).containsExactly(
                new StyleCsvRecord("15970", "Men", "Apparel", "Topwear", "Shirts", "Navy Blue", "Fall", 2011,
                        "Casual", "Turtle Check Men Navy Blue Shirt"),
                new StyleCsvRecord("39386", "Men", "Apparel", "Bottomwear", "Jeans", "Blue", "Summer", 2012,
                        "Casual", "Peter England Men Party Blue Jeans"),
                new StyleCsvRecord("59263", "Women", "Accessories", "Watches", "Watches", "Silver", "Winter", 2016,
                        "Casual", "Titan Women Silver Watch"));
    }

    @Test
    void readStyles_preservesEmbeddedComma_inQuotedProductDisplayName() throws IOException {
        String csv = """
                id,gender,masterCategory,subCategory,articleType,baseColour,season,year,usage,productDisplayName
                15970,Men,Apparel,Topwear,Shirts,Navy Blue,Fall,2011,Casual,"Nike, Men's Running Shoes"
                """;
        Path csvPath = writeCsv("styles.csv", csv);

        List<StyleCsvRecord> records = reader.readStyles(csvPath);

        assertThat(records).hasSize(1);
        assertThat(records.getFirst().id()).isEqualTo("15970");
        assertThat(records.getFirst().productDisplayName()).isEqualTo("Nike, Men's Running Shoes");
    }

    @Test
    void readStyles_trimsSurroundingWhitespace_onFieldValues() throws IOException {
        String csv = """
                id,gender,masterCategory,subCategory,articleType,baseColour,season,year,usage,productDisplayName
                15970,  Men  ,Apparel,Topwear,Shirts,Navy Blue,Fall,2011,Casual,Turtle Check Shirt
                """;
        Path csvPath = writeCsv("styles.csv", csv);

        List<StyleCsvRecord> records = reader.readStyles(csvPath);

        assertThat(records).hasSize(1);
        assertThat(records.getFirst().gender()).isEqualTo("Men");
    }

    @Test
    void readStyles_skipsRow_withUnparseableYear() throws IOException {
        String csv = """
                id,gender,masterCategory,subCategory,articleType,baseColour,season,year,usage,productDisplayName
                15970,Men,Apparel,Topwear,Shirts,Navy Blue,Fall,2011,Casual,Turtle Check Shirt
                39386,Men,Apparel,Bottomwear,Jeans,Blue,Summer,N/A,Casual,Blue Jeans
                59263,Women,Accessories,Watches,Watches,Silver,Winter,2016,Casual,Silver Watch
                """;
        Path csvPath = writeCsv("styles.csv", csv);

        List<StyleCsvRecord> records = reader.readStyles(csvPath);

        assertThat(records).hasSize(2);
        assertThat(records).extracting(StyleCsvRecord::id).containsExactly("15970", "59263");
    }

    @Test
    void readStyles_skipsRow_withMissingColumn() throws IOException {
        String csv = """
                id,gender,masterCategory,subCategory,articleType,baseColour,season,year,usage,productDisplayName
                15970,Men,Apparel,Topwear,Shirts,Navy Blue,Fall,2011,Casual,Turtle Check Shirt
                39386,Men,Apparel,Bottomwear,Jeans,Blue,Summer,2012,Casual
                59263,Women,Accessories,Watches,Watches,Silver,Winter,2016,Casual,Silver Watch
                """;
        Path csvPath = writeCsv("styles.csv", csv);

        List<StyleCsvRecord> records = reader.readStyles(csvPath);

        assertThat(records).hasSize(2);
        assertThat(records).extracting(StyleCsvRecord::id).containsExactly("15970", "59263");
    }

    @Test
    void readStyles_returnsEmptyList_whenCsvHasOnlyHeaderRow() throws IOException {
        String csv = """
                id,gender,masterCategory,subCategory,articleType,baseColour,season,year,usage,productDisplayName
                """;
        Path csvPath = writeCsv("styles.csv", csv);

        List<StyleCsvRecord> records = reader.readStyles(csvPath);

        assertThat(records).isEmpty();
    }

    @Test
    void readStyles_throwsUncheckedIOException_whenFileDoesNotExist() {
        Path missingPath = tempDir.resolve("does-not-exist.csv");

        assertThatThrownBy(() -> reader.readStyles(missingPath))
                .isInstanceOf(UncheckedIOException.class);
    }

    // ---- readImageLinks ----

    @Test
    void readImageLinks_returnsFilenameToLinkMap_whenCsvIsWellFormed() throws IOException {
        String csv = """
                filename,link
                15970.jpg,http://example.com/images/15970.jpg
                39386.jpg,http://example.com/images/39386.jpg
                """;
        Path csvPath = writeCsv("images.csv", csv);

        Map<String, String> links = reader.readImageLinks(csvPath);

        assertThat(links).containsExactlyInAnyOrderEntriesOf(Map.of(
                "15970.jpg", "http://example.com/images/15970.jpg",
                "39386.jpg", "http://example.com/images/39386.jpg"));
    }

    @Test
    void readImageLinks_lastRowWins_onDuplicateFilename() throws IOException {
        String csv = """
                filename,link
                15970.jpg,http://example.com/old-link.jpg
                15970.jpg,http://example.com/new-link.jpg
                """;
        Path csvPath = writeCsv("images.csv", csv);

        Map<String, String> links = reader.readImageLinks(csvPath);

        assertThat(links).hasSize(1);
        assertThat(links).containsEntry("15970.jpg", "http://example.com/new-link.jpg");
    }

    @Test
    void readImageLinks_throwsUncheckedIOException_whenFileDoesNotExist() {
        Path missingPath = tempDir.resolve("does-not-exist.csv");

        assertThatThrownBy(() -> reader.readImageLinks(missingPath))
                .isInstanceOf(UncheckedIOException.class);
    }

    private Path writeCsv(String fileName, String content) throws IOException {
        Path path = tempDir.resolve(fileName);
        Files.writeString(path, content);
        return path;
    }
}