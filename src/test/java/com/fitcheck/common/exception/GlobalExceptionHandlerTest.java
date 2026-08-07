package com.fitcheck.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private static final String REQUEST_URI = "/api/v1/test";

    @Mock
    private HttpServletRequest request;

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleAppException_resourceNotFound_returns404WithErrorResponseShape() {
        when(request.getRequestURI()).thenReturn(REQUEST_URI);
        ResourceNotFoundException ex = new ResourceNotFoundException("User not found");

        ResponseEntity<ErrorResponse> response = handler.handleAppException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(404);
        assertThat(body.error()).isEqualTo("Not Found");
        assertThat(body.message()).isEqualTo("User not found");
        assertThat(body.path()).isEqualTo(REQUEST_URI);
        assertThat(body.timestamp()).isNotNull();
    }

    @Test
    void handleAppException_badRequest_returns400() {
        when(request.getRequestURI()).thenReturn(REQUEST_URI);
        BadRequestException ex = new BadRequestException("Malformed input");

        ResponseEntity<ErrorResponse> response = handler.handleAppException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().status()).isEqualTo(400);
    }

    @Test
    void handleAppException_externalService_returns502() {
        when(request.getRequestURI()).thenReturn(REQUEST_URI);
        ExternalServiceException ex = new ExternalServiceException("R2 unreachable");

        ResponseEntity<ErrorResponse> response = handler.handleAppException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().status()).isEqualTo(502);
    }

    @Test
    void handleAppException_multipleSubtypes_allHandledByOneMethodReadingStatusFromInstance() {
        when(request.getRequestURI()).thenReturn(REQUEST_URI);

        AppException[] exceptions = {
                new ResourceNotFoundException("not found"),
                new BadRequestException("bad request"),
                new ConflictException("conflict"),
                new UnauthorizedException("unauthorized"),
                new ExternalServiceException("external")
        };

        for (AppException ex : exceptions) {
            ResponseEntity<ErrorResponse> response = handler.handleAppException(ex, request);
            assertThat(response.getStatusCode()).isEqualTo(ex.getStatus());
            assertThat(response.getBody().status()).isEqualTo(ex.getStatus().value());
        }
    }

    @Test
    void handleValidationException_returns400WithFieldErrors() throws NoSuchMethodException {
        when(request.getRequestURI()).thenReturn(REQUEST_URI);

        Method dummyMethod = getClass().getDeclaredMethod("dummyValidationTarget", String.class);
        MethodParameter methodParameter = new MethodParameter(dummyMethod, 0);
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "registerRequest");
        bindingResult.addError(new FieldError("registerRequest", "email", "must be a well-formed email address"));
        bindingResult.addError(new FieldError("registerRequest", "password", "size must be between 8 and 2147483647"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParameter, bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message())
                .isEqualTo("email: must be a well-formed email address, password: size must be between 8 and 2147483647");
    }

    @Test
    void handleGenericException_returns500WithGenericMessage_doesNotLeakStackTraceOrClassName() {
        when(request.getRequestURI()).thenReturn(REQUEST_URI);
        RuntimeException ex = new NullPointerException("some internal field was null, deep in a service");

        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ErrorResponse body = response.getBody();
        assertThat(body.message()).isEqualTo("Intercepted an unexpected error!");
        assertThat(body.message()).doesNotContain("NullPointerException");
        assertThat(body.message()).doesNotContain("some internal field was null");
    }

    @Test
    void handleNoResourceFound_returns404WithErrorResponseShape() {
        when(request.getRequestURI()).thenReturn(REQUEST_URI);
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, REQUEST_URI, REQUEST_URI);

        ResponseEntity<ErrorResponse> response = handler.handleNoResourceFound(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().path()).isEqualTo(REQUEST_URI);
    }

    @SuppressWarnings("unused")
    private void dummyValidationTarget(String param) {
        // Exists only to give MethodParameter a real Method + parameter index to reflect on.
    }
}