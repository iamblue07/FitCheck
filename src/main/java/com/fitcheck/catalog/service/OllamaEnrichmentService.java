package com.fitcheck.catalog.service;

import com.fitcheck.catalog.entity.Product;
import com.fitcheck.catalog.pipeline.ProductEnrichmentResult;
import com.fitcheck.common.exception.ExternalServiceException;
import com.fitcheck.common.taxonomy.StyleTag;
import com.fitcheck.common.taxonomy.StyleTagRepository;
import lombok.AllArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class OllamaEnrichmentService implements EnrichmentService {

    private final HttpClient httpClient;

    private final OllamaChatModel ollamaChatModel;
    private final StyleTagRepository styleTagRepository;

    @Override
    public ProductEnrichmentResult enrich(Product product) {
        byte[] imageBytes = downloadImage(product.getImageUrl());
        String allowedStyleTags = styleTagRepository.findAll().stream()
                .map(StyleTag::getName)
                .collect(Collectors.joining(", "));

        try {
            return ChatClient.create(ollamaChatModel).prompt()
                    .options(OllamaChatOptions.builder().disableThinking())
                    .user(user -> user
                            .text(buildPrompt(product, allowedStyleTags))
                            .media(MimeTypeUtils.IMAGE_JPEG, new ByteArrayResource(imageBytes)))
                    .call()
                    .entity(ProductEnrichmentResult.class);
        } catch (RuntimeException e) {
            throw new ExternalServiceException(
                    "Ollama enrichment call failed for product " + product.getId() + ": " + e.getMessage());
        }
    }

    private String buildPrompt(Product product, String allowedStyleTags) {
        return """
                You are enriching a fashion catalog product listing based on its image.
                Product: %s (%s / %s / %s)
                Respond with:
                - fit, silhouette, pattern, materialGuess, formality: short descriptive terms
                - occasion: a single value, e.g. "work", "gym", "beach"
                - primaryColor, secondaryColor: dominant colors visible (secondaryColor may be omitted if there's no distinct second color)
                - layeringRole: exactly one of "base", "mid", "outer"
                - description: a 4-5 sentence description, to be used as embedding source text
                - basePrice: a plausible EUR price for this item, as a plain number
                - styleTagNames: 1-3 tags that best fit this item, chosen only from: %s
                """.formatted(product.getProductDisplayName(), product.getMasterCategory(),
                product.getSubCategory(), product.getArticleType(), allowedStyleTags);
    }

    private byte[] downloadImage(String imageUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(imageUrl)).GET().build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                throw new ExternalServiceException(
                        "Failed to download product image, HTTP " + response.statusCode() + ": " + imageUrl);
            }
            return response.body();
        } catch (IOException e) {
            throw new ExternalServiceException("Failed to download product image " + imageUrl + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException("Interrupted while downloading product image: " + imageUrl);
        }
    }
}