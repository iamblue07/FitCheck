package com.sewlect.outfit.repository;

import com.sewlect.outfit.entity.AiPromptQuery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AiPromptQueryRepository extends JpaRepository<AiPromptQuery, UUID> {
}