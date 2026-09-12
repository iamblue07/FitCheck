package com.fitcheck.tryon.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fitcheck.common.exception.ExternalServiceException;
import com.fitcheck.tryon.domain.FashnPredictionResult;
import com.fitcheck.tryon.properties.TryonProperties;
import lombok.AllArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
@AllArgsConstructor
@EnableConfigurationProperties(TryonProperties.class)
public class FashnClient {

    private final RestClient fashnRestClient;
    private final TryonProperties properties;

    public String submitTryonV16(String modelImageUrl, String garmentImageUrl, String category) {
        FashnRunRequest request = new FashnRunRequest(
                properties.fashnV16ModelName(),
                new FashnRunInputs(modelImageUrl, garmentImageUrl, category,
                        properties.fashnV16Mode(), properties.fashnOutputFormat()));
        return submit(request);
    }

    public String submitTryonMax(String modelImageUrl, String productImageUrl) {
        FashnMaxRunRequest request = new FashnMaxRunRequest(
                properties.fashnMaxModelName(),
                new FashnMaxRunInputs(modelImageUrl, productImageUrl, properties.fashnMaxResolution(),
                        properties.fashnMaxGenerationMode(), properties.fashnOutputFormat()));
        return submit(request);
    }

    public FashnPredictionResult poll(String predictionId) {
        FashnPredictionResult result;
        try {
            result = fashnRestClient.get()
                    .uri("/status/{id}", predictionId)
                    .retrieve()
                    .body(FashnPredictionResult.class);
        } catch (RestClientException e) {
            throw new ExternalServiceException(
                    "FASHN poll call failed for prediction " + predictionId + ": " + e.getMessage());
        }

        if (result == null) {
            throw new ExternalServiceException("FASHN /v1/status returned no body for prediction " + predictionId);
        }
        return result;
    }

    private String submit(Object request) {
        FashnPredictionResult result;
        try {
            result = fashnRestClient.post()
                    .uri("/run")
                    .body(request)
                    .retrieve()
                    .body(FashnPredictionResult.class);
        } catch (RestClientException e) {
            throw new ExternalServiceException("FASHN submit call failed: " + e.getMessage());
        }

        if (result == null || result.id() == null) {
            throw new ExternalServiceException("FASHN /v1/run returned no prediction id");
        }
        return result.id();
    }

    private record FashnRunRequest(
            @JsonProperty("model_name") String modelName,
            FashnRunInputs inputs) {
    }

    private record FashnRunInputs(
            @JsonProperty("model_image") String modelImage,
            @JsonProperty("garment_image") String garmentImage,
            String category,
            String mode,
            @JsonProperty("output_format") String outputFormat) {
    }

    private record FashnMaxRunRequest(
            @JsonProperty("model_name") String modelName,
            FashnMaxRunInputs inputs) {
    }

    private record FashnMaxRunInputs(
            @JsonProperty("model_image") String modelImage,
            @JsonProperty("product_image") String productImage,
            String resolution,
            @JsonProperty("generation_mode") String generationMode,
            @JsonProperty("output_format") String outputFormat) {
    }
}