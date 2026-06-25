package com.example.orderservice.service;

import com.example.orderservice.model.Order;

import java.util.Optional;

public class CreateOrderResult {
    private final OrderCreateStatus status;
    private final Order order;

    public CreateOrderResult(OrderCreateStatus status, Order order) {
        this.status = status;
        this.order = order;
    }

    public OrderCreateStatus getStatus() {
        return status;
    }

    public Optional<Order> getOrder() {
        return Optional.ofNullable(order);
    }
}
