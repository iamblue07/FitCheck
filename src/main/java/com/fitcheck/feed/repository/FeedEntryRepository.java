package com.fitcheck.feed.repository;

import com.fitcheck.feed.entity.FeedEntry;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface FeedEntryRepository extends JpaRepository<FeedEntry, UUID> {

    long countByUserIdAndShownAtIsNull(UUID userId);

    @Query("""
            SELECT f FROM FeedEntry f
            JOIN FETCH f.outfit
            WHERE f.user.id = :userId AND f.shownAt IS NULL
            ORDER BY f.rankScore DESC, f.id DESC
            """)
    List<FeedEntry> findByUserIdAndShownAtIsNullOrderByRankScoreDescIdDesc(@Param("userId") UUID userId, Limit limit);

    @Query("""
            SELECT f FROM FeedEntry f
            JOIN FETCH f.outfit
            WHERE f.user.id = :userId AND f.shownAt IS NULL
            AND (f.rankScore < :cursorScore OR (f.rankScore = :cursorScore AND f.id < :cursorId))
            ORDER BY f.rankScore DESC, f.id DESC
            """)
    List<FeedEntry> findNextPage(
            @Param("userId") UUID userId,
            @Param("cursorScore") BigDecimal cursorScore,
            @Param("cursorId") UUID cursorId,
            Limit limit);

    boolean existsByUserIdAndOutfitId(UUID userId, UUID outfitId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE FeedEntry f SET f.shownAt = :shownAt WHERE f.id IN :ids")
    void markShown(@Param("ids") List<UUID> ids, @Param("shownAt") LocalDateTime shownAt);

}