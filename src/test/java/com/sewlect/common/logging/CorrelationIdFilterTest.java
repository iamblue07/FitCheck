package com.sewlect.common.logging.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorrelationIdFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        // Safety net: if an assertion fails before the filter's own finally block runs,
        // don't let a stray MDC value leak into whichever test runs next on this thread.
        MDC.remove(CorrelationIdFilter.MDC_KEY);
    }

    @Test
    void noIncomingHeader_generatesNewCorrelationId() throws Exception {
        AtomicReference<String> observedDuringRequest = new AtomicReference<>();
        doAnswer(invocation -> {
            observedDuringRequest.set(MDC.get(CorrelationIdFilter.MDC_KEY));
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(observedDuringRequest.get()).isNotNull();
        assertThatCode(() -> UUID.fromString(observedDuringRequest.get())).doesNotThrowAnyException();
    }

    @Test
    void incomingHeaderPresent_reusesProvidedIdInsteadOfGeneratingNew() throws Exception {
        String providedId = UUID.randomUUID().toString();
        when(request.getHeader(CorrelationIdFilter.HEADER_NAME)).thenReturn(providedId);

        AtomicReference<String> observedDuringRequest = new AtomicReference<>();
        doAnswer(invocation -> {
            observedDuringRequest.set(MDC.get(CorrelationIdFilter.MDC_KEY));
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(observedDuringRequest.get()).isEqualTo(providedId);
    }

    @Test
    void afterRequestCompletes_mdcIsCleared() throws Exception {
        filter.doFilterInternal(request, response, filterChain);

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void incomingHeaderMalformed_generatesNewCorrelationId() throws Exception {
        when(request.getHeader(CorrelationIdFilter.HEADER_NAME)).thenReturn("not-a-uuid");

        AtomicReference<String> observedDuringRequest = new AtomicReference<>();
        doAnswer(invocation -> {
            observedDuringRequest.set(MDC.get(CorrelationIdFilter.MDC_KEY));
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(observedDuringRequest.get()).isNotEqualTo("not-a-uuid");
        assertThatCode(() -> UUID.fromString(observedDuringRequest.get())).doesNotThrowAnyException();
    }

    @Test
    void responseCarriesCorrelationIdHeaderMatchingTheMdcValue() throws Exception {
        AtomicReference<String> observedDuringRequest = new AtomicReference<>();
        doAnswer(invocation -> {
            observedDuringRequest.set(MDC.get(CorrelationIdFilter.MDC_KEY));
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        ArgumentCaptor<String> headerValueCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).setHeader(org.mockito.ArgumentMatchers.eq(CorrelationIdFilter.HEADER_NAME),
                headerValueCaptor.capture());
        assertThat(headerValueCaptor.getValue()).isEqualTo(observedDuringRequest.get());
        assertThatCode(() -> UUID.fromString(headerValueCaptor.getValue())).doesNotThrowAnyException();
    }

    @Test
    void responseHeaderIsSetBeforeTheChainRunsSoItCannotBeLostToACommittedResponse() throws Exception {
        filter.doFilterInternal(request, response, filterChain);

        InOrder ordered = inOrder(response, filterChain);
        ordered.verify(response).setHeader(org.mockito.ArgumentMatchers.eq(CorrelationIdFilter.HEADER_NAME),
                org.mockito.ArgumentMatchers.anyString());
        ordered.verify(filterChain).doFilter(request, response);
    }

    @Test
    void incomingHeaderMalformed_responseHeaderCarriesGeneratedIdNotTheEchoedInput() throws Exception {
        String injectionAttempt = "abc] INFO fake.log.line [";
        when(request.getHeader(CorrelationIdFilter.HEADER_NAME)).thenReturn(injectionAttempt);

        filter.doFilterInternal(request, response, filterChain);

        ArgumentCaptor<String> headerValueCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).setHeader(org.mockito.ArgumentMatchers.eq(CorrelationIdFilter.HEADER_NAME),
                headerValueCaptor.capture());
        assertThat(headerValueCaptor.getValue()).isNotEqualTo(injectionAttempt);
        assertThatCode(() -> UUID.fromString(headerValueCaptor.getValue())).doesNotThrowAnyException();
    }
}