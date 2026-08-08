package com.fitcheck.identity.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record UpdateStylePreferencesRequest (

        @NotNull
        List<@NotNull UUID> styleTagIds
) {
}
