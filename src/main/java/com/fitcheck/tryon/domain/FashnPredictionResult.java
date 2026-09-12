package com.fitcheck.tryon.domain;

import java.util.List;

public record FashnPredictionResult(
        String id,
        String status,
        List<String> output,
        FashnError error
) {
}