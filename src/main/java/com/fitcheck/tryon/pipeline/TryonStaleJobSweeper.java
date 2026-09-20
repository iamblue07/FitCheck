package com.fitcheck.tryon.pipeline;

import com.fitcheck.tryon.entity.TryonRequest;
import com.fitcheck.tryon.enums.TryonRequestStatus;
import com.fitcheck.tryon.properties.TryonProperties;
import com.fitcheck.tryon.repository.TryonRequestRepository;
import com.fitcheck.tryon.service.TryonPersistenceService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@AllArgsConstructor
@EnableConfigurationProperties(TryonProperties.class)
public class TryonStaleJobSweeper {

    public static final String ABANDONED_MESSAGE =
            "Try-on job was abandoned by the application and swept after exceeding the stale in-flight threshold";

    private static final Set<TryonRequestStatus> IN_FLIGHT_STATUSES =
            Set.of(TryonRequestStatus.PENDING, TryonRequestStatus.PROCESSING);

    private final TryonRequestRepository tryonRequestRepository;
    private final TryonPersistenceService tryonPersistenceService;
    private final TryonProperties properties;
    private final Clock clock;

    @Scheduled(fixedRateString = "${tryon.stale-sweep-interval-ms}")
    public void sweepStaleInFlightRequests() {
        LocalDateTime cutoff = LocalDateTime.now(clock)
                .minus(Duration.ofMillis(properties.staleInFlightThresholdMs()));

        List<TryonRequest> stale =
                tryonRequestRepository.findByStatusInAndUpdatedAtBefore(IN_FLIGHT_STATUSES, cutoff);

        for (TryonRequest tryonRequest : stale) {
            log.warn("Sweeping abandoned try-on request {} in status {} last updated at {}",
                    tryonRequest.getId(), tryonRequest.getStatus(), tryonRequest.getUpdatedAt());
            tryonPersistenceService.markRequestFailed(tryonRequest.getId(), ABANDONED_MESSAGE);
        }
    }
}