package com.buy01.orderservice.model;

import java.math.BigDecimal;

public class CartItem extends AbstractLineItem {

    public CartItem() {
    }

    public CartItem(String productId, String sellerId, String name, String imageUrl, BigDecimal price, int quantity) {
        super(productId, sellerId, name, imageUrl, price, quantity);
    }
}
