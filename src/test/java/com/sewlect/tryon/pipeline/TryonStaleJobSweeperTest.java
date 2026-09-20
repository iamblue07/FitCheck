package com.sewlect.tryon.pipeline;

import com.sewlect.tryon.entity.TryonRequest;
import com.sewlect.tryon.enums.TryonRequestStatus;
import com.sewlect.tryon.properties.TryonProperties;
import com.sewlect.tryon.repository.TryonRequestRepository;
import com.sewlect.tryon.service.TryonPersistenceService;
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
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TryonStaleJobSweeperTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-09-04T12:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 4, 12, 0);
    private static final long JOB_TIMEOUT_MS = 900000L;
    private static final long STALE_THRESHOLD_MS = 1200000L;

    @Mock
    private TryonRequestRepository tryonRequestRepository;

    @Mock
    private TryonPersistenceService tryonPersistenceService;

    private TryonProperties properties;
    private TryonStaleJobSweeper sweeper;

    @BeforeEach
    void setUp() {
        properties = propertiesWith(STALE_THRESHOLD_MS);
        sweeper = new TryonStaleJobSweeper(tryonRequestRepository, tryonPersistenceService, properties,
                Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC));
    }

    @Test
    void sweep_processingRowPastTheThreshold_isMarkedFailedWithTheAbandonedJobMessage() {
        UUID requestId = UUID.randomUUID();
        TryonRequest wedged = TryonRequest.builder()
                .id(requestId).status(TryonRequestStatus.PROCESSING)
                .updatedAt(NOW.minusMinutes(25))
                .build();
        when(tryonRequestRepository.findByStatusInAndUpdatedAtBefore(any(), any())).thenReturn(List.of(wedged));

        sweeper.sweepStaleInFlightRequests();

        verify(tryonPersistenceService).markRequestFailed(requestId, TryonStaleJobSweeper.ABANDONED_MESSAGE);
    }

    @Test
    void sweep_cutoffIsExactlyNowMinusTheConfiguredThreshold() {
        when(tryonRequestRepository.findByStatusInAndUpdatedAtBefore(any(), any())).thenReturn(List.of());

        sweeper.sweepStaleInFlightRequests();

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(tryonRequestRepository).findByStatusInAndUpdatedAtBefore(any(), cutoffCaptor.capture());
        assertThat(cutoffCaptor.getValue()).isEqualTo(NOW.minusMinutes(20));
    }

    @Test
    void sweep_processingRowWithinTheThreshold_isNeverReturnedByTheCutoffQueryAndSoIsUntouched() {
        when(tryonRequestRepository.findByStatusInAndUpdatedAtBefore(any(), eq(NOW.minusMinutes(20))))
                .thenReturn(List.of());

        sweeper.sweepStaleInFlightRequests();

        verify(tryonPersistenceService, never()).markRequestFailed(any(), any());
    }

    @Test
    void sweep_onlyPendingAndProcessingAreConsidered_soACompleteRowIsNeverSwept() {
        when(tryonRequestRepository.findByStatusInAndUpdatedAtBefore(any(), any())).thenReturn(List.of());

        sweeper.sweepStaleInFlightRequests();

        ArgumentCaptor<Collection<TryonRequestStatus>> statusCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(tryonRequestRepository).findByStatusInAndUpdatedAtBefore(statusCaptor.capture(), any());
        assertThat(statusCaptor.getValue())
                .containsExactlyInAnyOrder(TryonRequestStatus.PENDING, TryonRequestStatus.PROCESSING);
        assertThat(statusCaptor.getValue()).doesNotContain(TryonRequestStatus.COMPLETE, TryonRequestStatus.FAILED);
    }

    @Test
    void sweep_everyStaleRowIsSweptNotJustTheFirst() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(tryonRequestRepository.findByStatusInAndUpdatedAtBefore(any(), any())).thenReturn(List.of(
                TryonRequest.builder().id(first).status(TryonRequestStatus.PROCESSING)
                        .updatedAt(NOW.minusMinutes(25)).build(),
                TryonRequest.builder().id(second).status(TryonRequestStatus.PENDING)
                        .updatedAt(NOW.minusMinutes(40)).build()));

        sweeper.sweepStaleInFlightRequests();

        verify(tryonPersistenceService).markRequestFailed(first, TryonStaleJobSweeper.ABANDONED_MESSAGE);
        verify(tryonPersistenceService).markRequestFailed(second, TryonStaleJobSweeper.ABANDONED_MESSAGE);
    }

    @Test
    void sweep_thresholdStrictlyExceedsJobTimeout_soASecondInstanceCannotKillALiveSelfTerminatingJob() {
        assertThat(properties.staleInFlightThresholdMs()).isGreaterThan(properties.jobTimeoutMs());
    }

    private TryonProperties propertiesWith(long staleInFlightThresholdMs) {
        return new TryonProperties(20, 1, 2000, 3000, 180000, JOB_TIMEOUT_MS, staleInFlightThresholdMs, 300000);
    }
}