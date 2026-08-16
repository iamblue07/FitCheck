package com.fitcheck.catalog.repository;

import com.fitcheck.catalog.entity.Product;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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

}

