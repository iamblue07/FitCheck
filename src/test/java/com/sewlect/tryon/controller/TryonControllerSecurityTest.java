package com.sewlect.tryon.controller;

import com.sewlect.common.exception.ResourceNotFoundException;
import com.sewlect.support.WebSliceTestConfig;
import com.sewlect.tryon.dto.TryonStatusResponse;
import com.sewlect.tryon.enums.TryonRequestStatus;
import com.sewlect.tryon.service.TryonRequestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TryonController.class)
@Import(WebSliceTestConfig.class)
@TestPropertySource(properties = {
        WebSliceTestConfig.JWT_SECRET_PROPERTY,
        WebSliceTestConfig.JWT_ACCESS_EXPIRATION_PROPERTY,
        WebSliceTestConfig.JWT_REFRESH_EXPIRATION_PROPERTY
})
class TryonControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TryonRequestService tryonRequestService;

    @Test
    void submit_missingAuthorizationHeader_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/tryon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outfitId\": \"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void submit_validToken_returns202WithPendingStatusBody() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID outfitId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        when(tryonRequestService.submit(any(), any()))
                .thenReturn(new TryonStatusResponse(requestId, TryonRequestStatus.PENDING, null, null));

        mockMvc.perform(post("/api/v1/tryon")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outfitId\": \"" + outfitId + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(requestId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.resultImageUrl").doesNotExist())
                .andExpect(jsonPath("$.errorMessage").doesNotExist());
    }

    @Test
    void submit_validToken_extractsUserIdFromJwtSubjectAndOutfitIdFromBody() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID outfitId = UUID.randomUUID();
        when(tryonRequestService.submit(any(), any()))
                .thenReturn(new TryonStatusResponse(UUID.randomUUID(), TryonRequestStatus.PENDING, null, null));

        mockMvc.perform(post("/api/v1/tryon")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"outfitId\": \"" + outfitId + "\"}"));

        verify(tryonRequestService).submit(eq(userId), eq(outfitId));
    }

    @Test
    void submit_inFlightRequestReused_returns202WithProcessingStatus() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID outfitId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        when(tryonRequestService.submit(any(), any()))
                .thenReturn(new TryonStatusResponse(requestId, TryonRequestStatus.PROCESSING, null, null));

        mockMvc.perform(post("/api/v1/tryon")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outfitId\": \"" + outfitId + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(requestId.toString()))
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    void getStatus_missingAuthorizationHeader_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/tryon/{requestId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getStatus_validToken_returns200WithCompleteStatusAndResultUrl() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        when(tryonRequestService.getStatus(any(), eq(requestId))).thenReturn(new TryonStatusResponse(
                requestId, TryonRequestStatus.COMPLETE, "https://r2.example.com/result-presigned", null));

        mockMvc.perform(get("/api/v1/tryon/{requestId}", requestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETE"))
                .andExpect(jsonPath("$.resultImageUrl").value("https://r2.example.com/result-presigned"));
    }

    @Test
    void getStatus_validToken_extractsUserIdFromJwtSubject() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        when(tryonRequestService.getStatus(any(), eq(requestId))).thenReturn(
                new TryonStatusResponse(requestId, TryonRequestStatus.PENDING, null, null));

        mockMvc.perform(get("/api/v1/tryon/{requestId}", requestId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken(userId)));

        verify(tryonRequestService).getStatus(eq(userId), eq(requestId));
    }

    @Test
    void getStatus_serviceThrowsResourceNotFoundForNonOwnerOrMissingRequest_returns404() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        when(tryonRequestService.getStatus(any(), eq(requestId)))
                .thenThrow(new ResourceNotFoundException("Tryon request not found: " + requestId));

        mockMvc.perform(get("/api/v1/tryon/{requestId}", requestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken(userId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getStatus_failedRequest_returns200WithErrorMessageAndNullResultUrl() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        when(tryonRequestService.getStatus(any(), eq(requestId))).thenReturn(new TryonStatusResponse(
                requestId, TryonRequestStatus.FAILED, null, "FASHN try-on step failed permanently: bad image"));

        mockMvc.perform(get("/api/v1/tryon/{requestId}", requestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorMessage").value("FASHN try-on step failed permanently: bad image"))
                .andExpect(jsonPath("$.resultImageUrl").doesNotExist());
    }
}