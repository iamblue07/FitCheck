package com.sewlect.tryon.service;

import com.sewlect.catalog.entity.Product;
import com.sewlect.common.ai.properties.FashnProperties;
import com.sewlect.common.exception.ExternalServiceException;
import com.sewlect.common.exception.ResourceNotFoundException;
import com.sewlect.common.storage.service.StorageService;
import com.sewlect.common.storage.util.StorageKeys;
import com.sewlect.common.taxonomy.enums.GarmentRole;
import com.sewlect.identity.enums.PhotoType;
import com.sewlect.identity.service.PhotoService;
import com.sewlect.tryon.entity.TryonRequest;
import com.sewlect.tryon.entity.TryonRequestItem;
import com.sewlect.tryon.properties.FashnModelProperties;
import com.sewlect.tryon.properties.TryonProperties;
import com.sewlect.tryon.repository.TryonRequestItemRepository;
import com.sewlect.tryon.repository.TryonRequestRepository;
import com.sewlect.tryon.support.TryonStepExecutor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

@Slf4j
@Service
@AllArgsConstructor
@EnableConfigurationProperties({TryonProperties.class, FashnModelProperties.class})
public class TryonJobExecutor {

    private final TryonRequestRepository tryonRequestRepository;
    private final TryonRequestItemRepository tryonRequestItemRepository;
    private final TryonStepExecutor tryonStepExecutor;
    private final FashnClient fashnClient;
    private final TryonPersistenceService tryonPersistenceService;
    private final PhotoService photoService;
    private final StorageService storageService;
    private final TryonProperties properties;
    private final FashnModelProperties modelProperties;
    private final FashnProperties fashnProperties;
    private final HttpClient httpClient;
    private final Clock clock;

    public void run(UUID tryonRequestId) {
        if (!tryonPersistenceService.markRequestProcessing(tryonRequestId)) {
            log.warn("Tryon job {} skipped: request is no longer pending", tryonRequestId);
            return;
        }

        TryonRequest tryonRequest = tryonRequestRepository.findById(tryonRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Tryon request not found: " + tryonRequestId));
        List<TryonRequestItem> items = tryonRequestItemRepository.findByTryonRequestIdOrderBySequenceOrder(tryonRequestId);

        String frontPhotoStorageKey = photoService.getStorageKey(tryonRequest.getUser().getId(), PhotoType.FRONT);
        Instant jobDeadline = clock.instant().plus(Duration.ofMillis(properties.jobTimeoutMs()));

        String currentImageUrl = null;

        for (TryonRequestItem item : items) {
            if (!clock.instant().isBefore(jobDeadline)) {
                String message = "Try-on job exceeded its " + properties.jobTimeoutMs() + "ms budget";
                log.warn("Tryon job {} abandoned: {}", tryonRequestId, message);
                tryonPersistenceService.markItemFailed(item.getId());
                tryonPersistenceService.markRequestFailed(tryonRequestId, message);
                return;
            }

            String previousImageUrl = currentImageUrl;
            Supplier<String> modelImageUrlSupplier = previousImageUrl == null
                    ? () -> presignedFrontPhotoUrl(frontPhotoStorageKey)
                    : () -> previousImageUrl;
            Product product = item.getProduct();

            try {
                currentImageUrl = tryonStepExecutor.execute(
                        submitterFor(modelImageUrlSupplier, product), jobDeadline);
            } catch (ExternalServiceException e) {
                log.warn("Tryon step failed for request {} item {}: {}", tryonRequestId, item.getId(), e.getMessage());
                tryonPersistenceService.markItemFailed(item.getId());
                tryonPersistenceService.markRequestFailed(tryonRequestId, e.getMessage());
                return;
            }
            if (!tryonPersistenceService.markItemComplete(item.getId())) {
                log.warn("Tryon job {} stopped: request was terminated while item {} was running",
                        tryonRequestId, item.getId());
                return;
            }
        }

        byte[] resultBytes = downloadBytes(currentImageUrl);
        String storageKey = StorageKeys.tryonResultKey(tryonRequestId);
        storageService.store(storageKey, resultBytes, "image/" + modelProperties.outputFormat());
        if (!tryonPersistenceService.markRequestComplete(tryonRequestId, storageKey)) {
            log.warn("Tryon job {} finished after the request was terminated; result {} was not recorded",
                    tryonRequestId, storageKey);
        }
    }

    private Supplier<String> submitterFor(Supplier<String> modelImageUrlSupplier, Product product) {
        GarmentRole role = product.getGarmentRole();
        if (role == GarmentRole.FOOTWEAR || role == GarmentRole.ACCESSORY) {
            return () -> fashnClient.submitTryonMax(modelImageUrlSupplier.get(), product.getImageUrl());
        }
        return () -> fashnClient.submitTryonV16(
                modelImageUrlSupplier.get(), product.getImageUrl(), resolveCategory(role));
    }

    private String resolveCategory(GarmentRole role) {
        if (role == GarmentRole.FULL_BODY) {
            return "one-pieces";
        }
        if (role == GarmentRole.TOP || role == GarmentRole.OUTERWEAR) {
            return "tops";
        }
        return "bottoms";
    }

    private String presignedFrontPhotoUrl(String storageKey) {
        return storageService.generateDownloadUrl(storageKey, StorageService.DEFAULT_TTL).toString();
    }

    private byte[] downloadBytes(String imageUrl) {
        long timeoutMs = fashnProperties.resultDownloadTimeout().toMillis();
        HttpRequest request = HttpRequest.newBuilder(URI.create(imageUrl)).GET().build();
        CompletableFuture<HttpResponse<byte[]>> pending =
                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
        try {
            HttpResponse<byte[]> response = pending.get(timeoutMs, TimeUnit.MILLISECONDS);

            if (response.statusCode() != 200) {
                throw new ExternalServiceException(
                        "Failed to download FASHN try-on result, HTTP " + response.statusCode() + ": " + imageUrl);
            }
            return response.body();
        } catch (TimeoutException e) {
            pending.cancel(true);
            throw new ExternalServiceException(
                    "Timed out after " + timeoutMs + "ms downloading FASHN try-on result: " + imageUrl);
        } catch (ExecutionException e) {
            throw new ExternalServiceException(
                    "Failed to download FASHN try-on result " + imageUrl + ": " + e.getCause().getMessage());
        } catch (InterruptedException e) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw new ExternalServiceException("Interrupted while downloading FASHN try-on result: " + imageUrl);
        }
    }
}