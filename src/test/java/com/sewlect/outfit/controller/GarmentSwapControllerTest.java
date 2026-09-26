package com.sewlect.outfit.controller;

import com.sewlect.outfit.service.GarmentSwapService;
import com.sewlect.support.WebSliceTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GarmentSwapController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(WebSliceTestConfig.class)
@TestPropertySource(properties = {
        WebSliceTestConfig.JWT_SECRET_PROPERTY,
        WebSliceTestConfig.JWT_ACCESS_EXPIRATION_PROPERTY,
        WebSliceTestConfig.JWT_REFRESH_EXPIRATION_PROPERTY,
        WebSliceTestConfig.SUBJECT_HASH_SECRET_PROPERTY
})
class GarmentSwapControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GarmentSwapService garmentSwapService;

    @Test
    void swap_missingProductIdField_returns400AndNeverInvokesService() throws Exception {
        UUID outfitId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        String body = """
                {}
                """;

        mockMvc.perform(post("/api/v1/outfits/{outfitId}/items/{itemId}/swap", outfitId, itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(garmentSwapService);
    }

    @Test
    void swap_explicitNullProductId_returns400AndNeverInvokesService() throws Exception {
        UUID outfitId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        String body = """
                {"productId": null}
                """;

        mockMvc.perform(post("/api/v1/outfits/{outfitId}/items/{itemId}/swap", outfitId, itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(garmentSwapService);
    }

    @Test
    void swap_malformedProductIdValue_returns400() throws Exception {
        UUID outfitId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        String body = """
                {"productId": "not-a-uuid"}
                """;

        mockMvc.perform(post("/api/v1/outfits/{outfitId}/items/{itemId}/swap", outfitId, itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(garmentSwapService);
    }
}