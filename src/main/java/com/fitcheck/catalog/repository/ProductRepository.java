package com.fitcheck.catalog.repository;

import com.fitcheck.catalog.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    @Query("SELECT p.externalId FROM Product p")
    List<String> findAllExternalIds();
}
