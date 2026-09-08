package com.buy01.orderservice.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.JwtException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Mock
    private MethodArgumentNotValidException validationException;

    @Mock
    private BindingResult bindingResult;

    @Test
    void handlesValidationErrorsWithFieldDetails() {
        when(validationException.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(new FieldError("request", "quantity", "must be positive")));

        ResponseEntity<ApiErrorResponse> response = handler.handleValidation(validationException);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Validation failed");
        assertThat(response.getBody().details()).containsExactly("quantity: must be positive");
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    void handlesNotFoundExceptions() {
        ResponseEntity<ApiErrorResponse> response = handler.handleNotFound(new OrderNotFoundException("order-1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().message()).isEqualTo("Order not found: order-1");
    }

    @Test
    void handlesProductNotFoundException() {
        ResponseEntity<ApiErrorResponse> response = handler.handleNotFound(new ProductNotFoundException("product-1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().message()).isEqualTo("Product not found: product-1");
    }

    @Test
    void handlesInvalidOrderStatusTransitionAsConflict() {
        ResponseEntity<ApiErrorResponse> response =
                handler.handleConflictOrBadRequest(new InvalidOrderStatusTransitionException("bad transition"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message()).isEqualTo("bad transition");
    }

    @Test
    void handlesCartEmptyAsBadRequest() {
        ResponseEntity<ApiErrorResponse> response = handler.handleConflictOrBadRequest(new CartEmptyException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handlesInsufficientStockAsBadRequest() {
        ResponseEntity<ApiErrorResponse> response =
                handler.handleConflictOrBadRequest(new InsufficientStockException("Phone"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handlesJwtExceptionAsUnauthorized() {
        ResponseEntity<ApiErrorResponse> response = handler.handleJwt(new JwtException("expired"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().message()).isEqualTo("Invalid or expired token");
    }

    @Test
    void handlesAccessDeniedAsForbidden() {
        ResponseEntity<ApiErrorResponse> response = handler.handleForbidden(new AccessDeniedException("nope"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().message()).isEqualTo("Forbidden");
    }

    @Test
    void handlesRemoteServiceExceptionAsBadGateway() {
        ResponseEntity<ApiErrorResponse> response =
                handler.handleRemoteService(new RemoteServiceException("Product service is unavailable", new RuntimeException("boom")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().message()).isEqualTo("Product service is unavailable");
    }
}
