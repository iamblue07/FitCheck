package com.fitcheck.social.repository;

import com.fitcheck.social.entity.UserOutfitInteraction;
import com.fitcheck.social.enums.InteractionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserOutfitInteractionRepository extends JpaRepository<UserOutfitInteraction, UUID> {

    boolean existsByUserIdAndOutfitIdAndInteractionType(UUID userId, UUID outfitId, InteractionType interactionType);

    long deleteByUserIdAndOutfitIdAndInteractionType(UUID userId, UUID outfitId, InteractionType interactionType);

    @Query(value = """
            SELECT i FROM UserOutfitInteraction i
            JOIN FETCH i.outfit
            WHERE i.user.id = :userId AND i.interactionType = :interactionType
            ORDER BY i.createdAt DESC, i.id DESC
            """,
            countQuery = """
                    SELECT COUNT(i) FROM UserOutfitInteraction i
                    WHERE i.user.id = :userId AND i.interactionType = :interactionType
                    """)
    Page<UserOutfitInteraction> findByUserIdAndInteractionTypeOrderByCreatedAtDesc(
            @Param("userId") UUID userId,
            @Param("interactionType") InteractionType interactionType,
            Pageable pageable);

    @Query("SELECT i.outfit.id FROM UserOutfitInteraction i "
            + "WHERE i.user.id = :userId AND i.interactionType = :interactionType")
    List<UUID> findOutfitIdsByUserIdAndInteractionType(@Param("userId") UUID userId,
                                                       @Param("interactionType") InteractionType interactionType);

}