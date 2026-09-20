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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.AsyncTaskExecutor;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TryonRequestServiceTest {

    private static final Set<TryonRequestStatus> IN_FLIGHT =
            Set.of(TryonRequestStatus.PENDING, TryonRequestStatus.PROCESSING);

    private static final Instant FIXED_INSTANT = Instant.parse("2026-09-04T12:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 4, 12, 0);
    private static final long STALE_THRESHOLD_MS = 1200000L;

    @Mock
    private OutfitItemQueryService outfitItemQueryService;
    @Mock
    private InMemoryRateLimiter inMemoryRateLimiter;
    @Mock
    private TryonPersistenceService tryonPersistenceService;
    @Mock
    private TryonRequestRepository tryonRequestRepository;
    @Mock
    private UserReferenceQueryService userReferenceQueryService;
    @Mock
    private PhotoService photoService;
    @Mock
    private StorageService storageService;
    @Mock
    private TryonJobExecutor tryonJobExecutor;
    @Mock
    private AsyncTaskExecutor tryonExecutor;

    private TryonProperties properties;
    private TryonRequestService service;

    private UUID userId;
    private UUID outfitId;

    @BeforeEach
    void setUp() {
        properties = new TryonProperties(20, 1, 2000, 3000, 180000, 900000, STALE_THRESHOLD_MS, 300000);
        service = new TryonRequestService(
                outfitItemQueryService, inMemoryRateLimiter, tryonPersistenceService, tryonRequestRepository,
                userReferenceQueryService, photoService, storageService, properties,
                Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC), tryonJobExecutor, tryonExecutor);

        userId = UUID.randomUUID();
        outfitId = UUID.randomUUID();
    }

    @SuppressWarnings("unchecked")
    private Future<?> stubExecutorSubmit(ArgumentCaptor<Runnable> captor) {
        Future<?> future = mock(Future.class);
        when(tryonExecutor.submit(captor.capture())).thenReturn((Future) future);
        return future;
    }

    // ---------- submit: guards before any work ----------

    @Test
    void submit_outfitHasNoValidProducts_propagatesExceptionBeforeConsumingRateLimitBudget() {
        when(outfitItemQueryService.findProductsForTryon(outfitId))
                .thenThrow(new ResourceNotFoundException("Outfit not found: " + outfitId));

        assertThatThrownBy(() -> service.submit(userId, outfitId))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(inMemoryRateLimiter);
        verifyNoInteractions(tryonPersistenceService);
        verifyNoInteractions(tryonExecutor);
    }

    @Test
    void submit_outfitHasNoTryonEligibleItems_throwsBadRequestAndNeverCreatesARequestRow() {
        when(outfitItemQueryService.findProductsForTryon(outfitId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.submit(userId, outfitId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining(outfitId.toString());

        verifyNoInteractions(tryonRequestRepository);
        verifyNoInteractions(inMemoryRateLimiter);
        verifyNoInteractions(tryonPersistenceService);
        verifyNoInteractions(tryonExecutor);
    }

    @Test
    void submit_requestAlreadyInFlightForSameOutfit_returnsItWithoutConsumingRateLimitOrQueueingAnother() {
        UUID inFlightId = UUID.randomUUID();
        Product product = Product.builder().id(UUID.randomUUID()).build();
        when(outfitItemQueryService.findProductsForTryon(outfitId)).thenReturn(List.of(product));
        TryonRequest inFlight = TryonRequest.builder()
                .id(inFlightId).status(TryonRequestStatus.PROCESSING)
                .updatedAt(NOW.minusMinutes(2))
                .build();
        when(tryonRequestRepository.findFirstByUserIdAndOutfitIdAndStatusInOrderByCreatedAtDesc(
                userId, outfitId, IN_FLIGHT)).thenReturn(Optional.of(inFlight));

        TryonStatusResponse response = service.submit(userId, outfitId);

        assertThat(response.id()).isEqualTo(inFlightId);
        assertThat(response.status()).isEqualTo(TryonRequestStatus.PROCESSING);
        verifyNoInteractions(inMemoryRateLimiter);
        verifyNoInteractions(tryonExecutor);
        verify(tryonPersistenceService, never()).createPending(any(), any(), any());
    }

    // ---------- submit: stale in-flight rows no longer wedge the outfit ----------

    @Test
    void submit_inFlightRequestOlderThanTheStaleThreshold_isIgnoredAndAFreshJobStarts() {
        Product product = Product.builder().id(UUID.randomUUID()).build();
        when(outfitItemQueryService.findProductsForTryon(outfitId)).thenReturn(List.of(product));
        TryonRequest wedged = TryonRequest.builder()
                .id(UUID.randomUUID()).status(TryonRequestStatus.PROCESSING)
                .updatedAt(NOW.minusMinutes(25))
                .build();
        when(tryonRequestRepository.findFirstByUserIdAndOutfitIdAndStatusInOrderByCreatedAtDesc(
                userId, outfitId, IN_FLIGHT)).thenReturn(Optional.of(wedged));
        when(inMemoryRateLimiter.tryConsume(any(), any(), anyInt(), any())).thenReturn(true);
        UUID newRequestId = stubHappyPathAfterRateLimit(product);

        TryonStatusResponse response = service.submit(userId, outfitId);

        assertThat(response.id()).isEqualTo(newRequestId);
        assertThat(response.status()).isEqualTo(TryonRequestStatus.PENDING);
        verify(tryonExecutor).submit(any(Runnable.class));
    }

    @Test
    void submit_inFlightRequestWithNoAuditTimestamp_isTreatedAsStaleRatherThanWedgingTheOutfitForever() {
        Product product = Product.builder().id(UUID.randomUUID()).build();
        when(outfitItemQueryService.findProductsForTryon(outfitId)).thenReturn(List.of(product));
        TryonRequest wedged = TryonRequest.builder()
                .id(UUID.randomUUID()).status(TryonRequestStatus.PENDING).build();
        when(tryonRequestRepository.findFirstByUserIdAndOutfitIdAndStatusInOrderByCreatedAtDesc(
                userId, outfitId, IN_FLIGHT)).thenReturn(Optional.of(wedged));
        when(inMemoryRateLimiter.tryConsume(any(), any(), anyInt(), any())).thenReturn(true);
        UUID newRequestId = stubHappyPathAfterRateLimit(product);

        TryonStatusResponse response = service.submit(userId, outfitId);

        assertThat(response.id()).isEqualTo(newRequestId);
        verify(tryonExecutor).submit(any(Runnable.class));
    }

    // ---------- submit: reuse of a valid COMPLETE result ----------

    @Test
    void submit_completedResultNewerThanFrontPhoto_isReusedWithAFreshPresignedUrlAndNoRateLimitCost() {
        UUID completedId = UUID.randomUUID();
        Product product = Product.builder().id(UUID.randomUUID()).build();
        when(outfitItemQueryService.findProductsForTryon(outfitId)).thenReturn(List.of(product));

        User user = User.builder().id(userId).build();
        String storageKey = "tryon-results/" + completedId + ".jpg";
        TryonRequest completed = TryonRequest.builder()
                .id(completedId).user(user).status(TryonRequestStatus.COMPLETE)
                .resultImageStorageKey(storageKey)
                .completedAt(LocalDateTime.of(2026, 9, 4, 11, 30))
                .build();
        when(tryonRequestRepository.findFirstByUserIdAndOutfitIdAndStatusOrderByCompletedAtDesc(
                userId, outfitId, TryonRequestStatus.COMPLETE)).thenReturn(Optional.of(completed));
        when(photoService.getLastModifiedAt(userId, PhotoType.FRONT))
                .thenReturn(Optional.of(LocalDateTime.of(2026, 9, 4, 11, 0)));
        when(tryonRequestRepository.findById(completedId)).thenReturn(Optional.of(completed));
        when(storageService.generateDownloadUrl(storageKey, StorageService.DEFAULT_TTL))
                .thenReturn(URI.create("https://r2.example.com/reused-presigned"));

        TryonStatusResponse response = service.submit(userId, outfitId);

        assertThat(response.id()).isEqualTo(completedId);
        assertThat(response.status()).isEqualTo(TryonRequestStatus.COMPLETE);
        assertThat(response.resultImageUrl()).isEqualTo("https://r2.example.com/reused-presigned");

        verifyNoInteractions(inMemoryRateLimiter);
        verifyNoInteractions(tryonExecutor);
        verify(tryonPersistenceService, never()).createPending(any(), any(), any());
    }

    @Test
    void submit_frontPhotoReplacedAfterTheCompletedResult_rendersFreshInsteadOfReusing() {
        UUID completedId = UUID.randomUUID();
        Product product = Product.builder().id(UUID.randomUUID()).build();
        when(outfitItemQueryService.findProductsForTryon(outfitId)).thenReturn(List.of(product));

        TryonRequest completed = TryonRequest.builder()
                .id(completedId).status(TryonRequestStatus.COMPLETE)
                .resultImageStorageKey("tryon-results/" + completedId + ".jpg")
                .completedAt(LocalDateTime.of(2026, 9, 4, 11, 0))
                .build();
        when(tryonRequestRepository.findFirstByUserIdAndOutfitIdAndStatusOrderByCompletedAtDesc(
                userId, outfitId, TryonRequestStatus.COMPLETE)).thenReturn(Optional.of(completed));
        when(photoService.getLastModifiedAt(userId, PhotoType.FRONT))
                .thenReturn(Optional.of(LocalDateTime.of(2026, 9, 4, 11, 30)));
        when(inMemoryRateLimiter.tryConsume(any(), any(), anyInt(), any())).thenReturn(true);
        UUID newRequestId = stubHappyPathAfterRateLimit(product);

        TryonStatusResponse response = service.submit(userId, outfitId);

        assertThat(response.id()).isEqualTo(newRequestId);
        assertThat(response.status()).isEqualTo(TryonRequestStatus.PENDING);
        verify(inMemoryRateLimiter).tryConsume(userId.toString(), "tryon-submit", 20, Duration.ofHours(1));
        verify(tryonExecutor).submit(any(Runnable.class));
        verify(storageService, never()).generateDownloadUrl(any(), any());
    }

    @Test
    void submit_noFrontPhotoRowExists_rendersFreshRatherThanReusingAPossiblyStaleResult() {
        UUID completedId = UUID.randomUUID();
        Product product = Product.builder().id(UUID.randomUUID()).build();
        when(outfitItemQueryService.findProductsForTryon(outfitId)).thenReturn(List.of(product));

        TryonRequest completed = TryonRequest.builder()
                .id(completedId).status(TryonRequestStatus.COMPLETE)
                .resultImageStorageKey("tryon-results/" + completedId + ".jpg")
                .completedAt(LocalDateTime.of(2026, 9, 4, 11, 30))
                .build();
        when(tryonRequestRepository.findFirstByUserIdAndOutfitIdAndStatusOrderByCompletedAtDesc(
                userId, outfitId, TryonRequestStatus.COMPLETE)).thenReturn(Optional.of(completed));
        when(photoService.getLastModifiedAt(userId, PhotoType.FRONT)).thenReturn(Optional.empty());
        when(inMemoryRateLimiter.tryConsume(any(), any(), anyInt(), any())).thenReturn(true);
        UUID newRequestId = stubHappyPathAfterRateLimit(product);

        TryonStatusResponse response = service.submit(userId, outfitId);

        assertThat(response.id()).isEqualTo(newRequestId);
        verify(tryonExecutor).submit(any(Runnable.class));
        verify(storageService, never()).generateDownloadUrl(any(), any());
    }

    // ---------- submit: fresh render path ----------

    @Test
    void submit_rateLimitExceeded_throwsRateLimitExceededExceptionAndNeverCreatesRequest() {
        Product product = Product.builder().id(UUID.randomUUID()).build();
        when(outfitItemQueryService.findProductsForTryon(outfitId)).thenReturn(List.of(product));
        when(inMemoryRateLimiter.tryConsume(userId.toString(), "tryon-submit", 20, Duration.ofHours(1)))
                .thenReturn(false);

        assertThatThrownBy(() -> service.submit(userId, outfitId))
                .isInstanceOf(RateLimitExceededException.class);

        verifyNoInteractions(tryonPersistenceService);
        verifyNoInteractions(tryonExecutor);
    }

    @Test
    void submit_rateLimitCheck_usesConfiguredLimitAndOneHourWindow() {
        Product product = Product.builder().id(UUID.randomUUID()).build();
        when(outfitItemQueryService.findProductsForTryon(outfitId)).thenReturn(List.of(product));
        when(inMemoryRateLimiter.tryConsume(any(), any(), anyInt(), any())).thenReturn(true);
        stubHappyPathAfterRateLimit(product);

        service.submit(userId, outfitId);

        verify(inMemoryRateLimiter).tryConsume(userId.toString(), "tryon-submit", 20, Duration.ofHours(1));
    }

    @Test
    void submit_success_returnsPendingStatusResponseWithNullResultAndErrorFields() {
        Product product = Product.builder().id(UUID.randomUUID()).build();
        when(outfitItemQueryService.findProductsForTryon(outfitId)).thenReturn(List.of(product));
        when(inMemoryRateLimiter.tryConsume(any(), any(), anyInt(), any())).thenReturn(true);
        UUID requestId = stubHappyPathAfterRateLimit(product);

        TryonStatusResponse response = service.submit(userId, outfitId);

        assertThat(response.id()).isEqualTo(requestId);
        assertThat(response.status()).isEqualTo(TryonRequestStatus.PENDING);
        assertThat(response.resultImageUrl()).isNull();
        assertThat(response.errorMessage()).isNull();
    }

    @Test
    void submit_success_submitsBackgroundJobToTryonExecutor() {
        Product product = Product.builder().id(UUID.randomUUID()).build();
        when(outfitItemQueryService.findProductsForTryon(outfitId)).thenReturn(List.of(product));
        when(inMemoryRateLimiter.tryConsume(any(), any(), anyInt(), any())).thenReturn(true);
        stubHappyPathAfterRateLimit(product);

        service.submit(userId, outfitId);

        verify(tryonExecutor).submit(any(Runnable.class));
    }

    @Test
    void submit_backgroundJobRunsSuccessfully_neverCallsMarkRequestFailed() {
        Product product = Product.builder().id(UUID.randomUUID()).build();
        when(outfitItemQueryService.findProductsForTryon(outfitId)).thenReturn(List.of(product));
        when(inMemoryRateLimiter.tryConsume(any(), any(), anyInt(), any())).thenReturn(true);
        UUID requestId = stubHappyPathAfterRateLimit(product);
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        stubExecutorSubmit(taskCaptor);

        service.submit(userId, outfitId);
        taskCaptor.getValue().run();

        verify(tryonJobExecutor).run(requestId);
        verify(tryonPersistenceService, never()).markRequestFailed(any(), any());
    }

    @Test
    void submit_backgroundJobThrowsRuntimeException_caughtAndMarksRequestFailedRatherThanPropagating() {
        Product product = Product.builder().id(UUID.randomUUID()).build();
        when(outfitItemQueryService.findProductsForTryon(outfitId)).thenReturn(List.of(product));
        when(inMemoryRateLimiter.tryConsume(any(), any(), anyInt(), any())).thenReturn(true);
        UUID requestId = stubHappyPathAfterRateLimit(product);
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        stubExecutorSubmit(taskCaptor);
        doThrow(new IllegalStateException("unexpected failure")).when(tryonJobExecutor).run(requestId);

        service.submit(userId, outfitId);

        assertThatCode(() -> taskCaptor.getValue().run()).doesNotThrowAnyException();
        verify(tryonPersistenceService).markRequestFailed(requestId, "unexpected failure");
    }

    // ---------- getStatus ----------

    @Test
    void getStatus_requestNotFound_throwsResourceNotFoundException() {
        UUID requestId = UUID.randomUUID();
        when(tryonRequestRepository.findById(requestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getStatus(userId, requestId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getStatus_requestBelongsToDifferentUser_throwsResourceNotFoundExceptionNotForbidden() {
        UUID requestId = UUID.randomUUID();
        User owner = User.builder().id(UUID.randomUUID()).build();
        TryonRequest request = TryonRequest.builder().id(requestId).user(owner).status(TryonRequestStatus.PENDING).build();
        when(tryonRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.getStatus(userId, requestId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(storageService, never()).generateDownloadUrl(any(), any());
    }

    @Test
    void getStatus_processingStatus_returnsNullResultImageUrlAndNullErrorMessage() {
        UUID requestId = UUID.randomUUID();
        User user = User.builder().id(userId).build();
        TryonRequest request = TryonRequest.builder()
                .id(requestId).user(user).status(TryonRequestStatus.PROCESSING).build();
        when(tryonRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        TryonStatusResponse response = service.getStatus(userId, requestId);

        assertThat(response.status()).isEqualTo(TryonRequestStatus.PROCESSING);
        assertThat(response.resultImageUrl()).isNull();
        assertThat(response.errorMessage()).isNull();
        verify(storageService, never()).generateDownloadUrl(any(), any());
    }

    @Test
    void getStatus_completeStatus_returnsFreshlyGeneratedPresignedUrl() {
        UUID requestId = UUID.randomUUID();
        User user = User.builder().id(userId).build();
        String storageKey = "tryon-results/" + requestId + ".jpg";
        TryonRequest request = TryonRequest.builder()
                .id(requestId).user(user).status(TryonRequestStatus.COMPLETE)
                .resultImageStorageKey(storageKey).build();
        when(tryonRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        URI presignedUrl = URI.create("https://r2.example.com/tryon-result-presigned");
        when(storageService.generateDownloadUrl(storageKey, StorageService.DEFAULT_TTL)).thenReturn(presignedUrl);

        TryonStatusResponse response = service.getStatus(userId, requestId);

        assertThat(response.resultImageUrl()).isEqualTo(presignedUrl.toString());
    }

    @Test
    void getStatus_completeStatus_generatesNewUrlOnEveryCallRatherThanCaching() {
        UUID requestId = UUID.randomUUID();
        User user = User.builder().id(userId).build();
        String storageKey = "tryon-results/" + requestId + ".jpg";
        TryonRequest request = TryonRequest.builder()
                .id(requestId).user(user).status(TryonRequestStatus.COMPLETE)
                .resultImageStorageKey(storageKey).build();
        when(tryonRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(storageService.generateDownloadUrl(eq(storageKey), eq(StorageService.DEFAULT_TTL)))
                .thenReturn(URI.create("https://r2.example.com/first"))
                .thenReturn(URI.create("https://r2.example.com/second"));

        TryonStatusResponse first = service.getStatus(userId, requestId);
        TryonStatusResponse second = service.getStatus(userId, requestId);

        assertThat(first.resultImageUrl()).isNotEqualTo(second.resultImageUrl());
        verify(storageService, times(2)).generateDownloadUrl(storageKey, StorageService.DEFAULT_TTL);
    }

    @Test
    void getStatus_failedStatus_returnsErrorMessageAndNullResultImageUrl() {
        UUID requestId = UUID.randomUUID();
        User user = User.builder().id(userId).build();
        TryonRequest request = TryonRequest.builder()
                .id(requestId).user(user).status(TryonRequestStatus.FAILED)
                .errorMessage("FASHN try-on step failed permanently: bad image").build();
        when(tryonRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        TryonStatusResponse response = service.getStatus(userId, requestId);

        assertThat(response.status()).isEqualTo(TryonRequestStatus.FAILED);
        assertThat(response.errorMessage()).isEqualTo("FASHN try-on step failed permanently: bad image");
        assertThat(response.resultImageUrl()).isNull();
        verify(storageService, never()).generateDownloadUrl(any(), any());
    }

    private UUID stubHappyPathAfterRateLimit(Product product) {
        UUID requestId = UUID.randomUUID();
        User user = User.builder().id(userId).build();
        Outfit outfit = Outfit.builder().id(outfitId).build();
        when(userReferenceQueryService.getReference(userId)).thenReturn(user);
        when(outfitItemQueryService.getReference(outfitId)).thenReturn(outfit);
        TryonRequest createdRequest = TryonRequest.builder()
                .id(requestId).user(user).outfit(outfit).status(TryonRequestStatus.PENDING).build();
        when(tryonPersistenceService.createPending(user, outfit, List.of(product))).thenReturn(createdRequest);
        return requestId;
    }
}