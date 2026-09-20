package com.sewlect.common.exception.support;

import com.sewlect.common.exception.dto.ErrorResponse;
import com.sewlect.common.logging.filter.CorrelationIdFilter;
import lombok.AllArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

@Component
@AllArgsConstructor
public class ErrorResponseFactory {

    private final Clock clock;

    public ErrorResponse build(HttpStatus status, String message, String path) {
        return build(status, message, path, null);
    }

    public ErrorResponse build(HttpStatus status, String message, String path, Map<String, String> fieldErrors) {
        return new ErrorResponse(
                Instant.now(clock),
                status.value(),
                status.getReasonPhrase(),
                message,
                path,
                MDC.get(CorrelationIdFilter.MDC_KEY),
                fieldErrors);
    }
}