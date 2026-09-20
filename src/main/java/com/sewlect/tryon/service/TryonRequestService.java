package com.sewlect.tryon.service;

import com.sewlect.catalog.entity.Product;
import com.sewlect.common.exception.BadRequestException;
import com.sewlect.common.exception.RateLimitExceededException;
import com.sewlect.common.exception.ResourceNotFoundException;
import com.sewlect.common.ratelimit.InMemoryRateLimiter;
import com.sewlect.common.storage.service.StorageService;
import com.sewlect.identity.entity.User;
import com.sewlect.identity.enums.PhotoType;
import com.sewlect.identity.service.PhotoService;
import com.sewlect.identity.service.UserReferenceQueryService;
import com.sewlect.outfit.entity.Outfit;
import com.sewlect.outfit.service.OutfitItemQueryService;
import com.sewlect.tryon.dto.TryonStatusResponse;
import com.sewlect.tryon.entity.TryonRequest;
import com.sewlect.tryon.enums.TryonRequestStatus;
import com.sewlect.tryon.properties.TryonProperties;
import com.sewlect.tryon.repository.TryonRequestRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@AllArgsConstructor
@EnableConfigurationProperties(TryonProperties.class)
public class TryonRequestService {

    private static final String RATE_LIMIT_OPERATION_KEY = "tryon-submit";

    private static final Set<TryonRequestStatus> IN_FLIGHT_STATUSES =
            Set.of(TryonRequestStatus.PENDING, TryonRequestStatus.PROCESSING);

    private final OutfitItemQueryService outfitItemQueryService;
    private final InMemoryRateLimiter inMemoryRateLimiter;
    private final TryonPersistenceService tryonPersistenceService;
    private final TryonRequestRepository tryonRequestRepository;
    private final UserReferenceQueryService userReferenceQueryService;
    private final PhotoService photoService;
    private final StorageService storageService;
    private final TryonProperties properties;
    private final Clock clock;
    private final TryonJobExecutor tryonJobExecutor;

    @Qualifier("tryonExecutor")
    private final AsyncTaskExecutor tryonExecutor;

    public TryonStatusResponse submit(UUID userId, UUID outfitId) {
        List<Product> orderedProducts = outfitItemQueryService.findProductsForTryon(outfitId);
        if (orderedProducts.isEmpty()) {
            throw new BadRequestException("Outfit " + outfitId + " has no try-on eligible items");
        }

        Optional<TryonRequest> inFlight = tryonRequestRepository
                .findFirstByUserIdAndOutfitIdAndStatusInOrderByCreatedAtDesc(userId, outfitId, IN_FLIGHT_STATUSES);
        if (inFlight.isPresent()) {
            TryonRequest existing = inFlight.get();
            if (isStale(existing)) {
                log.warn("Tryon submit for user {} outfit {} ignored abandoned in-flight request {} last updated at {}",
                        userId, outfitId, existing.getId(), existing.getUpdatedAt());
            } else {
                log.info("Tryon submit for user {} outfit {} returned in-flight request {} instead of starting a new one",
                        userId, outfitId, existing.getId());
                return new TryonStatusResponse(existing.getId(), existing.getStatus(), null, null);
            }
        }

        Optional<TryonRequest> reusable = findReusableResult(userId, outfitId);
        if (reusable.isPresent()) {
            TryonRequest existing = reusable.get();
            log.info("Tryon submit for user {} outfit {} reused completed request {} instead of rendering again",
                    userId, outfitId, existing.getId());
            return getStatus(userId, existing.getId());
        }

        boolean consumed = inMemoryRateLimiter.tryConsume(
                userId.toString(), RATE_LIMIT_OPERATION_KEY, properties.rateLimitPerHour(), Duration.ofHours(1));
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

    private boolean isStale(TryonRequest tryonRequest) {
        if (tryonRequest.getUpdatedAt() == null) {
            return true;
        }
        LocalDateTime cutoff = LocalDateTime.now(clock)
                .minus(Duration.ofMillis(properties.staleInFlightThresholdMs()));
        return tryonRequest.getUpdatedAt().isBefore(cutoff);
    }

    private Optional<TryonRequest> findReusableResult(UUID userId, UUID outfitId) {
        Optional<TryonRequest> completed = tryonRequestRepository
                .findFirstByUserIdAndOutfitIdAndStatusOrderByCompletedAtDesc(
                        userId, outfitId, TryonRequestStatus.COMPLETE);
        if (completed.isEmpty() || completed.get().getCompletedAt() == null) {
            return Optional.empty();
        }

        Optional<LocalDateTime> photoLastModifiedAt = photoService.getLastModifiedAt(userId, PhotoType.FRONT);
        if (photoLastModifiedAt.isEmpty()) {
            return Optional.empty();
        }

        return completed.get().getCompletedAt().isAfter(photoLastModifiedAt.get())
                ? completed
                : Optional.empty();
    }
}