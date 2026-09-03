package com.fitcheck.catalog.repository;

import com.fitcheck.catalog.entity.ProductStyleTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProductStyleTagRepository extends JpaRepository<ProductStyleTag, UUID> {

    List<UUID> findDistinctProductIdByStyleTagIdIn(Collection<UUID> styleTagIds);
}
