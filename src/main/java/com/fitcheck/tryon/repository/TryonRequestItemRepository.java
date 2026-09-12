package com.fitcheck.tryon.repository;

import com.fitcheck.tryon.entity.TryonRequestItem;
import com.fitcheck.tryon.enums.TryonRequestItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TryonRequestItemRepository extends JpaRepository<TryonRequestItem, UUID> {

    @Query("SELECT i FROM TryonRequestItem i JOIN FETCH i.product WHERE i.tryonRequest.id = :tryonRequestId ORDER BY i.sequenceOrder")
    List<TryonRequestItem> findByTryonRequestIdOrderBySequenceOrder(@Param("tryonRequestId") UUID tryonRequestId);

    List<TryonRequestItem> findByTryonRequestIdAndStatus(UUID tryonRequestId, TryonRequestItemStatus status);
}