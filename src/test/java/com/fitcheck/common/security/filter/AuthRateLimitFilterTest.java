package com.fitcheck.common.security.filter;

import com.fitcheck.common.exception.support.ErrorResponseFactory;
import com.fitcheck.common.ratelimit.InMemoryRateLimiter;
import com.fitcheck.common.security.properties.AuthRateLimitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthRateLimitFilterTest {

    private static final String CLIENT_IP = "198.51.100.42";
    private static final Duration WINDOW = Duration.ofMinutes(15);

    @Mock
    private InMemoryRateLimiter rateLimiter;

    private AuthRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        AuthRateLimitProperties properties = new AuthRateLimitProperties(20, 10, WINDOW);
        ErrorResponseFactory errorResponseFactory = new ErrorResponseFactory(
                Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC));
        filter = new AuthRateLimitFilter(rateLimiter, properties, errorResponseFactory, JsonMapper.builder().build());
    }

    @Test
    void ipBudgetExhausted_returns429AndNeverReachesTheChain() throws Exception {
        MockHttpServletRequest request = authRequest(AuthRateLimitFilter.REGISTER_PATH, null);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(rateLimiter.tryConsume(eq(CLIENT_IP), eq(AuthRateLimitFilter.IP_OPERATION_KEY), anyInt(), eq(WINDOW)))
                .thenReturn(false);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentAsString()).contains("\"status\":429");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void emailBudgetExhaustedOnLogin_returns429EvenThoughTheIpBudgetIsFine() throws Exception {
        MockHttpServletRequest request = authRequest(AuthRateLimitFilter.LOGIN_PATH,
                "{\"email\":\"Jane@Example.com\",\"password\":\"password123\"}");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(rateLimiter.tryConsume(eq(CLIENT_IP), eq(AuthRateLimitFilter.IP_OPERATION_KEY), anyInt(), eq(WINDOW)))
                .thenReturn(true);
        when(rateLimiter.tryConsume(eq("jane@example.com"), eq(AuthRateLimitFilter.EMAIL_OPERATION_KEY), anyInt(), eq(WINDOW)))
                .thenReturn(false);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void loginWithinBothBudgets_proceedsAndTheBodyIsStillReadableDownstream() throws Exception {
        String body = "{\"email\":\"jane@example.com\",\"password\":\"password123\"}";
        MockHttpServletRequest request = authRequest(AuthRateLimitFilter.LOGIN_PATH, body);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(rateLimiter.tryConsume(any(), any(), anyInt(), any())).thenReturn(true);

        filter.doFilterInternal(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(new String(chain.getRequest().getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo(body);
    }

    @Test
    void nonAuthPath_passesThroughWithoutConsumingAnyBudget() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/feed");
        request.setRequestURI("/api/v1/feed");
        request.setRemoteAddr(CLIENT_IP);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
        verifyNoInteractions(rateLimiter);
    }

    @Test
    void forgedXForwardedForHeader_doesNotChangeTheIpKey() throws Exception {
        MockHttpServletRequest request = authRequest(AuthRateLimitFilter.REFRESH_PATH, null);
        request.addHeader("X-Forwarded-For", "203.0.113.9");
        request.addHeader("Forwarded", "for=203.0.113.9");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(rateLimiter.tryConsume(eq(CLIENT_IP), eq(AuthRateLimitFilter.IP_OPERATION_KEY), anyInt(), eq(WINDOW)))
                .thenReturn(true);

        filter.doFilterInternal(request, response, chain);

        verify(rateLimiter).tryConsume(eq(CLIENT_IP), eq(AuthRateLimitFilter.IP_OPERATION_KEY), anyInt(), eq(WINDOW));
        verify(rateLimiter, never()).tryConsume(eq("203.0.113.9"), any(), anyInt(), any());
        assertThat(chain.getRequest()).isNotNull();
    }

    private MockHttpServletRequest authRequest(String path, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRequestURI(path);
        request.setRemoteAddr(CLIENT_IP);
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        if (body != null) {
            request.setContent(body.getBytes(StandardCharsets.UTF_8));
        }
        return request;
    }
}