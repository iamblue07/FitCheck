package com.fitcheck.outfit.repository;

import com.fitcheck.outfit.entity.AiPromptQueryOutfit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AiPromptQueryOutfitRepository extends JpaRepository<AiPromptQueryOutfit, UUID> {
}