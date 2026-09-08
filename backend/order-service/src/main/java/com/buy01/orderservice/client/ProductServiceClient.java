package com.buy01.orderservice.client;

import com.buy01.orderservice.dto.ProductSnapshotResponse;
import com.buy01.orderservice.dto.StockAdjustmentRequest;
import com.buy01.orderservice.exception.ProductNotFoundException;
import com.buy01.orderservice.exception.RemoteServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class ProductServiceClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public ProductServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.services.product.base-url}") String productServiceBaseUrl,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClientBuilder
                .baseUrl(productServiceBaseUrl)
                .build();
        this.objectMapper = objectMapper;
    }

    public ProductSnapshotResponse getProduct(String productId) {
        try {
            ProductSnapshotResponse response = restClient.get()
                    .uri("/products/{id}", productId)
                    .retrieve()
                    .body(ProductSnapshotResponse.class);

            if (response == null) {
                throw new ProductNotFoundException(productId);
            }
            return response;
        } catch (RestClientResponseException exception) {
            throw mapException(exception, productId);
        } catch (ResourceAccessException exception) {
            throw new RemoteServiceException("Product service is unavailable", exception);
        }
    }

    public void adjustStock(String authorizationHeader, String productId, int delta) {
        try {
            restClient.patch()
                    .uri("/internal/products/{id}/stock", productId)
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                    .body(new StockAdjustmentRequest(delta))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw mapException(exception, productId);
        } catch (ResourceAccessException exception) {
            throw new RemoteServiceException("Product service is unavailable", exception);
        }
    }

    private RuntimeException mapException(RestClientResponseException exception, String productId) {
        return switch (exception.getStatusCode().value()) {
            case 400 -> new IllegalArgumentException(extractMessage(exception, "Product validation failed"));
            case 404 -> new ProductNotFoundException(productId);
            default -> new RemoteServiceException("Product service request failed: HTTP " + exception.getStatusCode(), exception);
        };
    }

    private String extractMessage(RestClientResponseException exception, String fallback) {
        String responseBody = exception.getResponseBodyAsString();
        if (responseBody.isBlank()) {
            return fallback;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode message = root.get("message");
            if (message != null && !message.isNull() && !message.asText().isBlank()) {
                return message.asText();
            }
        } catch (Exception ignored) {
            // Not valid JSON (or no "message" field) - fall back to the raw response body below.
        }
        return responseBody;
    }
}
