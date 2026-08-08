package com.buy01.orderservice.model;

import java.math.BigDecimal;

public class CartItem {

    private String productId;
    private String sellerId;
    private String name;
    private String imageUrl;
    private BigDecimal price;
    private int quantity;

    public CartItem() {
    }

    public CartItem(String productId, String sellerId, String name, String imageUrl, BigDecimal price, int quantity) {
        this.productId = productId;
        this.sellerId = sellerId;
        this.name = name;
        this.imageUrl = imageUrl;
        this.price = price;
        this.quantity = quantity;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getSellerId() {
        return sellerId;
    }

    public void setSellerId(String sellerId) {
        this.sellerId = sellerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
