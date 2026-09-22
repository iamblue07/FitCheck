package com.sewlect.tryon.repository;

import com.sewlect.tryon.entity.TryonRequest;
import com.sewlect.tryon.enums.TryonRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TryonRequestRepository extends JpaRepository<TryonRequest, UUID> {

    Optional<TryonRequest> findFirstByUserIdAndOutfitIdAndStatusInOrderByCreatedAtDesc(
            UUID userId, UUID outfitId, Collection<TryonRequestStatus> statuses);

    Optional<TryonRequest> findFirstByUserIdAndOutfitIdAndStatusOrderByCompletedAtDesc(
            UUID userId, UUID outfitId, TryonRequestStatus status);

    List<TryonRequest> findByStatusInAndUpdatedAtBefore(
            Collection<TryonRequestStatus> statuses, LocalDateTime cutoff);

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE TryonRequest r
            SET r.status = :target, r.updatedAt = :now
            WHERE r.id = :id AND r.status = :expected
            """)
    int transitionStatus(
            @Param("id") UUID id,
            @Param("expected") TryonRequestStatus expected,
            @Param("target") TryonRequestStatus target,
            @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE TryonRequest r
            SET r.status = :target, r.resultImageStorageKey = :storageKey, r.completedAt = :now, r.updatedAt = :now
            WHERE r.id = :id AND r.status IN :expected
            """)
    int completeIfStatusIn(
            @Param("id") UUID id,
            @Param("expected") Collection<TryonRequestStatus> expected,
            @Param("target") TryonRequestStatus target,
            @Param("storageKey") String storageKey,
            @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE TryonRequest r
            SET r.status = :target, r.errorMessage = :errorMessage, r.completedAt = :now, r.updatedAt = :now
            WHERE r.id = :id AND r.status IN :expected
            """)
    int failIfStatusIn(
            @Param("id") UUID id,
            @Param("expected") Collection<TryonRequestStatus> expected,
            @Param("target") TryonRequestStatus target,
            @Param("errorMessage") String errorMessage,
            @Param("now") LocalDateTime now);
}