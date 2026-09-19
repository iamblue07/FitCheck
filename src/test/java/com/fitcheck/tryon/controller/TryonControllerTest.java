package com.fitcheck.tryon.controller;

import com.fitcheck.support.WebSliceTestConfig;
import com.fitcheck.tryon.service.TryonRequestService;
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

import static org.mockito.Mockito.verifyNoInteractions;
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
class TryonControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TryonRequestService tryonRequestService;

    @Test
    void submit_missingOutfitIdField_returns400AndNeverInvokesService() throws Exception {
        mockMvc.perform(post("/api/v1/tryon")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(tryonRequestService);
    }

    @Test
    void submit_nullOutfitIdValue_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/tryon")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outfitId\": null}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(tryonRequestService);
    }

    @Test
    void submit_malformedOutfitIdNotAUuid_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/tryon")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outfitId\": \"not-a-uuid\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(tryonRequestService);
    }

    @Test
    void submit_malformedJsonBody_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/tryon")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not json at all"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(tryonRequestService);
    }

    @Test
    void submit_missingBodyEntirely_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/tryon")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(tryonRequestService);
    }

    @Test
    void getStatus_requestIdNotAUuid_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/tryon/{requestId}", "not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(tryonRequestService);
    }
}