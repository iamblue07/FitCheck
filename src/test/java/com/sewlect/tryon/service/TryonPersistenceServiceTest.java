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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TryonPersistenceServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-09-04T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private TryonRequestRepository tryonRequestRepository;

    @Mock
    private TryonRequestItemRepository tryonRequestItemRepository;

    private TryonPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new TryonPersistenceService(tryonRequestRepository, tryonRequestItemRepository, FIXED_CLOCK);
    }

    @Test
    void createPending_savesRequestThenItemsInSequenceOrder() {
        UUID requestId = UUID.randomUUID();
        User user = User.builder().id(UUID.randomUUID()).build();
        Outfit outfit = Outfit.builder().id(UUID.randomUUID()).build();
        Product product1 = Product.builder().id(UUID.randomUUID()).build();
        Product product2 = Product.builder().id(UUID.randomUUID()).build();
        TryonRequest savedRequest = TryonRequest.builder()
                .id(requestId)
                .user(user)
                .outfit(outfit)
                .status(TryonRequestStatus.PENDING)
                .build();
        when(tryonRequestRepository.saveAndFlush(any(TryonRequest.class))).thenReturn(savedRequest);

        TryonRequest result = service.createPending(user, outfit, List.of(product1, product2));

        assertThat(result).isEqualTo(savedRequest);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TryonRequestItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(tryonRequestItemRepository).saveAll(itemsCaptor.capture());
        List<TryonRequestItem> savedItems = itemsCaptor.getValue();

        assertThat(savedItems).hasSize(2);
        assertThat(savedItems.get(0).getProduct()).isEqualTo(product1);
        assertThat(savedItems.get(0).getSequenceOrder()).isEqualTo(0);
        assertThat(savedItems.get(0).getStatus()).isEqualTo(TryonRequestItemStatus.PENDING);
        assertThat(savedItems.get(0).getTryonRequest()).isEqualTo(savedRequest);
        assertThat(savedItems.get(1).getProduct()).isEqualTo(product2);
        assertThat(savedItems.get(1).getSequenceOrder()).isEqualTo(1);
        assertThat(savedItems.get(1).getStatus()).isEqualTo(TryonRequestItemStatus.PENDING);
    }

    @Test
    void createPending_emptyItemList_savesEmptyListWithoutException() {
        User user = User.builder().id(UUID.randomUUID()).build();
        Outfit outfit = Outfit.builder().id(UUID.randomUUID()).build();
        TryonRequest savedRequest = TryonRequest.builder()
                .id(UUID.randomUUID())
                .user(user)
                .outfit(outfit)
                .status(TryonRequestStatus.PENDING)
                .build();
        when(tryonRequestRepository.saveAndFlush(any(TryonRequest.class))).thenReturn(savedRequest);

        service.createPending(user, outfit, List.of());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TryonRequestItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(tryonRequestItemRepository).saveAll(itemsCaptor.capture());
        assertThat(itemsCaptor.getValue()).isEmpty();
    }

    @Test
    void createPending_nullOutfit_isPersistedAsNull() {
        User user = User.builder().id(UUID.randomUUID()).build();
        TryonRequest savedRequest = TryonRequest.builder()
                .id(UUID.randomUUID())
                .user(user)
                .outfit(null)
                .status(TryonRequestStatus.PENDING)
                .build();
        when(tryonRequestRepository.saveAndFlush(any(TryonRequest.class))).thenReturn(savedRequest);

        TryonRequest result = service.createPending(user, null, List.of());

        assertThat(result.getOutfit()).isNull();
    }

    @Test
    void markRequestProcessing_pendingRequest_transitionsToProcessingStampingUpdatedAtAndReturnsTrue() {
        UUID requestId = UUID.randomUUID();
        when(tryonRequestRepository.transitionStatus(requestId, TryonRequestStatus.PENDING,
                TryonRequestStatus.PROCESSING, LocalDateTime.now(FIXED_CLOCK))).thenReturn(1);

        assertThat(service.markRequestProcessing(requestId)).isTrue();
    }

    @Test
    void markRequestProcessing_requestNoLongerPending_returnsFalse() {
        UUID requestId = UUID.randomUUID();
        when(tryonRequestRepository.transitionStatus(requestId, TryonRequestStatus.PENDING,
                TryonRequestStatus.PROCESSING, LocalDateTime.now(FIXED_CLOCK))).thenReturn(0);

        assertThat(service.markRequestProcessing(requestId)).isFalse();
    }

    @Test
    void markItemComplete_pendingItem_transitionsToCompleteAndReturnsTrue() {
        UUID itemId = UUID.randomUUID();
        when(tryonRequestItemRepository.transitionStatus(
                itemId, TryonRequestItemStatus.PENDING, TryonRequestItemStatus.COMPLETE)).thenReturn(1);

        assertThat(service.markItemComplete(itemId)).isTrue();
    }

    @Test
    void markItemComplete_itemNoLongerPending_returnsFalse() {
        UUID itemId = UUID.randomUUID();
        when(tryonRequestItemRepository.transitionStatus(
                itemId, TryonRequestItemStatus.PENDING, TryonRequestItemStatus.COMPLETE)).thenReturn(0);

        assertThat(service.markItemComplete(itemId)).isFalse();
    }

    @Test
    void markItemFailed_pendingItem_transitionsToFailedAndReturnsTrue() {
        UUID itemId = UUID.randomUUID();
        when(tryonRequestItemRepository.transitionStatus(
                itemId, TryonRequestItemStatus.PENDING, TryonRequestItemStatus.FAILED)).thenReturn(1);

        assertThat(service.markItemFailed(itemId)).isTrue();
    }

    @Test
    void markItemFailed_itemNoLongerPending_returnsFalse() {
        UUID itemId = UUID.randomUUID();
        when(tryonRequestItemRepository.transitionStatus(
                itemId, TryonRequestItemStatus.PENDING, TryonRequestItemStatus.FAILED)).thenReturn(0);

        assertThat(service.markItemFailed(itemId)).isFalse();
    }

    @Test
    void markRequestComplete_inFlightRequest_completesWithStorageKeyAndCompletedAtFromClock() {
        UUID requestId = UUID.randomUUID();
        when(tryonRequestRepository.completeIfStatusIn(eq(requestId), anyCollection(),
                eq(TryonRequestStatus.COMPLETE), eq("tryon-results/abc.jpg"), eq(LocalDateTime.now(FIXED_CLOCK))))
                .thenReturn(1);

        assertThat(service.markRequestComplete(requestId, "tryon-results/abc.jpg")).isTrue();

        assertThat(capturedExpectedStatusesForComplete(requestId))
                .containsExactlyInAnyOrder(TryonRequestStatus.PENDING, TryonRequestStatus.PROCESSING);
    }

    @Test
    void markRequestComplete_requestAlreadyTerminal_returnsFalse() {
        UUID requestId = UUID.randomUUID();
        when(tryonRequestRepository.completeIfStatusIn(eq(requestId), anyCollection(),
                eq(TryonRequestStatus.COMPLETE), eq("tryon-results/abc.jpg"), any())).thenReturn(0);

        assertThat(service.markRequestComplete(requestId, "tryon-results/abc.jpg")).isFalse();
    }

    @Test
    void markRequestFailed_inFlightRequest_failsRequestThenBulkFailsPendingItems() {
        UUID requestId = UUID.randomUUID();
        when(tryonRequestRepository.failIfStatusIn(eq(requestId), anyCollection(),
                eq(TryonRequestStatus.FAILED), eq("FASHN exhausted retries"), eq(LocalDateTime.now(FIXED_CLOCK))))
                .thenReturn(1);

        assertThat(service.markRequestFailed(requestId, "FASHN exhausted retries")).isTrue();

        InOrder ordered = inOrder(tryonRequestRepository, tryonRequestItemRepository);
        ordered.verify(tryonRequestRepository).failIfStatusIn(eq(requestId), anyCollection(),
                eq(TryonRequestStatus.FAILED), eq("FASHN exhausted retries"), any());
        ordered.verify(tryonRequestItemRepository).transitionStatusByTryonRequestId(
                requestId, TryonRequestItemStatus.PENDING, TryonRequestItemStatus.FAILED);
        assertThat(capturedExpectedStatusesForFail(requestId))
                .containsExactlyInAnyOrder(TryonRequestStatus.PENDING, TryonRequestStatus.PROCESSING);
    }

    @Test
    void markRequestFailed_requestAlreadyTerminal_returnsFalseAndNeverTouchesItems() {
        UUID requestId = UUID.randomUUID();
        when(tryonRequestRepository.failIfStatusIn(eq(requestId), anyCollection(),
                eq(TryonRequestStatus.FAILED), eq("step failed"), any())).thenReturn(0);

        assertThat(service.markRequestFailed(requestId, "step failed")).isFalse();

        verify(tryonRequestItemRepository, never()).transitionStatusByTryonRequestId(any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    private Collection<TryonRequestStatus> capturedExpectedStatusesForComplete(UUID requestId) {
        ArgumentCaptor<Collection<TryonRequestStatus>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(tryonRequestRepository).completeIfStatusIn(eq(requestId), captor.capture(), any(), any(), any());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private Collection<TryonRequestStatus> capturedExpectedStatusesForFail(UUID requestId) {
        ArgumentCaptor<Collection<TryonRequestStatus>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(tryonRequestRepository).failIfStatusIn(eq(requestId), captor.capture(), any(), any(), any());
        return captor.getValue();
    }
}