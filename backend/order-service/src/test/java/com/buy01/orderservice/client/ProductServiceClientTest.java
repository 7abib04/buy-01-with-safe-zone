package com.buy01.orderservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.buy01.orderservice.dto.ProductSnapshotResponse;
import com.buy01.orderservice.exception.ProductNotFoundException;
import com.buy01.orderservice.exception.RemoteServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ProductServiceClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final ProductServiceClient client = new ProductServiceClient(builder, "http://product-service", objectMapper);

    @Test
    void getProductReturnsParsedResponse() {
        server.expect(requestTo("http://product-service/products/product-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"id\":\"product-1\",\"name\":\"Phone\",\"description\":\"desc\",\"price\":100.00,"
                                + "\"quantity\":5,\"sellerId\":\"seller-1\",\"category\":\"ELECTRONICS\",\"imageUrls\":[]}",
                        MediaType.APPLICATION_JSON));

        ProductSnapshotResponse response = client.getProduct("product-1");

        assertThat(response.id()).isEqualTo("product-1");
        assertThat(response.name()).isEqualTo("Phone");
        assertThat(response.quantity()).isEqualTo(5);
    }

    @Test
    void getProductThrowsProductNotFoundOn404() {
        server.expect(requestTo("http://product-service/products/missing"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("{}").contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getProduct("missing"))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void getProductThrowsIllegalArgumentOn400WithMessage() {
        server.expect(requestTo("http://product-service/products/product-1"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"message\":\"Invalid product id\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getProduct("product-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid product id");
    }

    @Test
    void getProductThrowsIllegalArgumentWithFallbackWhenBodyIsNotJson() {
        server.expect(requestTo("http://product-service/products/product-1"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("not json").contentType(MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> client.getProduct("product-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("not json");
    }

    @Test
    void getProductThrowsRemoteServiceExceptionOnServerError() {
        server.expect(requestTo("http://product-service/products/product-1"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("boom")
                        .contentType(MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> client.getProduct("product-1"))
                .isInstanceOf(RemoteServiceException.class)
                .hasMessageContaining("HTTP 500");
    }

    @Test
    void getProductThrowsRemoteServiceExceptionWhenServiceUnreachable() {
        RestClient.Builder unreachableBuilder = RestClient.builder();
        ProductServiceClient unreachableClient =
                new ProductServiceClient(unreachableBuilder, "http://localhost:1", objectMapper);

        assertThatThrownBy(() -> unreachableClient.getProduct("product-1"))
                .isInstanceOf(RemoteServiceException.class)
                .hasMessage("Product service is unavailable");
    }

    @Test
    void adjustStockSendsPatchWithAuthorizationHeader() {
        server.expect(requestTo("http://product-service/internal/products/product-1/stock"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token"))
                .andRespond(withSuccess());

        client.adjustStock("Bearer token", "product-1", -2);

        server.verify();
    }

    @Test
    void adjustStockThrowsProductNotFoundOn404() {
        server.expect(requestTo("http://product-service/internal/products/missing/stock"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("{}").contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.adjustStock("Bearer token", "missing", 1))
                .isInstanceOf(ProductNotFoundException.class);
    }
}
