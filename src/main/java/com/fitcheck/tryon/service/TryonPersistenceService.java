package com.fitcheck.tryon.service;

import com.fitcheck.catalog.entity.Product;
import com.fitcheck.common.exception.ResourceNotFoundException;
import com.fitcheck.identity.entity.User;
import com.fitcheck.outfit.entity.Outfit;
import com.fitcheck.tryon.entity.TryonRequest;
import com.fitcheck.tryon.entity.TryonRequestItem;
import com.fitcheck.tryon.enums.TryonRequestItemStatus;
import com.fitcheck.tryon.enums.TryonRequestStatus;
import com.fitcheck.tryon.repository.TryonRequestItemRepository;
import com.fitcheck.tryon.repository.TryonRequestRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class TryonPersistenceService {

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
    public void markItemComplete(UUID itemId) {
        TryonRequestItem item = tryonRequestItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Tryon request item not found: " + itemId));
        item.setStatus(TryonRequestItemStatus.COMPLETE);
        tryonRequestItemRepository.save(item);
    }

    @Transactional
    public void markItemFailed(UUID itemId) {
        TryonRequestItem item = tryonRequestItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Tryon request item not found: " + itemId));
        item.setStatus(TryonRequestItemStatus.FAILED);
        tryonRequestItemRepository.save(item);
    }

    @Transactional
    public void markRequestComplete(UUID requestId, String storageKey) {
        TryonRequest tryonRequest = tryonRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Tryon request not found: " + requestId));
        tryonRequest.setStatus(TryonRequestStatus.COMPLETE);
        tryonRequest.setResultImageStorageKey(storageKey);
        tryonRequest.setCompletedAt(LocalDateTime.now(clock));
        tryonRequestRepository.save(tryonRequest);
    }

    @Transactional
    public void markRequestFailed(UUID requestId, String errorMessage) {
        TryonRequest tryonRequest = tryonRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Tryon request not found: " + requestId));
        tryonRequest.setStatus(TryonRequestStatus.FAILED);
        tryonRequest.setErrorMessage(errorMessage);
        tryonRequest.setCompletedAt(LocalDateTime.now(clock));
        tryonRequestRepository.save(tryonRequest);

        List<TryonRequestItem> pendingItems = tryonRequestItemRepository.findByTryonRequestIdAndStatus(
                requestId, TryonRequestItemStatus.PENDING);
        for (TryonRequestItem item : pendingItems) {
            item.setStatus(TryonRequestItemStatus.FAILED);
        }
        tryonRequestItemRepository.saveAll(pendingItems);
    }
}