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
    private final FashnErrorClassifier fashnErrorClassifier;

    public String execute(Supplier<String> predictionSubmitter, Instant jobDeadline) {
        int attemptsRemaining = properties.maxRetriesPerItem();

        while (true) {
            String predictionId;
            try {
                predictionId = predictionSubmitter.get();
            } catch (ExternalServiceException e) {
                if (attemptsRemaining <= 0) {
                    throw new ExternalServiceException(
                            "FASHN try-on submission failed and no attempts remain: " + e.getMessage());
                }
                attemptsRemaining--;
                sleep(properties.retryBackoffMs());
                continue;
            }

            AttemptOutcome outcome = pollUntilTerminal(predictionId, jobDeadline);

            if (outcome.succeeded()) {
                return outcome.outputUrl();
            }
            if (!outcome.retryable()) {
                throw new ExternalServiceException(
                        "FASHN try-on step failed permanently: " + outcome.errorMessage());
            }
            if (attemptsRemaining <= 0) {
                throw new ExternalServiceException(
                        "FASHN try-on step exhausted all retries: " + outcome.errorMessage());
            }

            attemptsRemaining--;
            sleep(properties.retryBackoffMs());
        }
    }

    private AttemptOutcome pollUntilTerminal(String predictionId, Instant jobDeadline) {
        Instant pollCeiling = clock.instant().plus(Duration.ofMillis(properties.pollTimeoutMs()));
        Instant deadline = jobDeadline.isBefore(pollCeiling) ? jobDeadline : pollCeiling;

        while (clock.instant().isBefore(deadline)) {
            FashnPredictionResult result = fashnClient.poll(predictionId);

            if (TERMINAL_STATUSES.contains(result.status())) {
                if (STATUS_COMPLETED.equals(result.status())) {
                    if (result.output() == null || result.output().isEmpty()) {
                        return AttemptOutcome.permanentFailure(
                                "FASHN prediction " + result.id() + " completed with no output image");
                    }
                    return AttemptOutcome.success(result.output().get(0));
                }
                String description = describeFailure(result);
                return fashnErrorClassifier.isRetryable(result)
                        ? AttemptOutcome.transientFailure(description)
                        : AttemptOutcome.permanentFailure(description);
            }

            sleep(properties.pollIntervalMs());
        }

        return AttemptOutcome.permanentFailure(
                "Polling timed out waiting for prediction " + predictionId);
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

    private record AttemptOutcome(boolean succeeded, boolean retryable, String outputUrl, String errorMessage) {

        static AttemptOutcome success(String outputUrl) {
            return new AttemptOutcome(true, false, outputUrl, null);
        }

        static AttemptOutcome transientFailure(String errorMessage) {
            return new AttemptOutcome(false, true, null, errorMessage);
        }

        static AttemptOutcome permanentFailure(String errorMessage) {
            return new AttemptOutcome(false, false, null, errorMessage);
        }
    }
}