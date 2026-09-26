package com.sewlect.outfit.controller;

import com.sewlect.common.taxonomy.enums.GarmentRole;
import com.sewlect.outfit.dto.AlternativeCandidateResponse;
import com.sewlect.outfit.domain.CompatibilityScoreBreakdown;
import com.sewlect.outfit.domain.OutfitItemView;
import com.sewlect.outfit.dto.OutfitResponse;
import com.sewlect.outfit.service.GarmentSwapService;
import com.sewlect.support.WebSliceTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GarmentSwapController.class)
@Import(WebSliceTestConfig.class)
@TestPropertySource(properties = {
        WebSliceTestConfig.JWT_SECRET_PROPERTY,
        WebSliceTestConfig.JWT_ACCESS_EXPIRATION_PROPERTY,
        WebSliceTestConfig.JWT_REFRESH_EXPIRATION_PROPERTY,
        WebSliceTestConfig.SUBJECT_HASH_SECRET_PROPERTY
})
class GarmentSwapControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GarmentSwapService garmentSwapService;

    @Test
    void listAlternatives_missingAuthorizationHeader_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/outfits/{outfitId}/items/{itemId}/alternatives",
                        UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void listAlternatives_validToken_returns200WithMappedBodyAndDerivesUserIdFromJwtSubject() throws Exception {
        UUID outfitId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        CompatibilityScoreBreakdown breakdown = new CompatibilityScoreBreakdown(
                new BigDecimal("0.8"), new BigDecimal("0.7"), new BigDecimal("0.9"),
                new BigDecimal("0.85"), new BigDecimal("0.82"));
        List<AlternativeCandidateResponse> mockResponse = List.of(
                new AlternativeCandidateResponse(candidateId, "Slim Fit Tee", "https://example.com/tee.jpg",
                        new BigDecimal("39.99"), breakdown));
        when(garmentSwapService.listAlternatives(eq(outfitId), eq(itemId), any())).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/outfits/{outfitId}/items/{itemId}/alternatives", outfitId, itemId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productId").value(candidateId.toString()))
                .andExpect(jsonPath("$[0].productDisplayName").value("Slim Fit Tee"))
                .andExpect(jsonPath("$[0].basePrice").value(39.99))
                .andExpect(jsonPath("$[0].projectedBreakdown.finalScore").value(0.82));
    }

    @Test
    void swap_missingAuthorizationHeader_returns401() throws Exception {
        String body = """
                {"productId": "%s"}
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/outfits/{outfitId}/items/{itemId}/swap",
                        UUID.randomUUID(), UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void swap_validTokenAndBody_returns200WithMappedOutfitAndDerivesUserIdFromJwtSubject() throws Exception {
        UUID outfitId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID resultOutfitId = UUID.randomUUID();
        UUID resultItemId = UUID.randomUUID();
        CompatibilityScoreBreakdown breakdown = new CompatibilityScoreBreakdown(
                new BigDecimal("0.6"), new BigDecimal("0.65"), new BigDecimal("0.7"),
                new BigDecimal("0.75"), new BigDecimal("0.68"));
        OutfitResponse mockResponse = new OutfitResponse(resultOutfitId, breakdown, new BigDecimal("104.99"),
                List.of(new OutfitItemView(resultItemId, productId, "Slim Fit Tee", "https://example.com/tee.jpg",
                        new BigDecimal("39.99"), GarmentRole.TOP)));
        when(garmentSwapService.swap(eq(outfitId), eq(itemId), eq(productId), any())).thenReturn(mockResponse);

        String body = """
                {"productId": "%s"}
                """.formatted(productId);

        mockMvc.perform(post("/api/v1/outfits/{outfitId}/items/{itemId}/swap", outfitId, itemId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outfitId").value(resultOutfitId.toString()))
                .andExpect(jsonPath("$.totalPrice").value(104.99))
                .andExpect(jsonPath("$.items[0].itemId").value(resultItemId.toString()))
                .andExpect(jsonPath("$.items[0].productId").value(productId.toString()))
                .andExpect(jsonPath("$.items[0].slot").value("TOP"));
    }
}