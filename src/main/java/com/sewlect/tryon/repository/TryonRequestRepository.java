package com.sewlect.tryon.repository;

import com.sewlect.tryon.entity.TryonRequest;
import com.sewlect.tryon.enums.TryonRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

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
}