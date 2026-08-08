package com.buy01.productservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.buy01.productservice.dto.ProductSearchResponse;
import com.buy01.productservice.model.Category;
import com.buy01.productservice.model.Product;
import com.buy01.productservice.repository.ProductRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@ExtendWith(MockitoExtension.class)
class ProductSearchServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductSearchService productSearchService;

    private Product product(String id, String name, BigDecimal price, Category category) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setDescription("desc");
        product.setPrice(price);
        product.setQuantity(5);
        product.setSellerId("seller-1");
        product.setCategory(category);
        product.setImageUrls(List.of());
        product.setCreatedAt(Instant.now());
        product.setUpdatedAt(Instant.now());
        return product;
    }

    @Test
    void searchReturnsPagedResultsAndFacets() {
        Product phone = product("product-1", "Phone", new BigDecimal("699.99"), Category.ELECTRONICS);

        when(mongoTemplate.count(any(Query.class), org.mockito.ArgumentMatchers.eq(Product.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), org.mockito.ArgumentMatchers.eq(Product.class))).thenReturn(List.of(phone));
        when(productRepository.findAll()).thenReturn(List.of(
                phone,
                product("product-2", "Case", new BigDecimal("9.99"), Category.ELECTRONICS)
        ));

        ProductSearchResponse response = productSearchService.search(
                "phone", Category.ELECTRONICS, null, null, "price_desc", 0, 20
        );

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).id()).isEqualTo("product-1");
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.categories()).contains("ELECTRONICS", "OTHER");
        assertThat(response.minPrice()).isEqualByComparingTo("9.99");
        assertThat(response.maxPrice()).isEqualByComparingTo("699.99");
    }
}
