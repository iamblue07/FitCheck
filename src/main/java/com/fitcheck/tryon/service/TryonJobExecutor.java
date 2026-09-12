package com.fitcheck.tryon.service;

import com.fitcheck.catalog.entity.Product;
import com.fitcheck.common.exception.ExternalServiceException;
import com.fitcheck.common.exception.ResourceNotFoundException;
import com.fitcheck.common.storage.service.StorageService;
import com.fitcheck.common.storage.util.StorageKeys;
import com.fitcheck.common.taxonomy.enums.GarmentRole;
import com.fitcheck.identity.enums.PhotoType;
import com.fitcheck.identity.service.PhotoService;
import com.fitcheck.tryon.entity.TryonRequest;
import com.fitcheck.tryon.entity.TryonRequestItem;
import com.fitcheck.tryon.properties.TryonProperties;
import com.fitcheck.tryon.repository.TryonRequestItemRepository;
import com.fitcheck.tryon.repository.TryonRequestRepository;
import com.fitcheck.tryon.support.TryonStepExecutor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Slf4j
@Service
@AllArgsConstructor
@EnableConfigurationProperties(TryonProperties.class)
public class TryonJobExecutor {

    private final TryonRequestRepository tryonRequestRepository;
    private final TryonRequestItemRepository tryonRequestItemRepository;
    private final TryonStepExecutor tryonStepExecutor;
    private final FashnClient fashnClient;
    private final TryonPersistenceService tryonPersistenceService;
    private final PhotoService photoService;
    private final StorageService storageService;
    private final TryonProperties properties;
    private final HttpClient httpClient;

    public void run(UUID tryonRequestId) {
        TryonRequest tryonRequest = tryonRequestRepository.findById(tryonRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Tryon request not found: " + tryonRequestId));
        List<TryonRequestItem> items = tryonRequestItemRepository.findByTryonRequestIdOrderBySequenceOrder(tryonRequestId);

        String currentImageUrl = resolveFrontPhotoUrl(tryonRequest.getUser().getId());

        for (TryonRequestItem item : items) {
            String modelImageUrl = currentImageUrl;
            Product product = item.getProduct();

            try {
                currentImageUrl = tryonStepExecutor.execute(submitterFor(modelImageUrl, product));
            } catch (ExternalServiceException e) {
                log.warn("Tryon step failed for request {} item {}: {}", tryonRequestId, item.getId(), e.getMessage());
                tryonPersistenceService.markItemFailed(item.getId());
                tryonPersistenceService.markRequestFailed(tryonRequestId, e.getMessage());
                return;
            }
            tryonPersistenceService.markItemComplete(item.getId());
        }

        byte[] resultBytes = downloadBytes(currentImageUrl);
        String storageKey = StorageKeys.tryonResultKey(tryonRequestId);
        storageService.store(storageKey, resultBytes, "image/" + properties.fashnOutputFormat());
        tryonPersistenceService.markRequestComplete(tryonRequestId, storageKey);
    }

    private Supplier<String> submitterFor(String modelImageUrl, Product product) {
        GarmentRole role = product.getGarmentRole();
        if (role == GarmentRole.FOOTWEAR || role == GarmentRole.ACCESSORY) {
            return () -> fashnClient.submitTryonMax(modelImageUrl, product.getImageUrl());
        }
        return () -> fashnClient.submitTryonV16(modelImageUrl, product.getImageUrl(), resolveCategory(role));
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

    private String resolveFrontPhotoUrl(UUID userId) {
        String storageKey = photoService.getStorageKey(userId, PhotoType.FRONT);
        return storageService.generateDownloadUrl(storageKey, StorageService.DEFAULT_TTL).toString();
    }

    private byte[] downloadBytes(String imageUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(imageUrl)).GET().build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                throw new ExternalServiceException(
                        "Failed to download FASHN try-on result, HTTP " + response.statusCode() + ": " + imageUrl);
            }
            return response.body();
        } catch (IOException e) {
            throw new ExternalServiceException(
                    "Failed to download FASHN try-on result " + imageUrl + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException("Interrupted while downloading FASHN try-on result: " + imageUrl);
        }
    }
}