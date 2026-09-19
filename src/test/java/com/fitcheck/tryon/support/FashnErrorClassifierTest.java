package com.fitcheck.tryon.support;

import com.fitcheck.tryon.domain.FashnError;
import com.fitcheck.tryon.domain.FashnPredictionResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FashnErrorClassifierTest {

    private final FashnErrorClassifier classifier = new FashnErrorClassifier();

    @Test
    void imageLoadError_isPermanent() {
        assertThat(classifier.isRetryable(failedWith("ImageLoadError"))).isFalse();
    }

    @Test
    void contentModerationError_isPermanent() {
        assertThat(classifier.isRetryable(failedWith("ContentModerationError"))).isFalse();
    }

    @Test
    void inputValidationError_isPermanent() {
        assertThat(classifier.isRetryable(failedWith("InputValidationError"))).isFalse();
    }

    @Test
    void thirdPartyError_isTransient() {
        assertThat(classifier.isRetryable(failedWith("ThirdPartyError"))).isTrue();
    }

    @Test
    void unavailableError_isTransient() {
        assertThat(classifier.isRetryable(failedWith("UnavailableError"))).isTrue();
    }

    @Test
    void pipelineError_isTransient() {
        assertThat(classifier.isRetryable(failedWith("PipelineError"))).isTrue();
    }

    @Test
    void unrecognisedErrorName_isTreatedAsPermanent() {
        assertThat(classifier.isRetryable(failedWith("SomeNewErrorFashnAddedLater"))).isFalse();
    }

    @Test
    void nullErrorObject_isTreatedAsPermanent() {
        assertThat(classifier.isRetryable(
                new FashnPredictionResult("pred-1", "failed", null, null))).isFalse();
    }

    @Test
    void nullErrorName_isTreatedAsPermanent() {
        assertThat(classifier.isRetryable(
                new FashnPredictionResult("pred-1", "failed", null, new FashnError(null, "no name")))).isFalse();
    }

    private FashnPredictionResult failedWith(String errorName) {
        return new FashnPredictionResult("pred-1", "failed", null, new FashnError(errorName, "message"));
    }
}