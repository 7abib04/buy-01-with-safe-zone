package com.buy01.orderservice.model;

import java.time.Instant;

public class StatusChange {

    private OrderStatus status;
    private Instant changedAt;
    private String changedBy;

    public StatusChange() {
    }

    public StatusChange(OrderStatus status, Instant changedAt, String changedBy) {
        this.status = status;
        this.changedAt = changedAt;
        this.changedBy = changedBy;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(Instant changedAt) {
        this.changedAt = changedAt;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }
}
