package com.fitcheck.catalog.repository;

import com.fitcheck.catalog.entity.Product;
import com.fitcheck.common.taxonomy.GarmentRole;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScoringFunction;
import org.springframework.data.domain.SearchResults;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Vector;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    @Query("SELECT p.externalId FROM Product p")
    List<String> findAllExternalIds();

    Optional<Product> findFirstByDescriptionIsNullOrderByCreatedAtAsc();

    Optional<Product> findFirstByDescriptionIsNullAndIdNotInOrderByCreatedAtAsc(Collection<UUID> excludedIds);

    long countByDescriptionIsNotNull();

    List<Product> findAllByDescriptionIsNotNullAndTextEmbeddingIsNull(Limit limit);

    List<Product> findAllByDescriptionIsNotNullAndTextEmbeddingIsNullAndIdNotIn(Collection<UUID> excludedIds, Limit limit);

    long countByTextEmbeddingIsNotNull();

    List<Product> findAllByGarmentRoleIsNull(Limit limit);

    List<Product> findAllByGarmentRoleIsNullAndIdNotIn(Collection<UUID> excludedIds, Limit limit);

    List<Product> findByGarmentRoleAndGenderInAndBasePriceLessThanEqual(
            GarmentRole garmentRole, Collection<String> genders, BigDecimal priceCeiling
    );

    SearchResults<Product> searchByGarmentRoleAndGenderInAndBasePriceLessThanEqualAndTextEmbeddingNear(
            GarmentRole garmentRole, Collection<String> genders, BigDecimal priceCeiling,
            Vector referenceEmbedding, ScoringFunction scoringFunction, Limit limit);

    SearchResults<Product> searchByGarmentRoleAndGenderInAndBasePriceLessThanEqualAndOccasionAndTextEmbeddingNear(
            GarmentRole garmentRole, Collection<String> genders, BigDecimal priceCeiling, String occasion,
            Vector referenceEmbedding, ScoringFunction scoringFunction, Limit limit);

}



