package com.fitcheck.outfit.service;

import com.fitcheck.common.exception.ExternalServiceException;
import com.fitcheck.common.taxonomy.enums.GarmentRole;
import com.fitcheck.outfit.properties.OutfitPromptProperties;
import com.fitcheck.outfit.domain.OutfitBlueprint;
import com.fitcheck.outfit.domain.PromptInferredQuery;
import com.fitcheck.outfit.domain.SlotDescription;
import com.fitcheck.outfit.domain.StructuredPromptQuery;
import com.fitcheck.outfit.support.OutfitGenderFilterResolver;
import lombok.AllArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fitcheck.common.ai.properties.OllamaCloudProperties;

@Service
@AllArgsConstructor
@EnableConfigurationProperties(OutfitPromptProperties.class)
public class PromptExtractionService {

    private final OllamaChatModel ollamaCloudChatModel;
    private final OutfitPromptProperties properties;
    private final OllamaCloudProperties ollamaCloudProperties;

    public StructuredPromptQuery extract(String rawPrompt) {
        StructuredPromptQuery query;
        try {
            query = ChatClient.create(ollamaCloudChatModel).prompt()
                    .options(OllamaChatOptions.builder()
                            .model(ollamaCloudProperties.chatModel())
                            .disableThinking())
                    .user(buildPrompt(rawPrompt))
                    .call()
                    .entity(StructuredPromptQuery.class);
        } catch (RuntimeException e) {
            throw new ExternalServiceException("Ollama Cloud prompt extraction failed: " + e.getMessage());
        }

        if (query == null || query.blueprints() == null || query.blueprints().isEmpty()) {
            throw new ExternalServiceException("Ollama Cloud prompt extraction returned no usable blueprints");
        }

        for (OutfitBlueprint blueprint : query.blueprints()) {
            validateShape(blueprint);
        }

        return query;
    }

    public PromptInferredQuery extractWithInferredFilters(String rawPrompt) {
        PromptInferredQuery query;
        try {
            query = ChatClient.create(ollamaCloudChatModel).prompt()
                    .options(OllamaChatOptions.builder()
                            .model(ollamaCloudProperties.chatModel())
                            .disableThinking())
                    .user(buildInferredFiltersPrompt(rawPrompt))
                    .call()
                    .entity(PromptInferredQuery.class);
        } catch (RuntimeException e) {
            throw new ExternalServiceException("Ollama Cloud prompt extraction failed: " + e.getMessage());
        }

        if (query == null || query.blueprints() == null || query.blueprints().isEmpty()) {
            throw new ExternalServiceException("Ollama Cloud prompt extraction returned no usable blueprints");
        }

        for (OutfitBlueprint blueprint : query.blueprints()) {
            validateShape(blueprint);
        }

        validateGenders(query.genders());

        return query;
    }

    public String extractSingleSlot(String rawPrompt, GarmentRole role) {
        SlotDescription result;
        try {
            result = ChatClient.create(ollamaCloudChatModel).prompt()
                    .options(OllamaChatOptions.builder()
                            .model(ollamaCloudProperties.chatModel())
                            .disableThinking())
                    .user(buildSingleSlotPrompt(rawPrompt, role))
                    .call()
                    .entity(SlotDescription.class);
        } catch (RuntimeException e) {
            throw new ExternalServiceException("Ollama Cloud single-slot prompt extraction failed: " + e.getMessage());
        }

        if (result == null || result.description() == null || result.description().isBlank()) {
            throw new ExternalServiceException("Ollama Cloud single-slot prompt extraction returned no usable description");
        }

        return result.description();
    }

    private void validateShape(OutfitBlueprint blueprint) {
        List<GarmentRole> roles = blueprint.slots().stream().map(SlotDescription::role).toList();
        Set<GarmentRole> roleSet = new HashSet<>(roles);

        if (roleSet.size() != roles.size()) {
            throw new ExternalServiceException(
                    "Structured prompt extraction returned a blueprint with duplicate slot roles: " + roles);
        }

        boolean hasTop = roleSet.contains(GarmentRole.TOP);
        boolean hasBottom = roleSet.contains(GarmentRole.BOTTOM);
        boolean hasFullBody = roleSet.contains(GarmentRole.FULL_BODY);
        boolean validCore = (hasTop && hasBottom && !hasFullBody) || (hasFullBody && !hasTop && !hasBottom);

        if (!validCore) {
            throw new ExternalServiceException(
                    "Structured prompt extraction returned a blueprint violating the top+bottom/full_body shape rule: " + roles);
        }

        if (!roleSet.contains(GarmentRole.FOOTWEAR)) {
            throw new ExternalServiceException(
                    "Structured prompt extraction returned a blueprint missing footwear: " + roles);
        }
    }

    private void validateGenders(Set<String> genders) {
        if (genders == null || genders.isEmpty()) {
            throw new ExternalServiceException("Ollama Cloud prompt extraction returned no inferred genders");
        }
        if (!OutfitGenderFilterResolver.ALL_GENDERS.containsAll(genders)) {
            throw new ExternalServiceException(
                    "Ollama Cloud prompt extraction returned an invalid gender category: " + genders);
        }
    }

    private String buildPrompt(String rawPrompt) {
        return """
                A user is looking for outfit ideas based on the following free-text request:
                "%s"

                Propose up to %d alternative outfit blueprints for this request (fewer is fine if only one shape makes sense).
                Each blueprint is a list of garment slots. Each slot has:
                - role: exactly one of TOP, BOTTOM, FULL_BODY, FOOTWEAR, OUTERWEAR, ACCESSORY (use these exact names)
                - description: a short natural-language description of what that slot should look like, based on the request

                Shape rules for every blueprint:
                - Either TOP and BOTTOM together, or FULL_BODY alone — never combine FULL_BODY with TOP or BOTTOM, and never include only one of TOP/BOTTOM without the other
                - FOOTWEAR is always required
                - OUTERWEAR and ACCESSORY are optional
                - Each role may appear at most once per blueprint
                """.formatted(rawPrompt, properties.maxBlueprints());
    }

    private String buildInferredFiltersPrompt(String rawPrompt) {
        return """
                A user is looking for outfit ideas based on the following free-text request, with no profile
                constraints applied - infer who the outfit is for and their budget directly from the request itself:
                "%s"

                Propose up to %d alternative outfit blueprints for this request (fewer is fine if only one shape makes sense).
                Each blueprint is a list of garment slots. Each slot has:
                - role: exactly one of TOP, BOTTOM, FULL_BODY, FOOTWEAR, OUTERWEAR, ACCESSORY (use these exact names)
                - description: a short natural-language description of what that slot should look like, based on the request

                Shape rules for every blueprint:
                - Either TOP and BOTTOM together, or FULL_BODY alone — never combine FULL_BODY with TOP or BOTTOM, and never include only one of TOP/BOTTOM without the other
                - FOOTWEAR is always required
                - OUTERWEAR and ACCESSORY are optional
                - Each role may appear at most once per blueprint

                Also infer, from the request alone:
                - genders: which of these categories the outfit is intended for - Men, Women, Boys, Girls, Unisex (use these exact names, pick one or more; if nothing in the request suggests a specific audience, use all five)
                - budget: a single number for the user's implied spending limit for the whole outfit, in EUR, if the request mentions or implies one (e.g. "under $100", "cheap", "budget-friendly" all imply a number); omit this field entirely if no budget is implied
                """.formatted(rawPrompt, properties.maxBlueprints());
    }

    private String buildSingleSlotPrompt(String rawPrompt, GarmentRole role) {
        return """
                A user wants to refine a single garment slot in their outfit based on the following request:
                "%s"

                The slot's role is fixed as %s and will not change - the request may reasonably ask for a different
                specific type of garment within that same role (for example, a different style of top), but never a
                different role entirely.

                Respond with a slot description object:
                - role: always "%s" (do not change this)
                - description: a short natural-language description of what this slot should now look like, based on the request
                """.formatted(rawPrompt, role, role);
    }
}