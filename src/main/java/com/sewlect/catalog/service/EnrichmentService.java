package com.sewlect.catalog.service;

import com.sewlect.catalog.entity.Product;
import com.sewlect.catalog.domain.ProductEnrichmentResult;

public interface EnrichmentService {

    ProductEnrichmentResult enrich(Product product);
}
