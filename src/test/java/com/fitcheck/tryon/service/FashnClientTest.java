package com.fitcheck.tryon.service;

import com.fitcheck.common.exception.ExternalServiceException;
import com.fitcheck.tryon.domain.FashnPredictionResult;
import com.fitcheck.tryon.properties.TryonProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FashnClientTest {

    private static final String BASE_URL = "https://fashn.test/v1";

    private MockRestServiceServer mockServer;
    private FashnClient client;
    private TryonProperties properties;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        properties = new TryonProperties(
                20, 5, 2000, 3000, 60000,
                "tryon-v1.6", "balanced", "tryon-max", "1k", "fast", "jpeg");
        client = new FashnClient(restClient, properties);
    }

    @Test
    void submitTryonV16_success_returnsPredictionId() {
        String expectedRequest = """
                {"model_name":"tryon-v1.6","inputs":{"model_image":"https://cdn.test/model.jpg","garment_image":"https://cdn.test/garment.jpg","category":"tops","mode":"balanced","output_format":"jpeg"}}
                """;
        mockServer.expect(requestTo(BASE_URL + "/run"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(content().json(expectedRequest, true))
                .andRespond(withSuccess("""
                        {"id":"pred-1","error":null}
                        """, MediaType.APPLICATION_JSON));

        String predictionId = client.submitTryonV16(
                "https://cdn.test/model.jpg", "https://cdn.test/garment.jpg", "tops");

        assertThat(predictionId).isEqualTo("pred-1");
        mockServer.verify();
    }

    @Test
    void submitTryonV16_usesConfiguredModelNameModeAndOutputFormatFromProperties() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer customServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        TryonProperties customProperties = new TryonProperties(
                20, 5, 2000, 3000, 60000,
                "tryon-v1.7-preview", "performance", "tryon-max", "1k", "fast", "png");
        FashnClient customClient = new FashnClient(restClient, customProperties);

        String expectedRequest = """
                {"model_name":"tryon-v1.7-preview","inputs":{"model_image":"m","garment_image":"g","category":"bottoms","mode":"performance","output_format":"png"}}
                """;
        customServer.expect(requestTo(BASE_URL + "/run"))
                .andExpect(content().json(expectedRequest, true))
                .andRespond(withSuccess("""
                        {"id":"pred-custom","error":null}
                        """, MediaType.APPLICATION_JSON));

        customClient.submitTryonV16("m", "g", "bottoms");

        customServer.verify();
    }

    @Test
    void submitTryonMax_success_usesProductImageFieldAndOmitsCategory() {
        String expectedRequest = """
                {"model_name":"tryon-max","inputs":{"model_image":"https://cdn.test/model.jpg","product_image":"https://cdn.test/shoe.jpg","resolution":"1k","generation_mode":"fast","output_format":"jpeg"}}
                """;
        mockServer.expect(requestTo(BASE_URL + "/run"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(content().json(expectedRequest, true))
                .andRespond(withSuccess("""
                        {"id":"pred-max-1","error":null}
                        """, MediaType.APPLICATION_JSON));

        String predictionId = client.submitTryonMax("https://cdn.test/model.jpg", "https://cdn.test/shoe.jpg");

        assertThat(predictionId).isEqualTo("pred-max-1");
        mockServer.verify();
    }

    @Test
    void submitTryonMax_usesConfiguredResolutionAndGenerationModeFromProperties() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer customServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        TryonProperties customProperties = new TryonProperties(
                20, 5, 2000, 3000, 60000,
                "tryon-v1.6", "balanced", "tryon-max", "4k", "quality", "jpeg");
        FashnClient customClient = new FashnClient(restClient, customProperties);

        String expectedRequest = """
                {"model_name":"tryon-max","inputs":{"model_image":"m","product_image":"p","resolution":"4k","generation_mode":"quality","output_format":"jpeg"}}
                """;
        customServer.expect(requestTo(BASE_URL + "/run"))
                .andExpect(content().json(expectedRequest, true))
                .andRespond(withSuccess("""
                        {"id":"pred-custom-max","error":null}
                        """, MediaType.APPLICATION_JSON));

        customClient.submitTryonMax("m", "p");

        customServer.verify();
    }

    @Test
    void submitTryonV16_serverError_throwsExternalServiceException() {
        mockServer.expect(requestTo(BASE_URL + "/run"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.submitTryonV16("m", "g", "tops"))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("FASHN submit call failed");
    }

    @Test
    void submitTryonV16_networkFailure_throwsExternalServiceException() {
        mockServer.expect(requestTo(BASE_URL + "/run"))
                .andRespond(request -> {
                    throw new IOException("connection reset");
                });

        assertThatThrownBy(() -> client.submitTryonV16("m", "g", "tops"))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("FASHN submit call failed");
    }

    @Test
    void submitTryonV16_responseHasNullId_throwsExternalServiceException() {
        mockServer.expect(requestTo(BASE_URL + "/run"))
                .andRespond(withSuccess("""
                        {"id":null,"error":null}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.submitTryonV16("m", "g", "tops"))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("returned no prediction id");
    }

    @Test
    void submitTryonV16_emptyResponseBody_throwsExternalServiceException() {
        mockServer.expect(requestTo(BASE_URL + "/run"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        assertThatThrownBy(() -> client.submitTryonV16("m", "g", "tops"))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("returned no prediction id");
    }

    @Test
    void poll_completedStatus_parsesFullResultIncludingOutputList() {
        mockServer.expect(requestTo(BASE_URL + "/status/pred-1"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"id":"pred-1","status":"completed","output":["https://cdn.fashn.ai/result.jpg"],"error":null}
                        """, MediaType.APPLICATION_JSON));

        FashnPredictionResult result = client.poll("pred-1");

        assertThat(result.id()).isEqualTo("pred-1");
        assertThat(result.status()).isEqualTo("completed");
        assertThat(result.output()).containsExactly("https://cdn.fashn.ai/result.jpg");
        assertThat(result.error()).isNull();
    }

    @Test
    void poll_failedStatus_parsesErrorNameAndMessage() {
        mockServer.expect(requestTo(BASE_URL + "/status/pred-2"))
                .andRespond(withSuccess("""
                        {"id":"pred-2","status":"failed","output":null,"error":{"name":"ImageLoadError","message":"Error loading model image"}}
                        """, MediaType.APPLICATION_JSON));

        FashnPredictionResult result = client.poll("pred-2");

        assertThat(result.status()).isEqualTo("failed");
        assertThat(result.error().name()).isEqualTo("ImageLoadError");
        assertThat(result.error().message()).isEqualTo("Error loading model image");
    }

    @Test
    void poll_serverError_throwsExternalServiceException() {
        mockServer.expect(requestTo(BASE_URL + "/status/pred-3"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> client.poll("pred-3"))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("FASHN poll call failed for prediction pred-3");
    }

    @Test
    void poll_emptyResponseBody_throwsExternalServiceException() {
        mockServer.expect(requestTo(BASE_URL + "/status/pred-4"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        assertThatThrownBy(() -> client.poll("pred-4"))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("returned no body for prediction pred-4");
    }
}