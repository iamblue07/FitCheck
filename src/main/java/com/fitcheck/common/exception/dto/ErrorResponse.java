package com.fitcheck.common.exception.dto;

import jakarta.annotation.Nullable;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

public record ErrorResponse (
    Instant timestamp,
    int status,
    String error,
    String message,
    String path,
    String correlationId,
    @Nullable
    Map<String, String> fieldErrors
) {}
