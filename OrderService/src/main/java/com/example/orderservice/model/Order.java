package com.example.orderservice.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public class Order {
    private String id;
    private String productId;
    private int quantity;
    private String customerName;
    private String shippingAddress;
    private BigDecimal totalPrice;
    private OrderStatus status;
    private Instant createdAt;

    public Order() {
    }

    public Order(String id, String productId, int quantity, String customerName, String shippingAddress,
                 BigDecimal totalPrice, OrderStatus status, Instant createdAt) {
        this.id = id;
        this.productId = productId;
        this.quantity = quantity;
        this.customerName = customerName;
        this.shippingAddress = shippingAddress;
        this.totalPrice = totalPrice;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Order)) return false;
        Order order = (Order) o;
        return quantity == order.quantity &&
                Objects.equals(id, order.id) &&
                Objects.equals(productId, order.productId) &&
                Objects.equals(customerName, order.customerName) &&
                Objects.equals(shippingAddress, order.shippingAddress) &&
                Objects.equals(totalPrice, order.totalPrice) &&
                status == order.status &&
                Objects.equals(createdAt, order.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, productId, quantity, customerName, shippingAddress, totalPrice, status, createdAt);
    }
}
