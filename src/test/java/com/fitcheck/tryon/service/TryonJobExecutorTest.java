package com.fitcheck.tryon.service;

import com.fitcheck.catalog.entity.Product;
import com.fitcheck.common.exception.ExternalServiceException;
import com.fitcheck.common.exception.ResourceNotFoundException;
import com.fitcheck.common.storage.service.StorageService;
import com.fitcheck.common.storage.util.StorageKeys;
import com.fitcheck.common.taxonomy.enums.GarmentRole;
import com.fitcheck.identity.entity.User;
import com.fitcheck.identity.enums.PhotoType;
import com.fitcheck.identity.service.PhotoService;
import com.fitcheck.tryon.entity.TryonRequest;
import com.fitcheck.tryon.entity.TryonRequestItem;
import com.fitcheck.tryon.enums.TryonRequestStatus;
import com.fitcheck.tryon.properties.TryonProperties;
import com.fitcheck.tryon.repository.TryonRequestItemRepository;
import com.fitcheck.tryon.repository.TryonRequestRepository;
import com.fitcheck.tryon.support.TryonStepExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TryonJobExecutorTest {

    @Mock
    private TryonRequestRepository tryonRequestRepository;
    @Mock
    private TryonRequestItemRepository tryonRequestItemRepository;
    @Mock
    private TryonStepExecutor tryonStepExecutor;
    @Mock
    private FashnClient fashnClient;
    @Mock
    private TryonPersistenceService tryonPersistenceService;
    @Mock
    private PhotoService photoService;
    @Mock
    private StorageService storageService;
    @Mock
    private HttpClient httpClient;

    private TryonProperties properties;
    private TryonJobExecutor jobExecutor;

    private UUID requestId;
    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        properties = new TryonProperties(20, 5, 2000, 3000, 60000,
                "tryon-v1.6", "balanced", "tryon-max", "1k", "fast", "jpeg");
        jobExecutor = new TryonJobExecutor(
                tryonRequestRepository, tryonRequestItemRepository, tryonStepExecutor, fashnClient,
                tryonPersistenceService, photoService, storageService, properties, httpClient);

        requestId = UUID.randomUUID();
        userId = UUID.randomUUID();
        user = User.builder().id(userId).build();
    }

    @Test
    void run_requestNotFound_throwsResourceNotFoundExceptionAndTouchesNothingElse() {
        when(tryonRequestRepository.findById(requestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobExecutor.run(requestId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(tryonRequestItemRepository, never()).findByTryonRequestIdOrderBySequenceOrder(any());
        verify(photoService, never()).getStorageKey(any(), any());
    }

    @Test
    void run_userHasNoFrontPhoto_propagatesResourceNotFoundException() {
        TryonRequest request = TryonRequest.builder().id(requestId).user(user).status(TryonRequestStatus.PENDING).build();
        when(tryonRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(tryonRequestItemRepository.findByTryonRequestIdOrderBySequenceOrder(requestId)).thenReturn(List.of());
        when(photoService.getStorageKey(userId, PhotoType.FRONT))
                .thenThrow(new ResourceNotFoundException("No front body photo found for user " + userId));

        assertThatThrownBy(() -> jobExecutor.run(requestId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(tryonStepExecutor, never()).execute(any());
    }

    @Test
    void run_emptyItemsList_downloadsAndStoresFrontPhotoDirectlyAsResult() throws Exception {
        TryonRequest request = TryonRequest.builder().id(requestId).user(user).status(TryonRequestStatus.PENDING).build();
        String frontPhotoUrl = stubFrontPhotoResolution(request);
        stubSuccessfulDownload();

        jobExecutor.run(requestId);

        verify(tryonStepExecutor, never()).execute(any());
        verifyDownloadedFrom(frontPhotoUrl);
        verify(storageService).store(eq(StorageKeys.tryonResultKey(requestId)), any(byte[].class), eq("image/jpeg"));
        verify(tryonPersistenceService).markRequestComplete(requestId, StorageKeys.tryonResultKey(requestId));
    }

    @Test
    void run_singleTopItem_fullChainSuccess_marksItemAndRequestComplete() throws Exception {
        TryonRequest request = TryonRequest.builder().id(requestId).user(user).status(TryonRequestStatus.PENDING).build();
        stubFrontPhotoResolution(request);
        TryonRequestItem item = itemFor(product(GarmentRole.TOP));
        when(tryonRequestItemRepository.findByTryonRequestIdOrderBySequenceOrder(requestId)).thenReturn(List.of(item));
        when(tryonStepExecutor.execute(any())).thenReturn("https://cdn.fashn.ai/step1.jpg");
        stubSuccessfulDownload();

        jobExecutor.run(requestId);

        verify(tryonPersistenceService).markItemComplete(item.getId());
        verify(tryonPersistenceService).markRequestComplete(requestId, StorageKeys.tryonResultKey(requestId));
        verify(tryonPersistenceService, never()).markItemFailed(any());
        verify(tryonPersistenceService, never()).markRequestFailed(any(), any());
    }

    @Test
    void run_topGarment_routesThroughV16WithTopsCategory() throws Exception {
        assertRoutesToV16(GarmentRole.TOP, "tops");
    }

    @Test
    void run_outerwearGarment_routesThroughV16WithTopsCategory() throws Exception {
        assertRoutesToV16(GarmentRole.OUTERWEAR, "tops");
    }

    @Test
    void run_bottomGarment_routesThroughV16WithBottomsCategory() throws Exception {
        assertRoutesToV16(GarmentRole.BOTTOM, "bottoms");
    }

    @Test
    void run_fullBodyGarment_routesThroughV16WithOnePiecesCategory() throws Exception {
        assertRoutesToV16(GarmentRole.FULL_BODY, "one-pieces");
    }

    @Test
    void run_footwearGarment_routesThroughTryonMaxNotV16() throws Exception {
        assertRoutesToMax(GarmentRole.FOOTWEAR);
    }

    @Test
    void run_accessoryGarment_routesThroughTryonMaxNotV16() throws Exception {
        assertRoutesToMax(GarmentRole.ACCESSORY);
    }

    @Test
    void run_multipleItems_chainPropagatesPreviousOutputAsNextModelImage() throws Exception {
        TryonRequest request = TryonRequest.builder().id(requestId).user(user).status(TryonRequestStatus.PENDING).build();
        String frontPhotoUrl = stubFrontPhotoResolution(request);
        Product topProduct = product(GarmentRole.TOP);
        Product bottomProduct = product(GarmentRole.BOTTOM);
        TryonRequestItem item1 = itemFor(topProduct);
        TryonRequestItem item2 = itemFor(bottomProduct);
        when(tryonRequestItemRepository.findByTryonRequestIdOrderBySequenceOrder(requestId))
                .thenReturn(List.of(item1, item2));
        when(tryonStepExecutor.execute(any()))
                .thenReturn("https://cdn.fashn.ai/step1.jpg")
                .thenReturn("https://cdn.fashn.ai/step2.jpg");
        stubSuccessfulDownload();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Supplier<String>> supplierCaptor = ArgumentCaptor.forClass(Supplier.class);
        jobExecutor.run(requestId);

        verify(tryonStepExecutor, times(2)).execute(supplierCaptor.capture());
        List<Supplier<String>> submitters = supplierCaptor.getAllValues();

        submitters.get(0).get();
        verify(fashnClient).submitTryonV16(frontPhotoUrl, topProduct.getImageUrl(), "tops");

        submitters.get(1).get();
        verify(fashnClient).submitTryonV16("https://cdn.fashn.ai/step1.jpg", bottomProduct.getImageUrl(), "bottoms");
    }

    @Test
    void run_multipleItemsAllSucceed_marksEachItemCompleteInSequenceOrder() throws Exception {
        TryonRequest request = TryonRequest.builder().id(requestId).user(user).status(TryonRequestStatus.PENDING).build();
        stubFrontPhotoResolution(request);
        TryonRequestItem item1 = itemFor(product(GarmentRole.TOP));
        TryonRequestItem item2 = itemFor(product(GarmentRole.BOTTOM));
        when(tryonRequestItemRepository.findByTryonRequestIdOrderBySequenceOrder(requestId))
                .thenReturn(List.of(item1, item2));
        when(tryonStepExecutor.execute(any()))
                .thenReturn("https://cdn.fashn.ai/step1.jpg")
                .thenReturn("https://cdn.fashn.ai/step2.jpg");
        stubSuccessfulDownload();

        jobExecutor.run(requestId);

        verify(tryonPersistenceService).markItemComplete(item1.getId());
        verify(tryonPersistenceService).markItemComplete(item2.getId());
    }

    @Test
    void run_midChainStepFails_shortCircuitsRemainingItemsAndNeverDownloadsOrStores() {
        TryonRequest request = TryonRequest.builder().id(requestId).user(user).status(TryonRequestStatus.PENDING).build();
        stubFrontPhotoResolution(request);
        TryonRequestItem item1 = itemFor(product(GarmentRole.TOP));
        TryonRequestItem item2 = itemFor(product(GarmentRole.BOTTOM));
        when(tryonRequestItemRepository.findByTryonRequestIdOrderBySequenceOrder(requestId))
                .thenReturn(List.of(item1, item2));
        when(tryonStepExecutor.execute(any()))
                .thenThrow(new ExternalServiceException("FASHN try-on step exhausted all retries: bad image"));

        jobExecutor.run(requestId);

        verify(tryonStepExecutor, times(1)).execute(any());
        verify(tryonPersistenceService).markItemFailed(item1.getId());
        verify(tryonPersistenceService, never()).markItemComplete(any());
        verify(tryonPersistenceService, never()).markItemFailed(item2.getId());
        verify(storageService, never()).store(any(), any(), any());
    }

    @Test
    void run_stepFailure_marksFailedItemThenRequestFailedWithSameErrorMessage() {
        TryonRequest request = TryonRequest.builder().id(requestId).user(user).status(TryonRequestStatus.PENDING).build();
        stubFrontPhotoResolution(request);
        TryonRequestItem item = itemFor(product(GarmentRole.TOP));
        when(tryonRequestItemRepository.findByTryonRequestIdOrderBySequenceOrder(requestId)).thenReturn(List.of(item));
        when(tryonStepExecutor.execute(any()))
                .thenThrow(new ExternalServiceException("FASHN try-on step exhausted all retries: bad image"));

        jobExecutor.run(requestId);

        verify(tryonPersistenceService).markItemFailed(item.getId());
        verify(tryonPersistenceService).markRequestFailed(
                requestId, "FASHN try-on step exhausted all retries: bad image");
    }

    @Test
    void run_downloadResultNon200Status_throwsExternalServiceException() throws Exception {
        TryonRequest request = TryonRequest.builder().id(requestId).user(user).status(TryonRequestStatus.PENDING).build();
        stubFrontPhotoResolution(request);
        TryonRequestItem item = itemFor(product(GarmentRole.TOP));
        when(tryonRequestItemRepository.findByTryonRequestIdOrderBySequenceOrder(requestId)).thenReturn(List.of(item));
        when(tryonStepExecutor.execute(any())).thenReturn("https://cdn.fashn.ai/step1.jpg");
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(404);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        assertThatThrownBy(() -> jobExecutor.run(requestId))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("404");

        verify(tryonPersistenceService, never()).markRequestComplete(any(), any());
    }

    @Test
    void run_downloadResultIOException_throwsExternalServiceExceptionWrappingCause() throws Exception {
        TryonRequest request = TryonRequest.builder().id(requestId).user(user).status(TryonRequestStatus.PENDING).build();
        stubFrontPhotoResolution(request);
        TryonRequestItem item = itemFor(product(GarmentRole.TOP));
        when(tryonRequestItemRepository.findByTryonRequestIdOrderBySequenceOrder(requestId)).thenReturn(List.of(item));
        when(tryonStepExecutor.execute(any())).thenReturn("https://cdn.fashn.ai/step1.jpg");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("connection reset"));

        assertThatThrownBy(() -> jobExecutor.run(requestId))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("connection reset");
    }

    @Test
    void run_downloadResultInterruptedException_throwsExternalServiceExceptionAndRestoresInterruptFlag() throws Exception {
        TryonRequest request = TryonRequest.builder().id(requestId).user(user).status(TryonRequestStatus.PENDING).build();
        stubFrontPhotoResolution(request);
        TryonRequestItem item = itemFor(product(GarmentRole.TOP));
        when(tryonRequestItemRepository.findByTryonRequestIdOrderBySequenceOrder(requestId)).thenReturn(List.of(item));
        when(tryonStepExecutor.execute(any())).thenReturn("https://cdn.fashn.ai/step1.jpg");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException());

        assertThatThrownBy(() -> jobExecutor.run(requestId))
                .isInstanceOf(ExternalServiceException.class);

        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void run_contentTypeDerivedFromConfiguredOutputFormat_notHardcodedJpeg() throws Exception {
        TryonProperties pngProperties = new TryonProperties(20, 5, 2000, 3000, 60000,
                "tryon-v1.6", "balanced", "tryon-max", "1k", "fast", "png");
        TryonJobExecutor pngJobExecutor = new TryonJobExecutor(
                tryonRequestRepository, tryonRequestItemRepository, tryonStepExecutor, fashnClient,
                tryonPersistenceService, photoService, storageService, pngProperties, httpClient);
        TryonRequest request = TryonRequest.builder().id(requestId).user(user).status(TryonRequestStatus.PENDING).build();
        stubFrontPhotoResolution(request);
        when(tryonRequestItemRepository.findByTryonRequestIdOrderBySequenceOrder(requestId)).thenReturn(List.of());
        stubSuccessfulDownload();

        pngJobExecutor.run(requestId);

        verify(storageService).store(any(), any(byte[].class), eq("image/png"));
    }

    @Test
    void run_resultStorageKeyDerivedFromStorageKeysUtil() throws Exception {
        TryonRequest request = TryonRequest.builder().id(requestId).user(user).status(TryonRequestStatus.PENDING).build();
        stubFrontPhotoResolution(request);
        when(tryonRequestItemRepository.findByTryonRequestIdOrderBySequenceOrder(requestId)).thenReturn(List.of());
        stubSuccessfulDownload();

        jobExecutor.run(requestId);

        String expectedKey = StorageKeys.tryonResultKey(requestId);
        verify(storageService).store(eq(expectedKey), any(byte[].class), anyString());
        verify(tryonPersistenceService).markRequestComplete(requestId, expectedKey);
    }

    private void assertRoutesToV16(GarmentRole role, String expectedCategory) throws Exception {
        TryonRequest request = TryonRequest.builder().id(requestId).user(user).status(TryonRequestStatus.PENDING).build();
        String frontPhotoUrl = stubFrontPhotoResolution(request);
        Product product = product(role);
        TryonRequestItem item = itemFor(product);
        when(tryonRequestItemRepository.findByTryonRequestIdOrderBySequenceOrder(requestId)).thenReturn(List.of(item));
        when(tryonStepExecutor.execute(any())).thenReturn("https://cdn.fashn.ai/step1.jpg");
        stubSuccessfulDownload();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Supplier<String>> supplierCaptor = ArgumentCaptor.forClass(Supplier.class);
        jobExecutor.run(requestId);

        verify(tryonStepExecutor).execute(supplierCaptor.capture());
        supplierCaptor.getValue().get();

        verify(fashnClient).submitTryonV16(frontPhotoUrl, product.getImageUrl(), expectedCategory);
        verify(fashnClient, never()).submitTryonMax(any(), any());
    }

    private void assertRoutesToMax(GarmentRole role) throws Exception {
        TryonRequest request = TryonRequest.builder().id(requestId).user(user).status(TryonRequestStatus.PENDING).build();
        String frontPhotoUrl = stubFrontPhotoResolution(request);
        Product product = product(role);
        TryonRequestItem item = itemFor(product);
        when(tryonRequestItemRepository.findByTryonRequestIdOrderBySequenceOrder(requestId)).thenReturn(List.of(item));
        when(tryonStepExecutor.execute(any())).thenReturn("https://cdn.fashn.ai/step1.jpg");
        stubSuccessfulDownload();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Supplier<String>> supplierCaptor = ArgumentCaptor.forClass(Supplier.class);
        jobExecutor.run(requestId);

        verify(tryonStepExecutor).execute(supplierCaptor.capture());
        supplierCaptor.getValue().get();

        verify(fashnClient).submitTryonMax(frontPhotoUrl, product.getImageUrl());
        verify(fashnClient, never()).submitTryonV16(any(), any(), any());
    }

    private String stubFrontPhotoResolution(TryonRequest request) {
        when(tryonRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        String storageKey = "body-photos/" + userId + "/front.jpg";
        String frontPhotoUrl = "https://r2.example.com/front-presigned";
        when(photoService.getStorageKey(userId, PhotoType.FRONT)).thenReturn(storageKey);
        when(storageService.generateDownloadUrl(storageKey, StorageService.DEFAULT_TTL))
                .thenReturn(URI.create(frontPhotoUrl));
        return frontPhotoUrl;
    }

    private void stubSuccessfulDownload() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("image-bytes".getBytes());
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
    }

    private void verifyDownloadedFrom(String expectedUrl) throws Exception {
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(requestCaptor.getValue().uri()).isEqualTo(URI.create(expectedUrl));
    }

    private Product product(GarmentRole role) {
        return Product.builder()
                .id(UUID.randomUUID())
                .imageUrl("https://cdn.test/" + role.name().toLowerCase() + ".jpg")
                .garmentRole(role)
                .build();
    }

    private TryonRequestItem itemFor(Product product) {
        return TryonRequestItem.builder().id(UUID.randomUUID()).product(product).build();
    }
}