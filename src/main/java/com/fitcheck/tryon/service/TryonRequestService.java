package com.fitcheck.tryon.service;

import com.fitcheck.catalog.entity.Product;
import com.fitcheck.common.exception.RateLimitExceededException;
import com.fitcheck.common.exception.ResourceNotFoundException;
import com.fitcheck.common.ratelimit.InMemoryRateLimiter;
import com.fitcheck.common.storage.service.StorageService;
import com.fitcheck.identity.entity.User;
import com.fitcheck.identity.service.UserReferenceQueryService;
import com.fitcheck.outfit.entity.Outfit;
import com.fitcheck.outfit.service.OutfitItemQueryService;
import com.fitcheck.tryon.dto.TryonStatusResponse;
import com.fitcheck.tryon.entity.TryonRequest;
import com.fitcheck.tryon.enums.TryonRequestStatus;
import com.fitcheck.tryon.properties.TryonProperties;
import com.fitcheck.tryon.repository.TryonRequestRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@AllArgsConstructor
@EnableConfigurationProperties(TryonProperties.class)
public class TryonRequestService {

    private static final String RATE_LIMIT_OPERATION_KEY = "tryon-submit";

    private final OutfitItemQueryService outfitItemQueryService;
    private final InMemoryRateLimiter inMemoryRateLimiter;
    private final TryonPersistenceService tryonPersistenceService;
    private final TryonRequestRepository tryonRequestRepository;
    private final UserReferenceQueryService userReferenceQueryService;
    private final StorageService storageService;
    private final TryonProperties properties;
    private final TryonJobExecutor tryonJobExecutor;

    @Qualifier("tryonExecutor")
    private final AsyncTaskExecutor tryonExecutor;

    public TryonStatusResponse submit(UUID userId, UUID outfitId) {
        List<Product> orderedProducts = outfitItemQueryService.findProductsForTryon(outfitId);

        boolean consumed = inMemoryRateLimiter.tryConsume(
                userId, RATE_LIMIT_OPERATION_KEY, properties.rateLimitPerHour(), Duration.ofHours(1));
        if (!consumed) {
            throw new RateLimitExceededException("Rate limit exceeded for this operation - try again later");
        }

        User user = userReferenceQueryService.getReference(userId);
        Outfit outfit = outfitItemQueryService.getReference(outfitId);
        TryonRequest tryonRequest = tryonPersistenceService.createPending(user, outfit, orderedProducts);
        UUID tryonRequestId = tryonRequest.getId();

        tryonExecutor.submit(() -> {
            try {
                tryonJobExecutor.run(tryonRequestId);
            } catch (RuntimeException e) {
                log.error("Background tryon job failed for request {}", tryonRequestId, e);
                tryonPersistenceService.markRequestFailed(tryonRequestId, e.getMessage());
            }
        });

        return new TryonStatusResponse(tryonRequestId, tryonRequest.getStatus(), null, null);
    }

    public TryonStatusResponse getStatus(UUID userId, UUID requestId) {
        TryonRequest tryonRequest = tryonRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Tryon request not found: " + requestId));

        if (!tryonRequest.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Tryon request not found: " + requestId);
        }

        String resultImageUrl = null;
        if (tryonRequest.getStatus() == TryonRequestStatus.COMPLETE) {
            resultImageUrl = storageService
                    .generateDownloadUrl(tryonRequest.getResultImageStorageKey(), StorageService.DEFAULT_TTL)
                    .toString();
        }

        return new TryonStatusResponse(
                tryonRequest.getId(), tryonRequest.getStatus(), resultImageUrl, tryonRequest.getErrorMessage());
    }
}