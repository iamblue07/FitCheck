package com.sewlect.catalog.controller;

import com.sewlect.catalog.entity.Product;
import com.sewlect.catalog.service.CatalogEnrichmentService;
import com.sewlect.support.WebSliceTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminCatalogController.class)
@Import(WebSliceTestConfig.class)
@TestPropertySource(properties = {
        WebSliceTestConfig.JWT_SECRET_PROPERTY,
        WebSliceTestConfig.JWT_ACCESS_EXPIRATION_PROPERTY,
        WebSliceTestConfig.JWT_REFRESH_EXPIRATION_PROPERTY,
        "spring.ai.model.chat=ollama"
})
class AdminCatalogControllerTest {

    private static final String ENRICH_NEXT_PATH = "/api/v1/admin/catalog/enrich-next";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatalogEnrichmentService catalogEnrichmentService;

    @Test
    void enrichNext_success_returns200WithEnrichedTrue() throws Exception {
        UUID productId = UUID.randomUUID();
        Product product = Product.builder().id(productId).productDisplayName("Blue Cotton Shirt").build();
        when(catalogEnrichmentService.enrichNext()).thenReturn(Optional.of(product));

        mockMvc.perform(post(ENRICH_NEXT_PATH)
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + WebSliceTestConfig.accessToken(UUID.randomUUID(), "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enriched").value(true))
                .andExpect(jsonPath("$.productId").value(productId.toString()))
                .andExpect(jsonPath("$.productDisplayName").value("Blue Cotton Shirt"));
    }

    @Test
    void enrichNext_nothingLeftToEnrich_returns200WithEnrichedFalse() throws Exception {
        when(catalogEnrichmentService.enrichNext()).thenReturn(Optional.empty());

        mockMvc.perform(post(ENRICH_NEXT_PATH)
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + WebSliceTestConfig.accessToken(UUID.randomUUID(), "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enriched").value(false))
                .andExpect(jsonPath("$.productId").doesNotExist())
                .andExpect(jsonPath("$.productDisplayName").doesNotExist());
    }

    @Test
    void enrichNext_userRoleToken_returns403AndNeverReachesTheService() throws Exception {
        mockMvc.perform(post(ENRICH_NEXT_PATH)
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + WebSliceTestConfig.accessToken(UUID.randomUUID(), "USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(catalogEnrichmentService);
    }

    @Test
    void enrichNext_missingToken_returns401() throws Exception {
        mockMvc.perform(post(ENRICH_NEXT_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void enrichNext_oldUnversionedAdminPath_isNoLongerMapped() throws Exception {
        mockMvc.perform(post("/admin/catalog/enrich-next")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + WebSliceTestConfig.accessToken(UUID.randomUUID(), "ADMIN")))
                .andExpect(status().isNotFound());

        verifyNoInteractions(catalogEnrichmentService);
    }
}