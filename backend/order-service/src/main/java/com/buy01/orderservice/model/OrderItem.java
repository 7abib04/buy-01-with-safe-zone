package com.buy01.orderservice.model;

import java.math.BigDecimal;

public class OrderItem extends AbstractLineItem {

    private String sellerName;
    private String category;
    private BigDecimal subtotal;

    public OrderItem() {
        // Required no-arg constructor for MongoDB/Jackson deserialization.
    }

    public String getSellerName() {
        return sellerName;
    }

    public void setSellerName(String sellerName) {
        this.sellerName = sellerName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }
}
