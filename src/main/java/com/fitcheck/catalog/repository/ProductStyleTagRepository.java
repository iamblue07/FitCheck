package com.fitcheck.catalog.repository;

import com.fitcheck.catalog.entity.ProductStyleTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductStyleTagRepository extends JpaRepository<ProductStyleTag, UUID> {
}
