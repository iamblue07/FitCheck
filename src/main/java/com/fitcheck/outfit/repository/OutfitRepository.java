package com.fitcheck.outfit.repository;

import com.fitcheck.outfit.entity.Outfit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OutfitRepository extends JpaRepository<Outfit, UUID> {

    Optional<Outfit> findByItemSetHash(String itemSetHash);

}