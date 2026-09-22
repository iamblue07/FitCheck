package com.sewlect.tryon.service;

import com.sewlect.catalog.entity.Product;
import com.sewlect.identity.entity.User;
import com.sewlect.outfit.entity.Outfit;
import com.sewlect.tryon.entity.TryonRequest;
import com.sewlect.tryon.entity.TryonRequestItem;
import com.sewlect.tryon.enums.TryonRequestItemStatus;
import com.sewlect.tryon.enums.TryonRequestStatus;
import com.sewlect.tryon.repository.TryonRequestItemRepository;
import com.sewlect.tryon.repository.TryonRequestRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@AllArgsConstructor
public class TryonPersistenceService {

    private static final Set<TryonRequestStatus> IN_FLIGHT_STATUSES =
            Set.of(TryonRequestStatus.PENDING, TryonRequestStatus.PROCESSING);

    private final TryonRequestRepository tryonRequestRepository;
    private final TryonRequestItemRepository tryonRequestItemRepository;
    private final Clock clock;

    @Transactional
    public TryonRequest createPending(User user, Outfit outfit, List<Product> orderedItems) {
        TryonRequest tryonRequest = TryonRequest.builder()
                .user(user)
                .outfit(outfit)
                .status(TryonRequestStatus.PENDING)
                .build();
        tryonRequest = tryonRequestRepository.saveAndFlush(tryonRequest);

        List<TryonRequestItem> items = new ArrayList<>();
        for (int i = 0; i < orderedItems.size(); i++) {
            items.add(TryonRequestItem.builder()
                    .tryonRequest(tryonRequest)
                    .product(orderedItems.get(i))
                    .sequenceOrder(i)
                    .status(TryonRequestItemStatus.PENDING)
                    .build());
        }
        tryonRequestItemRepository.saveAll(items);

        return tryonRequest;
    }

    @Transactional
    public boolean markRequestProcessing(UUID requestId) {
        return tryonRequestRepository.transitionStatus(
                requestId, TryonRequestStatus.PENDING, TryonRequestStatus.PROCESSING, LocalDateTime.now(clock)) == 1;
    }

    @Transactional
    public boolean markItemComplete(UUID itemId) {
        return tryonRequestItemRepository.transitionStatus(
                itemId, TryonRequestItemStatus.PENDING, TryonRequestItemStatus.COMPLETE) == 1;
    }

    @Transactional
    public boolean markItemFailed(UUID itemId) {
        return tryonRequestItemRepository.transitionStatus(
                itemId, TryonRequestItemStatus.PENDING, TryonRequestItemStatus.FAILED) == 1;
    }

    @Transactional
    public boolean markRequestComplete(UUID requestId, String storageKey) {
        return tryonRequestRepository.completeIfStatusIn(
                requestId, IN_FLIGHT_STATUSES, TryonRequestStatus.COMPLETE, storageKey, LocalDateTime.now(clock)) == 1;
    }

    @Transactional
    public boolean markRequestFailed(UUID requestId, String errorMessage) {
        int updated = tryonRequestRepository.failIfStatusIn(
                requestId, IN_FLIGHT_STATUSES, TryonRequestStatus.FAILED, errorMessage, LocalDateTime.now(clock));
        if (updated == 0) {
            return false;
        }
        tryonRequestItemRepository.transitionStatusByTryonRequestId(
                requestId, TryonRequestItemStatus.PENDING, TryonRequestItemStatus.FAILED);
        return true;
    }
}