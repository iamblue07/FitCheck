package com.fitcheck.catalog.service;

import com.fitcheck.catalog.entity.Product;
import com.fitcheck.catalog.pipeline.ProductEnrichmentResult;

public interface EnrichmentService {

    ProductEnrichmentResult enrich(Product product);
}
