package com.sewlect.tryon.support;

import com.sewlect.common.exception.ExternalServiceException;
import com.sewlect.tryon.domain.FashnError;
import com.sewlect.tryon.domain.FashnPredictionResult;
import com.sewlect.tryon.properties.TryonProperties;
import com.sewlect.tryon.service.FashnClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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

    private static final Instant FAR_FUTURE_JOB_DEADLINE = Instant.parse("2099-01-01T00:00:00Z");

    @Mock
    private FashnClient fashnClient;

    private final FashnErrorClassifier classifier = new FashnErrorClassifier();

    private TryonStepExecutor executor;
    private Supplier<String> submitter;

    @BeforeEach
    void setUp() {
        executor = executorWith(properties(5, 60000));
        submitter = mockSupplier("pred-1");
    }

    // ---------- completed with output ----------

    @Test
    void execute_firstAttemptSucceeds_returnsOutputUrlWithoutResubmitting() {
        when(fashnClient.poll("pred-1")).thenReturn(completedResult("pred-1", "https://cdn.test/out.jpg"));

        String result = executor.execute(submitter, FAR_FUTURE_JOB_DEADLINE);

        assertThat(result).isEqualTo("https://cdn.test/out.jpg");
        verify(submitter, times(1)).get();
    }

    @Test
    void execute_multiplePollsBeforeTerminal_pollsTheSamePredictionUntilTerminal() {
        when(fashnClient.poll("pred-1"))
                .thenReturn(processingResult("pred-1"))
                .thenReturn(processingResult("pred-1"))
                .thenReturn(completedResult("pred-1", "https://cdn.test/out.jpg"));

        String result = executor.execute(submitter, FAR_FUTURE_JOB_DEADLINE);

        assertThat(result).isEqualTo("https://cdn.test/out.jpg");
        verify(fashnClient, times(3)).poll("pred-1");
        verify(submitter, times(1)).get();
    }

    // ---------- poll ceiling expires: the case this restructure exists for ----------

    @Test
    void execute_predictionStaysNonTerminalPastPollCeiling_submitsExactlyOnceAndThrows() {
        TryonStepExecutor timeoutExecutor = executorWith(properties(5, 10));
        when(fashnClient.poll("pred-1")).thenReturn(processingResult("pred-1"));

        assertThatThrownBy(() -> timeoutExecutor.execute(submitter, FAR_FUTURE_JOB_DEADLINE))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("Polling timed out")
                .hasMessageContaining("pred-1");

        verify(submitter, times(1)).get();
    }

    @Test
    void execute_jobDeadlineTighterThanPollCeiling_stopsAtTheJobDeadlineAndSubmitsOnce() {
        TryonStepExecutor longCeilingExecutor = executorWith(properties(5, 600000));
        when(fashnClient.poll("pred-1")).thenReturn(processingResult("pred-1"));

        Instant tightJobDeadline = Instant.now().plusMillis(10);

        assertThatThrownBy(() -> longCeilingExecutor.execute(submitter, tightJobDeadline))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("Polling timed out");

        verify(submitter, times(1)).get();
    }

    @Test
    void execute_jobDeadlineAlreadyPassed_neverPollsAtAll() {
        TryonStepExecutor longCeilingExecutor = executorWith(properties(5, 600000));

        Instant expiredJobDeadline = Instant.now().minusSeconds(1);

        assertThatThrownBy(() -> longCeilingExecutor.execute(submitter, expiredJobDeadline))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("Polling timed out");

        verify(submitter, times(1)).get();
        verify(fashnClient, never()).poll(any());
    }

    // ---------- permanent terminal failure ----------

    @Test
    void execute_permanentErrorName_throwsImmediatelyWithoutConsumingFurtherAttempts() {
        when(fashnClient.poll("pred-1"))
                .thenReturn(failedResult("pred-1", "failed", new FashnError("ImageLoadError", "bad model image")));

        assertThatThrownBy(() -> executor.execute(submitter, FAR_FUTURE_JOB_DEADLINE))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("failed permanently")
                .hasMessageContaining("bad model image");

        verify(submitter, times(1)).get();
    }

    @Test
    void execute_unknownErrorName_isTreatedAsPermanentAndNeverResubmits() {
        when(fashnClient.poll("pred-1"))
                .thenReturn(failedResult("pred-1", "failed", new FashnError("BrandNewError", "who knows")));

        assertThatThrownBy(() -> executor.execute(submitter, FAR_FUTURE_JOB_DEADLINE))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("failed permanently");

        verify(submitter, times(1)).get();
    }

    @Test
    void execute_terminalFailureWithNoErrorObject_isTreatedAsPermanent() {
        when(fashnClient.poll("pred-1")).thenReturn(failedResult("pred-1", "failed", null));

        assertThatThrownBy(() -> executor.execute(submitter, FAR_FUTURE_JOB_DEADLINE))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("pred-1")
                .hasMessageContaining("failed");

        verify(submitter, times(1)).get();
    }

    @Test
    void execute_canceledStatusWithPermanentError_throwsWithoutResubmitting() {
        when(fashnClient.poll("pred-1"))
                .thenReturn(failedResult("pred-1", "canceled", new FashnError("InputValidationError", "bad params")));

        assertThatThrownBy(() -> executor.execute(submitter, FAR_FUTURE_JOB_DEADLINE))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("bad params");

        verify(submitter, times(1)).get();
    }

    @Test
    void execute_timedOutStatusWithTransientError_isRetriedLikeAnyOtherTransient() {
        submitter = mockSupplier("pred-1", "pred-2");
        when(fashnClient.poll("pred-1"))
                .thenReturn(failedResult("pred-1", "timed_out", new FashnError("UnavailableError", "overloaded")));
        when(fashnClient.poll("pred-2")).thenReturn(completedResult("pred-2", "https://cdn.test/out.jpg"));

        String result = executor.execute(submitter, FAR_FUTURE_JOB_DEADLINE);

        assertThat(result).isEqualTo("https://cdn.test/out.jpg");
        verify(submitter, times(2)).get();
    }

    @Test
    void execute_completedWithEmptyOutputList_isPermanentNotRetried() {
        when(fashnClient.poll("pred-1"))
                .thenReturn(new FashnPredictionResult("pred-1", "completed", List.of(), null));

        assertThatThrownBy(() -> executor.execute(submitter, FAR_FUTURE_JOB_DEADLINE))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("completed with no output image");

        verify(submitter, times(1)).get();
    }

    @Test
    void execute_completedWithNullOutputList_isPermanentNotRetried() {
        when(fashnClient.poll("pred-1"))
                .thenReturn(new FashnPredictionResult("pred-1", "completed", null, null));

        assertThatThrownBy(() -> executor.execute(submitter, FAR_FUTURE_JOB_DEADLINE))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("completed with no output image");

        verify(submitter, times(1)).get();
    }

    // ---------- transient terminal failure ----------

    @Test
    void execute_transientErrorName_consumesOneAttemptAndSubmitsANewPrediction() {
        submitter = mockSupplier("pred-1", "pred-2");
        when(fashnClient.poll("pred-1"))
                .thenReturn(failedResult("pred-1", "failed", new FashnError("PipelineError", "pipeline blew up")));
        when(fashnClient.poll("pred-2")).thenReturn(completedResult("pred-2", "https://cdn.test/out.jpg"));

        String result = executor.execute(submitter, FAR_FUTURE_JOB_DEADLINE);

        assertThat(result).isEqualTo("https://cdn.test/out.jpg");
        verify(submitter, times(2)).get();
    }

    @Test
    void execute_transientFailuresBeyondTheAttemptBudget_throwsExhaustedAfterBudgetPlusOneSubmissions() {
        TryonStepExecutor twoAttemptExecutor = executorWith(properties(1, 60000));
        submitter = mockSupplier("pred-1", "pred-2");
        when(fashnClient.poll(any()))
                .thenReturn(failedResult("pred-1", "failed", new FashnError("UnavailableError", "overloaded")));

        assertThatThrownBy(() -> twoAttemptExecutor.execute(submitter, FAR_FUTURE_JOB_DEADLINE))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("exhausted all retries")
                .hasMessageContaining("overloaded");

        verify(submitter, times(2)).get();
    }

    @Test
    void execute_zeroMaxRetries_transientFailureStillEndsAfterOneSubmission() {
        TryonStepExecutor singleAttemptExecutor = executorWith(properties(0, 60000));
        when(fashnClient.poll("pred-1"))
                .thenReturn(failedResult("pred-1", "failed", new FashnError("ThirdPartyError", "upstream refused")));

        assertThatThrownBy(() -> singleAttemptExecutor.execute(submitter, FAR_FUTURE_JOB_DEADLINE))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("exhausted all retries");

        verify(submitter, times(1)).get();
    }

    // ---------- submission throws before returning an id ----------

    @Test
    void execute_submissionThrowsBeforeReturningAnId_isSafeToRepeatAndConsumesOneAttempt() {
        submitter = throwingThenReturningSupplier(
                new ExternalServiceException("FASHN submit call failed: connection reset"), "pred-2");
        when(fashnClient.poll("pred-2")).thenReturn(completedResult("pred-2", "https://cdn.test/out.jpg"));

        String result = executor.execute(submitter, FAR_FUTURE_JOB_DEADLINE);

        assertThat(result).isEqualTo("https://cdn.test/out.jpg");
        verify(submitter, times(2)).get();
        verify(fashnClient, never()).poll("pred-1");
    }

    @Test
    void execute_submissionThrowsWithNoAttemptsRemaining_throwsWithoutPolling() {
        TryonStepExecutor singleAttemptExecutor = executorWith(properties(0, 60000));
        Supplier<String> alwaysThrowing = alwaysThrowingSupplier(
                new ExternalServiceException("FASHN submit call failed: connection reset"));

        assertThatThrownBy(() -> singleAttemptExecutor.execute(alwaysThrowing, FAR_FUTURE_JOB_DEADLINE))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("no attempts remain")
                .hasMessageContaining("connection reset");

        verify(alwaysThrowing, times(1)).get();
        verify(fashnClient, never()).poll(any());
    }

    @Test
    void execute_submitterThrowsSomethingOtherThanExternalServiceException_propagatesWithoutRetrying() {
        Supplier<String> missingPhotoSupplier = alwaysThrowingSupplier(
                new IllegalStateException("front photo lookup exploded"));

        assertThatThrownBy(() -> executor.execute(missingPhotoSupplier, FAR_FUTURE_JOB_DEADLINE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("front photo lookup exploded");

        verify(missingPhotoSupplier, times(1)).get();
        verify(fashnClient, never()).poll(any());
    }

    // ---------- fixtures ----------

    private TryonStepExecutor executorWith(TryonProperties properties) {
        return new TryonStepExecutor(fashnClient, properties, Clock.systemUTC(), classifier);
    }

    private TryonProperties properties(int maxRetriesPerItem, long pollTimeoutMs) {
        return new TryonProperties(20, maxRetriesPerItem, 1, 1, pollTimeoutMs, 900000, 1200000, 300000);
    }

    @SuppressWarnings("unchecked")
    private Supplier<String> mockSupplier(String first, String... rest) {
        Supplier<String> mockedSupplier = mock(Supplier.class);
        lenient().when(mockedSupplier.get()).thenReturn(first, rest);
        return mockedSupplier;
    }

    @SuppressWarnings("unchecked")
    private Supplier<String> throwingThenReturningSupplier(RuntimeException failure, String thenReturn) {
        Supplier<String> mockedSupplier = mock(Supplier.class);
        lenient().when(mockedSupplier.get()).thenThrow(failure).thenReturn(thenReturn);
        return mockedSupplier;
    }

    @SuppressWarnings("unchecked")
    private Supplier<String> alwaysThrowingSupplier(RuntimeException failure) {
        Supplier<String> mockedSupplier = mock(Supplier.class);
        lenient().when(mockedSupplier.get()).thenThrow(failure);
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

    private static Duration unusedButKeepsDurationImportHonest() {
        return Duration.ZERO;
    }
}