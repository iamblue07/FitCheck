package com.fitcheck.identity.controller;

import com.fitcheck.identity.dto.StyleTagResponse;
import com.fitcheck.identity.service.StyleTagService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StyleTagController.class)
@AutoConfigureMockMvc(addFilters = false)
class StyleTagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StyleTagService styleTagService;

    @Test
    void listAll_success_returns200WithMappedTags() throws Exception {
        when(styleTagService.listAll()).thenReturn(List.of(
                new StyleTagResponse(UUID.randomUUID(), "minimalist"),
                new StyleTagResponse(UUID.randomUUID(), "streetwear")));

        mockMvc.perform(get("/api/v1/style-tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("minimalist"));
    }
}