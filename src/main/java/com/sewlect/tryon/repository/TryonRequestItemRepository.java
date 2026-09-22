package com.sewlect.tryon.repository;

import com.sewlect.tryon.entity.TryonRequestItem;
import com.sewlect.tryon.enums.TryonRequestItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TryonRequestItemRepository extends JpaRepository<TryonRequestItem, UUID> {

    @Query("SELECT i FROM TryonRequestItem i JOIN FETCH i.product WHERE i.tryonRequest.id = :tryonRequestId ORDER BY i.sequenceOrder")
    List<TryonRequestItem> findByTryonRequestIdOrderBySequenceOrder(@Param("tryonRequestId") UUID tryonRequestId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE TryonRequestItem i SET i.status = :target WHERE i.id = :id AND i.status = :expected")
    int transitionStatus(
            @Param("id") UUID id,
            @Param("expected") TryonRequestItemStatus expected,
            @Param("target") TryonRequestItemStatus target);

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE TryonRequestItem i SET i.status = :target
            WHERE i.tryonRequest.id = :tryonRequestId AND i.status = :expected
            """)
    int transitionStatusByTryonRequestId(
            @Param("tryonRequestId") UUID tryonRequestId,
            @Param("expected") TryonRequestItemStatus expected,
            @Param("target") TryonRequestItemStatus target);
}