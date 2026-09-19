package com.fitcheck.tryon.repository;

import com.fitcheck.tryon.entity.TryonRequest;
import com.fitcheck.tryon.enums.TryonRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface TryonRequestRepository extends JpaRepository<TryonRequest, UUID> {

    Optional<TryonRequest> findFirstByUserIdAndOutfitIdAndStatusInOrderByCreatedAtDesc(
            UUID userId, UUID outfitId, Collection<TryonRequestStatus> statuses);

    Optional<TryonRequest> findFirstByUserIdAndOutfitIdAndStatusOrderByCompletedAtDesc(
            UUID userId, UUID outfitId, TryonRequestStatus status);
}