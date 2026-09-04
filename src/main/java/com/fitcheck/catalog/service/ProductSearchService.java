package com.fitcheck.catalog.service;

import com.fitcheck.catalog.entity.Product;
import com.fitcheck.catalog.repository.ProductRepository;
import com.fitcheck.common.taxonomy.GarmentRole;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScoringFunction;
import org.springframework.data.domain.SearchResults;
import org.springframework.data.domain.Vector;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Service
@AllArgsConstructor
public class ProductSearchService {

    private final ProductRepository productRepository;

    public List<Product> findEligible(GarmentRole role, Set<String> genders, BigDecimal priceCeiling) {
        return productRepository.findByGarmentRoleAndGenderInAndBasePriceLessThanEqual(role, genders, priceCeiling);
    }

    public SearchResults<Product> findNearest(GarmentRole role, Set<String> genders, BigDecimal priceCeiling,
                                              Vector referenceEmbedding, ScoringFunction scoringFunction, Limit limit) {
        return productRepository.searchByGarmentRoleAndGenderInAndBasePriceLessThanEqualAndTextEmbeddingNear(
                role, genders, priceCeiling, referenceEmbedding, scoringFunction, limit);
    }

    public SearchResults<Product> findNearestByOccasion(GarmentRole role, Set<String> genders, BigDecimal priceCeiling,
                                                        String occasion, Vector referenceEmbedding,
                                                        ScoringFunction scoringFunction, Limit limit) {
        return productRepository.searchByGarmentRoleAndGenderInAndBasePriceLessThanEqualAndOccasionAndTextEmbeddingNear(
                role, genders, priceCeiling, occasion, referenceEmbedding, scoringFunction, limit);
    }
}