package com.fitcheck.tryon.support;

import com.fitcheck.common.exception.ExternalServiceException;
import com.fitcheck.tryon.domain.FashnError;
import com.fitcheck.tryon.domain.FashnPredictionResult;
import com.fitcheck.tryon.properties.TryonProperties;
import com.fitcheck.tryon.service.FashnClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TryonStepExecutorTest {

    @Mock
    private FashnClient fashnClient;

    private TryonStepExecutor executor;
    private Supplier<String> submitter;

    @BeforeEach
    void setUp() {
        TryonProperties properties = new TryonProperties(
                20, 5, 1, 1, 60000,
                "tryon-v1.6", "balanced", "tryon-max", "1k", "fast", "jpeg");
        executor = new TryonStepExecutor(fashnClient, properties, Clock.systemUTC());
        submitter = mockSupplier("pred-1");
    }

    @Test
    void execute_firstAttemptSucceeds_returnsOutputUrlWithoutRetrying() {
        when(fashnClient.poll("pred-1")).thenReturn(completedResult("pred-1", "https://cdn.test/out.jpg"));

        String result = executor.execute(submitter);

        assertThat(result).isEqualTo("https://cdn.test/out.jpg");
        verify(submitter, times(1)).get();
    }

    @Test
    void execute_firstAttemptFailedStatus_retriesAndSecondAttemptSucceeds() {
        submitter = mockSupplier("pred-1", "pred-2");
        when(fashnClient.poll("pred-1")).thenReturn(failedResult("pred-1", "failed", null));
        when(fashnClient.poll("pred-2")).thenReturn(completedResult("pred-2", "https://cdn.test/out.jpg"));

        String result = executor.execute(submitter);

        assertThat(result).isEqualTo("https://cdn.test/out.jpg");
        verify(submitter, times(2)).get();
    }

    @Test
    void execute_canceledStatus_treatedAsFailureAndRetried() {
        submitter = mockSupplier("pred-1", "pred-2");
        when(fashnClient.poll("pred-1")).thenReturn(failedResult("pred-1", "canceled", null));
        when(fashnClient.poll("pred-2")).thenReturn(completedResult("pred-2", "https://cdn.test/out.jpg"));

        String result = executor.execute(submitter);

        assertThat(result).isEqualTo("https://cdn.test/out.jpg");
    }

    @Test
    void execute_timedOutStatus_treatedAsFailureAndRetried() {
        submitter = mockSupplier("pred-1", "pred-2");
        when(fashnClient.poll("pred-1")).thenReturn(failedResult("pred-1", "timed_out", null));
        when(fashnClient.poll("pred-2")).thenReturn(completedResult("pred-2", "https://cdn.test/out.jpg"));

        String result = executor.execute(submitter);

        assertThat(result).isEqualTo("https://cdn.test/out.jpg");
    }

    @Test
    void execute_allAttemptsFail_throwsExternalServiceExceptionAfterMaxRetriesPlusOneAttempts() {
        submitter = mockSupplier("p1", "p2", "p3", "p4", "p5", "p6");
        for (String predictionId : List.of("p1", "p2", "p3", "p4", "p5", "p6")) {
            when(fashnClient.poll(predictionId))
                    .thenReturn(failedResult(predictionId, "failed", new FashnError("Err", "attempt failed")));
        }

        assertThatThrownBy(() -> executor.execute(submitter))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("exhausted all retries");

        verify(submitter, times(6)).get();
    }

    @Test
    void execute_errorObjectPresent_usesErrorMessageInFinalExceptionMessage() {
        when(fashnClient.poll("pred-1"))
                .thenReturn(failedResult("pred-1", "failed", new FashnError("ImageLoadError", "bad model image")));
        TryonProperties zeroRetries = new TryonProperties(
                20, 0, 1, 1, 60000, "tryon-v1.6", "balanced", "tryon-max", "1k", "fast", "jpeg");
        TryonStepExecutor singleAttemptExecutor = new TryonStepExecutor(fashnClient, zeroRetries, Clock.systemUTC());

        assertThatThrownBy(() -> singleAttemptExecutor.execute(submitter))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("bad model image");
    }

    @Test
    void execute_errorObjectAbsentOnFailedStatus_fallsBackToGenericDescription() {
        when(fashnClient.poll("pred-1")).thenReturn(failedResult("pred-1", "failed", null));
        TryonProperties zeroRetries = new TryonProperties(
                20, 0, 1, 1, 60000, "tryon-v1.6", "balanced", "tryon-max", "1k", "fast", "jpeg");
        TryonStepExecutor singleAttemptExecutor = new TryonStepExecutor(fashnClient, zeroRetries, Clock.systemUTC());

        assertThatThrownBy(() -> singleAttemptExecutor.execute(submitter))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("pred-1")
                .hasMessageContaining("failed");
    }

    @Test
    void execute_completedWithEmptyOutputList_treatedAsFailureNotCrash() {
        submitter = mockSupplier("pred-1", "pred-2");
        when(fashnClient.poll("pred-1"))
                .thenReturn(new FashnPredictionResult("pred-1", "completed", List.of(), null));
        when(fashnClient.poll("pred-2")).thenReturn(completedResult("pred-2", "https://cdn.test/out.jpg"));

        String result = executor.execute(submitter);

        assertThat(result).isEqualTo("https://cdn.test/out.jpg");
        verify(submitter, times(2)).get();
    }

    @Test
    void execute_completedWithNullOutputList_treatedAsFailureNotCrash() {
        submitter = mockSupplier("pred-1", "pred-2");
        when(fashnClient.poll("pred-1"))
                .thenReturn(new FashnPredictionResult("pred-1", "completed", null, null));
        when(fashnClient.poll("pred-2")).thenReturn(completedResult("pred-2", "https://cdn.test/out.jpg"));

        String result = executor.execute(submitter);

        assertThat(result).isEqualTo("https://cdn.test/out.jpg");
    }

    @Test
    void execute_multiplePollsBeforeTerminal_pollsRepeatedlyUntilTerminal() {
        when(fashnClient.poll("pred-1"))
                .thenReturn(processingResult("pred-1"))
                .thenReturn(processingResult("pred-1"))
                .thenReturn(completedResult("pred-1", "https://cdn.test/out.jpg"));

        String result = executor.execute(submitter);

        assertThat(result).isEqualTo("https://cdn.test/out.jpg");
        verify(fashnClient, times(3)).poll("pred-1");
    }

    @Test
    void execute_eachRetryResubmitsFromScratch_neverReusesThePreviousPredictionId() {
        submitter = mockSupplier("pred-1", "pred-2", "pred-3");
        when(fashnClient.poll("pred-1")).thenReturn(failedResult("pred-1", "failed", null));
        when(fashnClient.poll("pred-2")).thenReturn(failedResult("pred-2", "failed", null));
        when(fashnClient.poll("pred-3")).thenReturn(completedResult("pred-3", "https://cdn.test/out.jpg"));

        executor.execute(submitter);

        verify(submitter, times(3)).get();
        verify(fashnClient, never()).poll("pred-1-resumed");
    }

    @Test
    void execute_pollNeverReachesTerminalWithinTimeout_treatedAsFailedAttempt() {
        TryonProperties tightTimeout = new TryonProperties(
                20, 0, 1, 3, 10, "tryon-v1.6", "balanced", "tryon-max", "1k", "fast", "jpeg");
        TryonStepExecutor timeoutExecutor = new TryonStepExecutor(fashnClient, tightTimeout, Clock.systemUTC());
        when(fashnClient.poll("pred-1")).thenReturn(processingResult("pred-1"));

        assertThatThrownBy(() -> timeoutExecutor.execute(submitter))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("timed out");
    }

    @Test
    void execute_zeroMaxRetries_onlyOneAttemptTotalOnFailure() {
        TryonProperties zeroRetries = new TryonProperties(
                20, 0, 1, 1, 60000, "tryon-v1.6", "balanced", "tryon-max", "1k", "fast", "jpeg");
        TryonStepExecutor singleAttemptExecutor = new TryonStepExecutor(fashnClient, zeroRetries, Clock.systemUTC());
        when(fashnClient.poll("pred-1")).thenReturn(failedResult("pred-1", "failed", null));

        assertThatThrownBy(() -> singleAttemptExecutor.execute(submitter))
                .isInstanceOf(ExternalServiceException.class);

        verify(submitter, times(1)).get();
    }

    @SuppressWarnings("unchecked")
    private Supplier<String> mockSupplier(String first, String... rest) {
        Supplier<String> mockedSupplier = mock(Supplier.class);
        lenient().when(mockedSupplier.get()).thenReturn(first, rest);
        return mockedSupplier;
    }

    private FashnPredictionResult completedResult(String id, String outputUrl) {
        return new FashnPredictionResult(id, "completed", List.of(outputUrl), null);
    }

    private FashnPredictionResult failedResult(String id, String status, FashnError error) {
        return new FashnPredictionResult(id, status, null, error);
    }

    private FashnPredictionResult processingResult(String id) {
        return new FashnPredictionResult(id, "processing", null, null);
    }
}