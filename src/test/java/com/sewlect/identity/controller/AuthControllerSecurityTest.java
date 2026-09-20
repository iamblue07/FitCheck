package com.sewlect.identity.controller;

import com.sewlect.common.ratelimit.InMemoryRateLimiter;
import com.sewlect.common.security.filter.AuthRateLimitFilter;
import com.sewlect.common.logging.filter.CorrelationIdFilter;
import com.sewlect.identity.service.AuthService;
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

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(WebSliceTestConfig.class)
@TestPropertySource(properties = {
        WebSliceTestConfig.JWT_SECRET_PROPERTY,
        WebSliceTestConfig.JWT_ACCESS_EXPIRATION_PROPERTY,
        WebSliceTestConfig.JWT_REFRESH_EXPIRATION_PROPERTY
})
class AuthControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private InMemoryRateLimiter inMemoryRateLimiter;

    @Test
    void protectedEndpoint_missingAuthorizationHeader_returns401InErrorResponseShape() throws Exception {
        mockMvc.perform(get("/api/v1/anything"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.path").value("/api/v1/anything"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void protectedEndpoint_validToken_passesSecurityAndReaches404ForUnmappedRoute() throws Exception {
        mockMvc.perform(get("/api/v1/anything")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void protectedEndpoint_isNotSubjectToTheAuthRateLimitFilter() throws Exception {
        mockMvc.perform(get("/api/v1/anything")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken()))
                .andExpect(status().isNotFound());

        verifyNoInteractions(inMemoryRateLimiter);
    }

    @Test
    void login_ipBudgetExhausted_returns429ThroughTheRealChainBeforeReachingTheController() throws Exception {
        when(inMemoryRateLimiter.tryConsume(
                any(), eq(AuthRateLimitFilter.IP_OPERATION_KEY), anyInt(), any(Duration.class)))
                .thenReturn(false);

        String body = """
                {"email": "valid@example.com", "password": "password123"}
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/login"))
                .andExpect(jsonPath("$.correlationId").exists())
                .andExpect(header().exists(CorrelationIdFilter.HEADER_NAME));

        verifyNoInteractions(authService);
        verify(inMemoryRateLimiter, never()).tryConsume(
                any(), eq(AuthRateLimitFilter.EMAIL_OPERATION_KEY), anyInt(), any(Duration.class));
    }

    @Test
    void login_ipBudgetAvailableButEmailBudgetExhausted_returns429ThroughTheRealChain() throws Exception {
        when(inMemoryRateLimiter.tryConsume(
                any(), eq(AuthRateLimitFilter.IP_OPERATION_KEY), anyInt(), any(Duration.class)))
                .thenReturn(true);
        when(inMemoryRateLimiter.tryConsume(
                eq("valid@example.com"), eq(AuthRateLimitFilter.EMAIL_OPERATION_KEY), anyInt(), any(Duration.class)))
                .thenReturn(false);

        String body = """
                {"email": "Valid@Example.com", "password": "password123"}
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429));

        verifyNoInteractions(authService);
    }
}