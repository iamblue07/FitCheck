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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    void markItemComplete_existingItem_setsStatusAndSaves() {
        UUID itemId = UUID.randomUUID();
        TryonRequestItem item = TryonRequestItem.builder().id(itemId).status(TryonRequestItemStatus.PENDING).build();
        when(tryonRequestItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        service.markItemComplete(itemId);

        assertThat(item.getStatus()).isEqualTo(TryonRequestItemStatus.COMPLETE);
        verify(tryonRequestItemRepository).save(item);
    }

    @Test
    void markItemComplete_itemNotFound_throwsResourceNotFoundException() {
        UUID itemId = UUID.randomUUID();
        when(tryonRequestItemRepository.findById(itemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markItemComplete(itemId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(itemId.toString());

        verify(tryonRequestItemRepository, never()).save(any());
    }

    @Test
    void markItemFailed_existingItem_setsStatusAndSaves() {
        UUID itemId = UUID.randomUUID();
        TryonRequestItem item = TryonRequestItem.builder().id(itemId).status(TryonRequestItemStatus.PENDING).build();
        when(tryonRequestItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        service.markItemFailed(itemId);

        assertThat(item.getStatus()).isEqualTo(TryonRequestItemStatus.FAILED);
        verify(tryonRequestItemRepository).save(item);
    }

    @Test
    void markItemFailed_itemNotFound_throwsResourceNotFoundException() {
        UUID itemId = UUID.randomUUID();
        when(tryonRequestItemRepository.findById(itemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markItemFailed(itemId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(tryonRequestItemRepository, never()).save(any());
    }

    @Test
    void markRequestComplete_existingRequest_setsStatusStorageKeyAndCompletedAtFromClock() {
        UUID requestId = UUID.randomUUID();
        TryonRequest request = TryonRequest.builder().id(requestId).status(TryonRequestStatus.PENDING).build();
        when(tryonRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        service.markRequestComplete(requestId, "tryon-results/abc.jpg");

        assertThat(request.getStatus()).isEqualTo(TryonRequestStatus.COMPLETE);
        assertThat(request.getResultImageStorageKey()).isEqualTo("tryon-results/abc.jpg");
        assertThat(request.getCompletedAt()).isEqualTo(LocalDateTime.now(FIXED_CLOCK));
        verify(tryonRequestRepository).save(request);
    }

    @Test
    void markRequestComplete_requestNotFound_throwsResourceNotFoundException() {
        UUID requestId = UUID.randomUUID();
        when(tryonRequestRepository.findById(requestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRequestComplete(requestId, "key"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(tryonRequestRepository, never()).save(any());
    }

    @Test
    void markRequestFailed_existingRequest_setsStatusErrorMessageAndCompletedAt() {
        UUID requestId = UUID.randomUUID();
        TryonRequest request = TryonRequest.builder().id(requestId).status(TryonRequestStatus.PENDING).build();
        when(tryonRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(tryonRequestItemRepository.findByTryonRequestIdAndStatus(requestId, TryonRequestItemStatus.PENDING))
                .thenReturn(List.of());

        service.markRequestFailed(requestId, "FASHN exhausted retries");

        assertThat(request.getStatus()).isEqualTo(TryonRequestStatus.FAILED);
        assertThat(request.getErrorMessage()).isEqualTo("FASHN exhausted retries");
        assertThat(request.getCompletedAt()).isEqualTo(LocalDateTime.now(FIXED_CLOCK));
        verify(tryonRequestRepository).save(request);
    }

    @Test
    void markRequestFailed_bulkTransitionsEveryPendingItemToFailed() {
        UUID requestId = UUID.randomUUID();
        TryonRequest request = TryonRequest.builder().id(requestId).status(TryonRequestStatus.PENDING).build();
        TryonRequestItem pendingItem1 = TryonRequestItem.builder()
                .id(UUID.randomUUID()).status(TryonRequestItemStatus.PENDING).build();
        TryonRequestItem pendingItem2 = TryonRequestItem.builder()
                .id(UUID.randomUUID()).status(TryonRequestItemStatus.PENDING).build();
        when(tryonRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(tryonRequestItemRepository.findByTryonRequestIdAndStatus(requestId, TryonRequestItemStatus.PENDING))
                .thenReturn(List.of(pendingItem1, pendingItem2));

        service.markRequestFailed(requestId, "step failed");

        assertThat(pendingItem1.getStatus()).isEqualTo(TryonRequestItemStatus.FAILED);
        assertThat(pendingItem2.getStatus()).isEqualTo(TryonRequestItemStatus.FAILED);
        verify(tryonRequestItemRepository).saveAll(List.of(pendingItem1, pendingItem2));
    }

    @Test
    void markRequestFailed_noPendingItemsLeft_savesEmptyListWithoutException() {
        UUID requestId = UUID.randomUUID();
        TryonRequest request = TryonRequest.builder().id(requestId).status(TryonRequestStatus.PENDING).build();
        when(tryonRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(tryonRequestItemRepository.findByTryonRequestIdAndStatus(requestId, TryonRequestItemStatus.PENDING))
                .thenReturn(List.of());

        service.markRequestFailed(requestId, "step failed");

        verify(tryonRequestItemRepository).saveAll(List.of());
    }

    @Test
    void markRequestFailed_requestNotFound_throwsResourceNotFoundExceptionAndNeverQueriesItems() {
        UUID requestId = UUID.randomUUID();
        when(tryonRequestRepository.findById(requestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRequestFailed(requestId, "irrelevant"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(tryonRequestItemRepository, never()).findByTryonRequestIdAndStatus(any(), any());
        verify(tryonRequestRepository, never()).save(any());
    }
}