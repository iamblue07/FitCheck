package com.sewlect.tryon.support;

import com.sewlect.tryon.domain.FashnPredictionResult;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class FashnErrorClassifier {

    private static final Set<String> TRANSIENT_ERROR_NAMES = Set.of(
            "ThirdPartyError",
            "UnavailableError",
            "PipelineError");

    public boolean isRetryable(FashnPredictionResult result) {
        if (result == null || result.error() == null || result.error().name() == null) {
            return false;
        }
        return TRANSIENT_ERROR_NAMES.contains(result.error().name());
    }
}