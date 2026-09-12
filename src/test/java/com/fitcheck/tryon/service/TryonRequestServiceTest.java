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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.AsyncTaskExecutor;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
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
        properties = new TryonProperties(20, 5, 2000, 3000, 60000,
                "tryon-v1.6", "balanced", "tryon-max", "1k", "fast", "jpeg");
        service = new TryonRequestService(
                outfitItemQueryService, inMemoryRateLimiter, tryonPersistenceService, tryonRequestRepository,
                userReferenceQueryService, storageService, properties, tryonJobExecutor, tryonExecutor);

        userId = UUID.randomUUID();
        outfitId = UUID.randomUUID();
    }

    @SuppressWarnings("unchecked")
    private Future<?> stubExecutorSubmit(ArgumentCaptor<Runnable> captor) {
        Future<?> future = mock(Future.class);
        when(tryonExecutor.submit(captor.capture())).thenReturn((Future) future);
        return future;
    }

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
    void submit_rateLimitExceeded_throwsRateLimitExceededExceptionAndNeverCreatesRequest() {
        Product product = Product.builder().id(UUID.randomUUID()).build();
        when(outfitItemQueryService.findProductsForTryon(outfitId)).thenReturn(List.of(product));
        when(inMemoryRateLimiter.tryConsume(userId, "tryon-submit", 20, Duration.ofHours(1))).thenReturn(false);

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

        verify(inMemoryRateLimiter).tryConsume(userId, "tryon-submit", 20, Duration.ofHours(1));
    }

    @Test
    void submit_success_returnsPendingStatusResponseWithNullResultAndErrorFields() {
        UUID requestId = UUID.randomUUID();
        Product product = Product.builder().id(UUID.randomUUID()).build();
        when(outfitItemQueryService.findProductsForTryon(outfitId)).thenReturn(List.of(product));
        when(inMemoryRateLimiter.tryConsume(any(), any(), anyInt(), any())).thenReturn(true);
        User user = User.builder().id(userId).build();
        Outfit outfit = Outfit.builder().id(outfitId).build();
        when(userReferenceQueryService.getReference(userId)).thenReturn(user);
        when(outfitItemQueryService.getReference(outfitId)).thenReturn(outfit);
        TryonRequest createdRequest = TryonRequest.builder()
                .id(requestId).user(user).outfit(outfit).status(TryonRequestStatus.PENDING).build();
        when(tryonPersistenceService.createPending(user, outfit, List.of(product))).thenReturn(createdRequest);

        TryonStatusResponse response = service.submit(userId, outfitId);

        assertThat(response.id()).isEqualTo(requestId);
        assertThat(response.status()).isEqualTo(TryonRequestStatus.PENDING);
        assertThat(response.resultImageUrl()).isNull();
        assertThat(response.errorMessage()).isNull();
    }

    @Test
    void submit_success_submitsBackgroundJobToTryonExecutor() {
        Product product = Product.builder().id(UUID.randomUUID()).build();
        stubHappyPathAfterRateLimit(product);
        when(outfitItemQueryService.findProductsForTryon(outfitId)).thenReturn(List.of(product));
        when(inMemoryRateLimiter.tryConsume(any(), any(), anyInt(), any())).thenReturn(true);

        service.submit(userId, outfitId);

        verify(tryonExecutor).submit(any(Runnable.class));
    }

    @Test
    void submit_backgroundJobRunsSuccessfully_neverCallsMarkRequestFailed() {
        UUID requestId = UUID.randomUUID();
        Product product = Product.builder().id(UUID.randomUUID()).build();
        when(outfitItemQueryService.findProductsForTryon(outfitId)).thenReturn(List.of(product));
        when(inMemoryRateLimiter.tryConsume(any(), any(), anyInt(), any())).thenReturn(true);
        User user = User.builder().id(userId).build();
        Outfit outfit = Outfit.builder().id(outfitId).build();
        when(userReferenceQueryService.getReference(userId)).thenReturn(user);
        when(outfitItemQueryService.getReference(outfitId)).thenReturn(outfit);
        TryonRequest createdRequest = TryonRequest.builder()
                .id(requestId).user(user).outfit(outfit).status(TryonRequestStatus.PENDING).build();
        when(tryonPersistenceService.createPending(user, outfit, List.of(product))).thenReturn(createdRequest);
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        stubExecutorSubmit(taskCaptor);

        service.submit(userId, outfitId);
        taskCaptor.getValue().run();

        verify(tryonJobExecutor).run(requestId);
        verify(tryonPersistenceService, never()).markRequestFailed(any(), any());
    }

    @Test
    void submit_backgroundJobThrowsRuntimeException_caughtAndMarksRequestFailedRatherThanPropagating() {
        UUID requestId = UUID.randomUUID();
        Product product = Product.builder().id(UUID.randomUUID()).build();
        when(outfitItemQueryService.findProductsForTryon(outfitId)).thenReturn(List.of(product));
        when(inMemoryRateLimiter.tryConsume(any(), any(), anyInt(), any())).thenReturn(true);
        User user = User.builder().id(userId).build();
        Outfit outfit = Outfit.builder().id(outfitId).build();
        when(userReferenceQueryService.getReference(userId)).thenReturn(user);
        when(outfitItemQueryService.getReference(outfitId)).thenReturn(outfit);
        TryonRequest createdRequest = TryonRequest.builder()
                .id(requestId).user(user).outfit(outfit).status(TryonRequestStatus.PENDING).build();
        when(tryonPersistenceService.createPending(user, outfit, List.of(product))).thenReturn(createdRequest);
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        stubExecutorSubmit(taskCaptor);
        doThrow(new IllegalStateException("unexpected failure"))
                .when(tryonJobExecutor).run(requestId);

        service.submit(userId, outfitId);

        assertThatCode(() -> taskCaptor.getValue().run()).doesNotThrowAnyException();
        verify(tryonPersistenceService).markRequestFailed(requestId, "unexpected failure");
    }

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
    void getStatus_pendingStatus_returnsNullResultImageUrlAndNullErrorMessage() {
        UUID requestId = UUID.randomUUID();
        User user = User.builder().id(userId).build();
        TryonRequest request = TryonRequest.builder().id(requestId).user(user).status(TryonRequestStatus.PENDING).build();
        when(tryonRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        TryonStatusResponse response = service.getStatus(userId, requestId);

        assertThat(response.status()).isEqualTo(TryonRequestStatus.PENDING);
        assertThat(response.resultImageUrl()).isNull();
        assertThat(response.errorMessage()).isNull();
        verify(storageService, never()).generateDownloadUrl(any(), any());
    }

    @Test
    void getStatus_completeStatus_returnsFreshlyGeneratedPresignedUrl() {
        UUID requestId = UUID.randomUUID();
        User user = User.builder().id(userId).build();
        TryonRequest request = TryonRequest.builder()
                .id(requestId).user(user).status(TryonRequestStatus.COMPLETE)
                .resultImageStorageKey("tryon-results/" + requestId + ".jpg").build();
        when(tryonRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        URI presignedUrl = URI.create("https://r2.example.com/tryon-result-presigned");
        when(storageService.generateDownloadUrl("tryon-results/" + requestId + ".jpg", StorageService.DEFAULT_TTL))
                .thenReturn(presignedUrl);

        TryonStatusResponse response = service.getStatus(userId, requestId);

        assertThat(response.resultImageUrl()).isEqualTo(presignedUrl.toString());
    }

    @Test
    void getStatus_completeStatus_generatesNewUrlOnEveryCallRatherThanCaching() {
        UUID requestId = UUID.randomUUID();
        User user = User.builder().id(userId).build();
        TryonRequest request = TryonRequest.builder()
                .id(requestId).user(user).status(TryonRequestStatus.COMPLETE)
                .resultImageStorageKey("tryon-results/" + requestId + ".jpg").build();
        when(tryonRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(storageService.generateDownloadUrl(eq("tryon-results/" + requestId + ".jpg"), eq(StorageService.DEFAULT_TTL)))
                .thenReturn(URI.create("https://r2.example.com/first"))
                .thenReturn(URI.create("https://r2.example.com/second"));

        TryonStatusResponse first = service.getStatus(userId, requestId);
        TryonStatusResponse second = service.getStatus(userId, requestId);

        assertThat(first.resultImageUrl()).isNotEqualTo(second.resultImageUrl());
        verify(storageService, times(2))
                .generateDownloadUrl("tryon-results/" + requestId + ".jpg", StorageService.DEFAULT_TTL);
    }

    @Test
    void getStatus_failedStatus_returnsErrorMessageAndNullResultImageUrl() {
        UUID requestId = UUID.randomUUID();
        User user = User.builder().id(userId).build();
        TryonRequest request = TryonRequest.builder()
                .id(requestId).user(user).status(TryonRequestStatus.FAILED)
                .errorMessage("FASHN try-on step exhausted all retries: bad image").build();
        when(tryonRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        TryonStatusResponse response = service.getStatus(userId, requestId);

        assertThat(response.status()).isEqualTo(TryonRequestStatus.FAILED);
        assertThat(response.errorMessage()).isEqualTo("FASHN try-on step exhausted all retries: bad image");
        assertThat(response.resultImageUrl()).isNull();
        verify(storageService, never()).generateDownloadUrl(any(), any());
    }

    private void stubHappyPathAfterRateLimit(Product product) {
        User user = User.builder().id(userId).build();
        Outfit outfit = Outfit.builder().id(outfitId).build();
        when(userReferenceQueryService.getReference(userId)).thenReturn(user);
        when(outfitItemQueryService.getReference(outfitId)).thenReturn(outfit);
        TryonRequest createdRequest = TryonRequest.builder()
                .id(UUID.randomUUID()).user(user).outfit(outfit).status(TryonRequestStatus.PENDING).build();
        when(tryonPersistenceService.createPending(user, outfit, List.of(product))).thenReturn(createdRequest);
    }
}