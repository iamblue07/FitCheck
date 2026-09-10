package com.fitcheck.common.ai.util;

import java.util.Arrays;

public final class EmbeddingVectorTruncator {

    private EmbeddingVectorTruncator() {
    }

    public static float[] truncateAndNormalize(float[] fullEmbedding, int targetDimensions) {
        if (fullEmbedding.length < targetDimensions) {
            throw new IllegalStateException(
                    "Embedding model returned %d dimensions, expected at least %d"
                            .formatted(fullEmbedding.length, targetDimensions));
        }
        return normalize(Arrays.copyOf(fullEmbedding, targetDimensions));
    }

    private static float[] normalize(float[] vector) {
        double sumOfSquares = 0.0;
        for (float value : vector) {
            sumOfSquares += (double) value * value;
        }
        float norm = (float) Math.sqrt(sumOfSquares);
        if (norm == 0f) {
            return vector;
        }
        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = vector[i] / norm;
        }
        return normalized;
    }
}