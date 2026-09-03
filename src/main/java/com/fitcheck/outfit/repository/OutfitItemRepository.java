package com.fitcheck.outfit.repository;

import com.fitcheck.outfit.entity.OutfitItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OutfitItemRepository extends JpaRepository<OutfitItem, UUID> {
}