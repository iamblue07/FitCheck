package com.fitcheck.identity.controller;

import com.fitcheck.identity.dto.StyleTagResponse;
import com.fitcheck.identity.dto.UserProfileResponse;
import com.fitcheck.identity.enums.Sex;
import com.fitcheck.identity.service.ProfileService;
import com.fitcheck.support.WebSliceTestConfig;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProfileController.class)
@Import(WebSliceTestConfig.class)
@TestPropertySource(properties = {
        WebSliceTestConfig.JWT_SECRET_PROPERTY,
        WebSliceTestConfig.JWT_ACCESS_EXPIRATION_PROPERTY,
        WebSliceTestConfig.JWT_REFRESH_EXPIRATION_PROPERTY
})
class ProfileControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfileService profileService;

    @Test
    void getProfile_validToken_returns200WithMappedBody() throws Exception {
        UserProfileResponse mockResponse = new UserProfileResponse(
                LocalDate.of(1998, 4, 12), Sex.FEMALE, BigDecimal.valueOf(170), BigDecimal.valueOf(62),
                BigDecimal.valueOf(24.5), BigDecimal.valueOf(150), "RON",
                List.of(new StyleTagResponse(UUID.randomUUID(), "minimalist")));
        when(profileService.getProfile(any())).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/users/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("RON"))
                .andExpect(jsonPath("$.styleTags[0].name").value("minimalist"));
    }

    @Test
    void getProfile_missingAuthorizationHeader_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void updateStylePreferences_validToken_returns200WithMappedList() throws Exception {
        UUID tagId = UUID.randomUUID();
        when(profileService.updateStylePreferences(any(), any()))
                .thenReturn(List.of(new StyleTagResponse(tagId, "minimalist")));

        String body = """
                {"styleTagIds": ["%s"]}
                """.formatted(tagId);

        mockMvc.perform(put("/api/v1/users/me/style-preferences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("minimalist"));
    }
}