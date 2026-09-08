package com.buy01.productservice.service;

import com.buy01.productservice.dto.ProductResponse;
import com.buy01.productservice.dto.ProductSearchResponse;
import com.buy01.productservice.model.Category;
import com.buy01.productservice.model.Product;
import com.buy01.productservice.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Service
public class ProductSearchService {

    private static final String FIELD_PRICE = "price";

    private final MongoTemplate mongoTemplate;
    private final ProductRepository productRepository;

    public ProductSearchService(MongoTemplate mongoTemplate, ProductRepository productRepository) {
        this.mongoTemplate = mongoTemplate;
        this.productRepository = productRepository;
    }

    public ProductSearchResponse search(
            String q, Category category, BigDecimal minPrice, BigDecimal maxPrice, String sort, int page, int size
    ) {
        Query query = buildQuery(q, category, minPrice, maxPrice);
        long totalElements = mongoTemplate.count(query, Product.class);

        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : size;
        int totalPages = (int) Math.ceil((double) totalElements / safeSize);

        query.with(resolveSort(sort));
        query.with(PageRequest.of(safePage, safeSize));

        List<ProductResponse> content = mongoTemplate.find(query, Product.class).stream()
                .map(this::mapToResponse)
                .toList();

        List<BigDecimal> allPrices = productRepository.findAll().stream()
                .map(Product::getPrice)
                .filter(Objects::nonNull)
                .toList();
        BigDecimal minPriceObserved = allPrices.stream().min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        BigDecimal maxPriceObserved = allPrices.stream().max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);

        return new ProductSearchResponse(
                content, safePage, safeSize, totalElements, totalPages,
                availableCategories(), minPriceObserved, maxPriceObserved
        );
    }

    private Query buildQuery(String q, Category category, BigDecimal minPrice, BigDecimal maxPrice) {
        List<Criteria> criteria = new ArrayList<>();

        if (q != null && !q.isBlank()) {
            String pattern = Pattern.quote(q.trim());
            criteria.add(new Criteria().orOperator(
                    Criteria.where("name").regex(pattern, "i"),
                    Criteria.where("description").regex(pattern, "i")
            ));
        }
        if (category != null) {
            criteria.add(Criteria.where("category").is(category));
        }
        if (minPrice != null) {
            criteria.add(Criteria.where(FIELD_PRICE).gte(minPrice));
        }
        if (maxPrice != null) {
            criteria.add(Criteria.where(FIELD_PRICE).lte(maxPrice));
        }

        Query query = new Query();
        if (!criteria.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
        }
        return query;
    }

    private Sort resolveSort(String sort) {
        return switch (sort == null ? "newest" : sort) {
            case "price_asc" -> Sort.by(Sort.Direction.ASC, FIELD_PRICE);
            case "price_desc" -> Sort.by(Sort.Direction.DESC, FIELD_PRICE);
            case "oldest" -> Sort.by(Sort.Direction.ASC, "createdAt");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }

    private List<String> availableCategories() {
        return Arrays.stream(Category.values()).map(Enum::name).toList();
    }

    private ProductResponse mapToResponse(Product product) {
        List<String> imageUrls = product.getImageUrls() == null ? List.of() : List.copyOf(product.getImageUrls());
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getQuantity(),
                product.getSellerId(),
                product.getCategory(),
                imageUrls,
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
