package com.fitcheck.catalog.repository;

import com.fitcheck.catalog.entity.Product;
import com.fitcheck.common.taxonomy.GarmentRole;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScoringFunction;
import org.springframework.data.domain.SearchResults;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Vector;
import org.springframework.data.repository.query.Param;

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

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Product p SET p.garmentRole = :role WHERE p.id IN :ids")
    int updateGarmentRoleByIdIn(@Param("role") GarmentRole role, @Param("ids") Collection<UUID> ids);

    List<Product> findByGarmentRoleAndGenderInAndBasePriceLessThanEqual(
            GarmentRole garmentRole, Collection<String> genders, BigDecimal priceCeiling
    );

    List<Product> findByArticleTypeAndGenderInAndIdNot(
            String articleType, Collection<String> genders, UUID excludeId, Limit limit
    );

    SearchResults<Product> searchByGarmentRoleAndGenderInAndBasePriceLessThanEqualAndTextEmbeddingNear(
            GarmentRole garmentRole, Collection<String> genders, BigDecimal priceCeiling,
            Vector referenceEmbedding, ScoringFunction scoringFunction, Limit limit);

    SearchResults<Product> searchByGarmentRoleAndGenderInAndBasePriceLessThanEqualAndOccasionAndTextEmbeddingNear(
            GarmentRole garmentRole, Collection<String> genders, BigDecimal priceCeiling, String occasion,
            Vector referenceEmbedding, ScoringFunction scoringFunction, Limit limit);

}