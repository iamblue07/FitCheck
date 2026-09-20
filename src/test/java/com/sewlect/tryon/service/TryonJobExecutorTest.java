package com.sewlect.tryon.service;

import com.sewlect.catalog.entity.Product;
import com.sewlect.common.exception.ExternalServiceException;
import com.sewlect.common.exception.ResourceNotFoundException;
import com.sewlect.common.storage.service.StorageService;
import com.sewlect.common.storage.util.StorageKeys;
import com.sewlect.common.taxonomy.enums.GarmentRole;
import com.sewlect.identity.entity.User;
import com.sewlect.identity.enums.PhotoType;
import com.sewlect.identity.service.PhotoService;
import com.sewlect.tryon.entity.TryonRequest;
import com.sewlect.tryon.entity.TryonRequestItem;
import com.sewlect.tryon.enums.TryonRequestStatus;
import com.sewlect.tryon.properties.FashnModelProperties;
import com.sewlect.tryon.properties.TryonProperties;
import com.sewlect.tryon.repository.TryonRequestItemRepository;
import com.sewlect.tryon.repository.TryonRequestRepository;
import com.sewlect.tryon.support.TryonStepExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TryonJobExecutorTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-09-04T12:00:00Z");
    private static final long JOB_TIMEOUT_MS = 900000L;

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
    private FashnModelProperties modelProperties;
    private TryonJobExecutor jobExecutor;

    private UUID requestId;
    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        properties = tryonPropertiesWith(JOB_TIMEOUT_MS);
        modelProperties = modelPropertiesWith("jpeg");
        jobExecutor = jobExecutorWith(properties, modelProperties, Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC));

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
    void run_marksRequestProcessingBeforeReadingTheRequestRow() throws Exception {
        TryonRequest request = pendingRequest();
        stubFrontPhotoResolution(request);
        TryonRequestItem item = itemFor(product(GarmentRole.TOP));
        when(tryonRequestItemRepository.findByTryonRequestIdOrderBySequenceOrder(requestId)).thenReturn(List.of(item));
        when(tryonStepExecutor.execute(any(), any())).thenReturn("https://cdn.fashn.ai/step1.jpg");
        stubSuccessfulDownload();

        jobExecutor.run(requestId);

        InOrder ordered = inOrder(tryonPersistenceService, tryonRequestRepository, tryonStepExecutor);
        ordered.verify(tryonPersistenceService).markRequestProcessing(requestId);
        ordered.verify(tryonRequestRepository).findById(requestId);
        ordered.verify(tryonStepExecutor).execute(any(), any());
    }

    @Test
    void run_userHasNoFrontPhoto_propagatesResourceNotFoundException() {
        TryonRequest request = pendingRequest();
        when(tryonRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(tryonRequestItemRepository.findByTryonRequestIdOrderBySequenceOrder(requestId)).thenReturn(List.of());
        when(photoService.getStorageKey(userId, PhotoType.FRONT))
                .thenThrow(new ResourceNotFoundException("No front body photo found for user " + userId));

        assertThatThrownBy(() -> jobExecutor.run(requestId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(tryonStepExecutor, never()).execute(any(), any());
    }

    @Test
    void run_singleTopItem_fullChainSuccess_marksItemAndRequestComplete() throws Exception {
        TryonRequest request = pendingRequest();
        stubFrontPhotoResolution(request);
        TryonRequestItem item = itemFor(product(GarmentRole.TOP));
        when(tryonRequestItemRepository.findByTryonRequestIdOrderBySequenceOrder(requestId)).thenReturn(List.of(item));
        when(tryonStepExecutor.execute(any(), any())).thenReturn("https://cdn.fashn.ai/step1.jpg");
        stubSuccessfulDownload();

        jobExecutor.run(requestId);

        verify(tryonPersistenceService).markItemComplete(item.getId());
        verify(tryonPersistenceService).markRequestComplete(requestId, StorageKeys.tryonResultKey(requestId));
        verify(tryonPersistenceService, never()).markItemFailed(any());
        verify(tryonPersistenceService, never()).markRequestFailed(any(), any());
    }

    @Test
    void run_storesTheLastStepOutputNotTheUntouchedFrontPhoto() throws Exception {
        TryonRequest request = pendingRequest();
        stubFrontPhotoResolution(request);
        TryonRequestItem item = itemFor(product(GarmentRole.TOP));
        when(tryonRequestItemRepository.findByTryonRequestIdOrderBySequenceOrder(requestId)).thenReturn(List.of(item));
        when(tryonStepExecutor.execute(any(), any())).thenReturn("https://cdn.fashn.ai/step1.jpg");
        stubSuccessfulDownload();

        jobExecutor.run(requestId);

        verifyDownloadedFrom("https://cdn.fashn.ai/step1.jpg");
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
    void run_firstItemSubmitter_reresolvesThePresignedFrontPhotoUrlOnEveryInvocation() throws Exception {
        TryonRequest request = pendingRequest();
        String frontPhotoUrl = stubFrontPhotoResolution(request);
        Product topProduct = product(GarmentRole.TOP);
        TryonRequestItem item = itemFor(topProduct);
        when(tryonRequestItemRepository.findByTryonRequestIdOrderBySequenceOrder(requestId)).thenReturn(List.of(item));
        when(tryonStepExecutor.execute(any(), any())).thenReturn("https://cdn.fashn.ai/step1.jpg");
        stubSuccessfulDownload();

        jobExecutor.run(requestId);

        Supplier<String> submitter = captureSubmitters().get(0);
        submitter.get();
        submitter.get();

        verify(storageService, times(2))
                .generateDownloadUrl(frontPhotoStorageKey(), StorageService.DEFAULT_TTL);
        verify(fashnClient, times(2)).submitTryonV16(frontPhotoUrl, topProduct.getImageUrl(), "tops");
    }

    @Test
    void run_multipleItems_chainPropagatesPreviousOutputAsNextModelImage() throws Exception {
        TryonRequest request = pendingRequest();
        String frontPhotoUrl = stubFrontPhotoResolution(request);
        Product topProduct = product(GarmentRole.TOP);
        Product bottomProduct = product(GarmentRole.BOTTOM);
        TryonRequestItem item1 = itemFor(topProduct);
        TryonRequestItem item2 = itemFor(bottomProduct);
        when(tryonRequestItemRepository.findByTryonRequestIdOrderBySequenceOrder(requestId))
                .thenReturn(List.of(item1, item2));
        when(tryonStepExecutor.execute(any(), any()))
                .thenReturn("https://cdn.fashn.ai/step1.jpg")
                .thenReturn("https://cdn.fashn.ai/step2.jpg");
        stubSuccessfulDownload();

        jobExecutor.run(requestId);

        List<Supplier<String>> submitters = captureSubmitters();
        assertThat(submitters).hasSize(2);

        submitters.get(0).get();
        verify(fashnClient).submitTryonV16(frontPhotoUrl, topProduct.getImageUrl(), "tops");

        submitters.get(1).get();
        verify(fashnClient).submitTryonV16("https://cdn.fashn.ai/step1.jpg", bottomProduct.getImageUrl(), "bottoms");
    }

    @Test
    void run_multipleItemsAllSucceed_marksEachItemCompleteInSequenceOrder() throws Exception {
        TryonRequest request = pendingRequest();
        stubFrontPhotoResolution(request);
        TryonRequestItem item1 = itemFor(product(GarmentRole.TOP));
        TryonRequestItem item2 = itemFor(product(GarmentRole.BOTTOM));
        when(tryonRequestItemRepository.findByTryonRequestIdOrderBySequenceOrder(requestId))
                .thenReturn(List.of(item1, item2));
        when(tryonStepExecutor.execute(any(), any()))
                .thenReturn("https://cdn.fashn.ai/step1.jpg")
                .thenReturn("https://cdn.fashn.ai/step2.jpg");
        stubSuccessfulDownload();

        jobExecutor.run(requestId);

        InOrder ordered = inOrder(tryonPersistenceService);
        ordered.verify(tryonPersistenceService).markItemComplete(item1.getId());
        ordered.verify(tryonPersistenceService).markItemComplete(item2.getId());
    }

    @Test
    void run_everyStepReceivesTheSameJobDeadlineDerivedFromClockAndJobTimeout() throws Exception {
        TryonRequest request = pendingRequest();
        stubFrontPhotoResolution(request);
        TryonRequestItem item1 = itemFor(product(GarmentRole.TOP));
        TryonRequestItem item2 = itemFor(product(GarmentRole.BOTTOM));
        when(tryonRequestItemRepository.findByTryonRequestIdOrderBySequenceOrder(requestId))
                .thenReturn(List.of(item1, item2));
        when(tryonStepExecutor.execute(any(), any()))
                .thenReturn("https://cdn.fashn.ai/step1.jpg")
                .thenReturn("https://cdn.fashn.ai/step2.jpg");
        stubSuccessfulDownload();

        jobExecutor.run(requestId);

        ArgumentCaptor<Instant> deadlineCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(tryonStepExecutor, times(2)).execute(any(), deadlineCaptor.capture());

        Instant expectedDeadline = FIXED_INSTANT.plus(Duration.ofMillis(JOB_TIMEOUT_MS));
        assertThat(deadlineCaptor.getAllValues()).containsExactly(expectedDeadline, expectedDeadline);
    }

    @Test
    void run_jobBudgetExhaustedBeforeFirstItem_failsWithBudgetMessageAndNeverCallsTheStepExecutor() {
        TryonJobExecutor budgetExecutor = jobExecutorWith(
                tryonPropertiesWith(1L), modelProperties, new AdvancingClock(FIXED_INSTANT, Duration.ofSeconds(10)));
        TryonRequest request = pendingRequest();
        stubFrontPhotoResolution(request);
        TryonRequestItem item = itemFor(product(GarmentRole.TOP));
        when(tryonRequestItemRepository.findByTryonRequestIdOrderBySequenceOrder(requestId)).thenReturn(List.of(item));

        budgetExecutor.run(requestId);

        verify(tryonStepExecutor, never()).execute(any(), any());
        verify(tryonPersistenceService).markItemFailed(item.getId());
        verify(tryonPersistenceService).markRequestFailed(eq(requestId), contains("budget"));
        verify(storageService, never()).store(any(), any(), any());
    }

    @Test
    void run_jobBudgetExhaustedBetweenItems_stopsBeforeTheSecondItem() throws Exception {
        TryonJobExecutor budgetExecutor = jobExecutorWith(
                tryonPropertiesWith(15000L), modelProperties,
                new AdvancingClock(FIXED_INSTANT, Duration.ofSeconds(10)));
        TryonRequest request = pendingRequest();
        stubFrontPhotoResolution(request);
        TryonRequestItem item1 = itemFor(product(GarmentRole.TOP));
        TryonRequestItem item2 = itemFor(product(GarmentRole.BOTTOM));
        when(tryonRequestItemRepository.findByTryonRequestIdOrderBySequenceOrder(requestId))
                .thenReturn(List.of(item1, item2));
        when(tryonStepExecutor.execute(any(), any())).thenReturn("https://cdn.fashn.ai/step1.jpg");

        budgetExecutor.run(requestId);

        verify(tryonStepExecutor, times(1)).execute(any(), any());
        verify(tryonPersistenceService).markItemComplete(item1.getId());
        verify(tryonPersistenceService).markItemFailed(item2.getId());
        verify(tryonPersistenceService).markRequestFailed(eq(requestId), contains("budget"));
        verify(tryonPersistenceService, never()).markRequestComplete(any(), any());
    }

    @Test
    void run_midChainStepFails_shortCircuitsRemainingItemsAndNeverDownloadsOrStores() {
        TryonRequest request = pendingRequest();
        stubFrontPhotoResolution(request);
        TryonRequestItem item1 = itemFor(product(GarmentRole.TOP));
        TryonRequestItem item2 = itemFor(product(GarmentRole.BOTTOM));
        when(tryonRequestItemRepository.findByTryonRequestIdOrderBySequenceOrder(requestId))
                .thenReturn(List.of(item1, item2));
        when(tryonStepExecutor.execute(any(), any()))
                .thenThrow(new ExternalServiceException("FASHN try-on step failed permanently: bad image"));

        jobExecutor.run(requestId);

        verify(tryonStepExecutor, times(1)).execute(any(), any());
        verify(tryonPersistenceService).markItemFailed(item1.getId());
        verify(tryonPersistenceService, never()).markItemComplete(any());
        verify(tryonPersistenceService, never()).markItemFailed(item2.getId());
        verify(storageService, never()).store(any(), any(), any());
    }

    @Test
    void run_stepFailure_marksFailedItemThenRequestFailedWithSameErrorMessage() {
        TryonRequest request = pendingRequest();
        stubFrontPhotoResolution(request);
        TryonRequestItem item = itemFor(product(GarmentRole.TOP));
        when(tryonRequestItemRepository.findByTryonRequestIdOrderBySequenceOrder(requestId)).thenReturn(List.of(item));
        when(tryonStepExecutor.execute(any(), any()))
                .thenThrow(new ExternalServiceException("FASHN try-on step failed permanently: bad image"));

        jobExecutor.run(requestId);

        InOrder ordered = inOrder(tryonPersistenceService);
        ordered.verify(tryonPersistenceService).markItemFailed(item.getId());
        ordered.verify(tryonPersistenceService).markRequestFailed(
                requestId, "FASHN try-on step failed permanently: bad image");
    }

    @Test
    void run_downloadResultNon200Status_throwsExternalServiceException() throws Exception {
        TryonRequest request = pendingRequest();
        stubFrontPhotoResolution(request);
        TryonRequestItem item = itemFor(product(GarmentRole.TOP));
        when(tryonRequestItemRepository.findByTryonRequestIdOrderBySequenceOrder(requestId)).thenReturn(List.of(item));
        when(tryonStepExecutor.execute(any(), any())).thenReturn("https://cdn.fashn.ai/step1.jpg");
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
        TryonRequest request = pendingRequest();
        stubFrontPhotoResolution(request);
        TryonRequestItem item = itemFor(product(GarmentRole.TOP));
        when(tryonRequestItemRepository.findByTryonRequestIdOrderBySequenceOrder(requestId)).thenReturn(List.of(item));
        when(tryonStepExecutor.execute(any(), any())).thenReturn("https://cdn.fashn.ai/step1.jpg");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("connection reset"));

        assertThatThrownBy(() -> jobExecutor.run(requestId))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("connection reset");
    }

    @Test
    void run_downloadResultInterruptedException_throwsExternalServiceExceptionAndRestoresInterruptFlag() throws Exception {
        TryonRequest request = pendingRequest();
        stubFrontPhotoResolution(request);
        TryonRequestItem item = itemFor(product(GarmentRole.TOP));
        when(tryonRequestItemRepository.findByTryonRequestIdOrderBySequenceOrder(requestId)).thenReturn(List.of(item));
        when(tryonStepExecutor.execute(any(), any())).thenReturn("https://cdn.fashn.ai/step1.jpg");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException());

        assertThatThrownBy(() -> jobExecutor.run(requestId))
                .isInstanceOf(ExternalServiceException.class);

        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void run_contentTypeDerivedFromConfiguredOutputFormat_notHardcodedJpeg() throws Exception {
        TryonJobExecutor pngJobExecutor = jobExecutorWith(
                properties, modelPropertiesWith("png"), Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC));
        TryonRequest request = pendingRequest();
        stubFrontPhotoResolution(request);
        TryonRequestItem item = itemFor(product(GarmentRole.TOP));
        when(tryonRequestItemRepository.findByTryonRequestIdOrderBySequenceOrder(requestId)).thenReturn(List.of(item));
        when(tryonStepExecutor.execute(any(), any())).thenReturn("https://cdn.fashn.ai/step1.jpg");
        stubSuccessfulDownload();

        pngJobExecutor.run(requestId);

        verify(storageService).store(any(), any(byte[].class), eq("image/png"));
    }

    @Test
    void run_resultStorageKeyDerivedFromStorageKeysUtil() throws Exception {
        TryonRequest request = pendingRequest();
        stubFrontPhotoResolution(request);
        TryonRequestItem item = itemFor(product(GarmentRole.TOP));
        when(tryonRequestItemRepository.findByTryonRequestIdOrderBySequenceOrder(requestId)).thenReturn(List.of(item));
        when(tryonStepExecutor.execute(any(), any())).thenReturn("https://cdn.fashn.ai/step1.jpg");
        stubSuccessfulDownload();

        jobExecutor.run(requestId);

        String expectedKey = StorageKeys.tryonResultKey(requestId);
        verify(storageService).store(eq(expectedKey), any(byte[].class), anyString());
        verify(tryonPersistenceService).markRequestComplete(requestId, expectedKey);
    }

    // ---------- shared assertions ----------

    private void assertRoutesToV16(GarmentRole role, String expectedCategory) throws Exception {
        TryonRequest request = pendingRequest();
        String frontPhotoUrl = stubFrontPhotoResolution(request);
        Product product = product(role);
        TryonRequestItem item = itemFor(product);
        when(tryonRequestItemRepository.findByTryonRequestIdOrderBySequenceOrder(requestId)).thenReturn(List.of(item));
        when(tryonStepExecutor.execute(any(), any())).thenReturn("https://cdn.fashn.ai/step1.jpg");
        stubSuccessfulDownload();

        jobExecutor.run(requestId);

        captureSubmitters().get(0).get();

        verify(fashnClient).submitTryonV16(frontPhotoUrl, product.getImageUrl(), expectedCategory);
        verify(fashnClient, never()).submitTryonMax(any(), any());
    }

    private void assertRoutesToMax(GarmentRole role) throws Exception {
        TryonRequest request = pendingRequest();
        String frontPhotoUrl = stubFrontPhotoResolution(request);
        Product product = product(role);
        TryonRequestItem item = itemFor(product);
        when(tryonRequestItemRepository.findByTryonRequestIdOrderBySequenceOrder(requestId)).thenReturn(List.of(item));
        when(tryonStepExecutor.execute(any(), any())).thenReturn("https://cdn.fashn.ai/step1.jpg");
        stubSuccessfulDownload();

        jobExecutor.run(requestId);

        captureSubmitters().get(0).get();

        verify(fashnClient).submitTryonMax(frontPhotoUrl, product.getImageUrl());
        verify(fashnClient, never()).submitTryonV16(any(), any(), any());
    }

    // ---------- fixtures ----------

    private TryonJobExecutor jobExecutorWith(TryonProperties tryonProperties,
                                             FashnModelProperties fashnModelProperties, Clock clock) {
        return new TryonJobExecutor(
                tryonRequestRepository, tryonRequestItemRepository, tryonStepExecutor, fashnClient,
                tryonPersistenceService, photoService, storageService, tryonProperties, fashnModelProperties,
                httpClient, clock);
    }

    private TryonProperties tryonPropertiesWith(long jobTimeoutMs) {
        return new TryonProperties(20, 1, 2000, 3000, 180000, jobTimeoutMs, 1200000, 300000);
    }

    private FashnModelProperties modelPropertiesWith(String outputFormat) {
        return new FashnModelProperties("tryon-v1.6", "balanced", "tryon-max", "1k", "fast", outputFormat);
    }

    private TryonRequest pendingRequest() {
        return TryonRequest.builder().id(requestId).user(user).status(TryonRequestStatus.PENDING).build();
    }

    private String frontPhotoStorageKey() {
        return "body-photos/" + userId + "/front.jpg";
    }

    private String stubFrontPhotoResolution(TryonRequest request) {
        when(tryonRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        String frontPhotoUrl = "https://r2.example.com/front-presigned";
        when(photoService.getStorageKey(userId, PhotoType.FRONT)).thenReturn(frontPhotoStorageKey());
        lenient().when(storageService.generateDownloadUrl(frontPhotoStorageKey(), StorageService.DEFAULT_TTL))
                .thenReturn(URI.create(frontPhotoUrl));
        return frontPhotoUrl;
    }

    @SuppressWarnings("unchecked")
    private List<Supplier<String>> captureSubmitters() {
        ArgumentCaptor<Supplier<String>> supplierCaptor = ArgumentCaptor.forClass(Supplier.class);
        verify(tryonStepExecutor, org.mockito.Mockito.atLeastOnce()).execute(supplierCaptor.capture(), any());
        return supplierCaptor.getAllValues();
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

    private static final class AdvancingClock extends Clock {

        private final Duration step;
        private Instant instant;

        private AdvancingClock(Instant start, Duration step) {
            this.instant = start;
            this.step = step;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            Instant current = instant;
            instant = instant.plus(step);
            return current;
        }
    }
}