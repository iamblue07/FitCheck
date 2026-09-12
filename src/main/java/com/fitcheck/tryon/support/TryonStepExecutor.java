package com.fitcheck.tryon.support;

import com.fitcheck.common.exception.ExternalServiceException;
import com.fitcheck.tryon.domain.FashnPredictionResult;
import com.fitcheck.tryon.properties.TryonProperties;
import com.fitcheck.tryon.service.FashnClient;
import lombok.AllArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.function.Supplier;

@Component
@AllArgsConstructor
@EnableConfigurationProperties(TryonProperties.class)
public class TryonStepExecutor {

    private static final String STATUS_COMPLETED = "completed";
    private static final Set<String> TERMINAL_STATUSES = Set.of(STATUS_COMPLETED, "failed", "canceled", "timed_out");

    private final FashnClient fashnClient;
    private final TryonProperties properties;
    private final Clock clock;

    public String execute(Supplier<String> predictionSubmitter) {
        String lastErrorMessage = null;

        for (int attempt = 0; attempt <= properties.maxRetriesPerItem(); attempt++) {
            if (attempt > 0) {
                sleep(properties.retryBackoffMs());
            }

            String predictionId = predictionSubmitter.get();
            AttemptOutcome outcome = pollUntilTerminal(predictionId);

            if (outcome.succeeded()) {
                return outcome.outputUrl();
            }
            lastErrorMessage = outcome.errorMessage();
        }

        throw new ExternalServiceException("FASHN try-on step exhausted all retries: " + lastErrorMessage);
    }

    private AttemptOutcome pollUntilTerminal(String predictionId) {
        Instant deadline = clock.instant().plus(Duration.ofMillis(properties.pollTimeoutMs()));

        while (clock.instant().isBefore(deadline)) {
            FashnPredictionResult result = fashnClient.poll(predictionId);

            if (TERMINAL_STATUSES.contains(result.status())) {
                if (STATUS_COMPLETED.equals(result.status())) {
                    if (result.output() == null || result.output().isEmpty()) {
                        return AttemptOutcome.failure(
                                "FASHN prediction " + result.id() + " completed with no output image");
                    }
                    return AttemptOutcome.success(result.output().get(0));
                }
                return AttemptOutcome.failure(describeFailure(result));
            }

            sleep(properties.pollIntervalMs());
        }

        return AttemptOutcome.failure(
                "Polling timed out after " + properties.pollTimeoutMs() + "ms for prediction " + predictionId);
    }

    private String describeFailure(FashnPredictionResult result) {
        if (result.error() != null) {
            return result.error().message();
        }
        return "FASHN prediction " + result.id() + " ended with status " + result.status();
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException("Interrupted while waiting on FASHN try-on step");
        }
    }

    private record AttemptOutcome(boolean succeeded, String outputUrl, String errorMessage) {

        static AttemptOutcome success(String outputUrl) {
            return new AttemptOutcome(true, outputUrl, null);
        }

        static AttemptOutcome failure(String errorMessage) {
            return new AttemptOutcome(false, null, errorMessage);
        }
    }
}