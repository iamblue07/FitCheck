package com.sewlect.common.logging.enums;

public enum ExternalCallOutcome {

    SUCCESS("success"),
    RETRYABLE_FAILURE("retryable_failure"),
    PERMANENT_FAILURE("permanent_failure");

    private final String logValue;

    ExternalCallOutcome(String logValue) {
        this.logValue = logValue;
    }

    public String logValue() {
        return logValue;
    }
}