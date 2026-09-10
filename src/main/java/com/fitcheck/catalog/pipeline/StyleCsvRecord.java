package com.fitcheck.catalog.pipeline;

public record StyleCsvRecord (
        String id,
        String gender,
        String masterCategory,
        String subCategory,
        String articleType,
        String baseColour,
        String season,
        Integer year,
        String usage,
        String productDisplayName
){
}
