package com.fitcheck.catalog.repository;

import com.fitcheck.catalog.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {


}
