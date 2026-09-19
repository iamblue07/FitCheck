package com.fitcheck.common.logging.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ExternalCallLogger {

    public static final String EXTERNAL_LOGGER_NAME = "com.fitcheck.external.calls";

    private static final Logger log = LoggerFactory.getLogger(EXTERNAL_LOGGER_NAME);

    public void logCall(String provider, String operation, long durationMs, boolean success) {
        if (success) {
            log.info("provider={} operation={} durationMs={} outcome=success", provider, operation, durationMs);
        } else {
            log.warn("provider={} operation={} durationMs={} outcome=failure", provider, operation, durationMs);
        }
    }
}