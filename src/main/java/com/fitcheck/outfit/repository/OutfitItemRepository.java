package com.fitcheck.outfit.repository;

import com.fitcheck.outfit.entity.OutfitItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface OutfitItemRepository extends JpaRepository<OutfitItem, UUID> {

    @Query("SELECT DISTINCT oi.product.id FROM OutfitItem oi WHERE oi.outfit.id = :outfitId")
    List<UUID> findDistinctProductIdByOutfitId(@Param("outfitId") UUID outfitId);

    @Query("SELECT oi FROM OutfitItem oi JOIN FETCH oi.product WHERE oi.outfit.id = :outfitId")
    List<OutfitItem> findByOutfitId(@Param("outfitId") UUID outfitId);

    @Query("SELECT COALESCE(SUM(oi.product.basePrice), 0) FROM OutfitItem oi WHERE oi.outfit.id = :outfitId")
    BigDecimal sumBasePriceByOutfitId(@Param("outfitId") UUID outfitId);
}