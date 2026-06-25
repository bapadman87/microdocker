package com.example.orderservice.service;

import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderRequest;
import com.example.orderservice.model.OrderStatus;
import com.example.orderservice.model.ProductDto;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {
    private final List<Order> orders = new ArrayList<>();
    private final ProductClient productClient;

    public OrderService(ProductClient productClient) {
        this.productClient = productClient;
    }

    public List<Order> getAllOrders() {
        return Collections.unmodifiableList(orders);
    }

    public Optional<Order> getOrderById(String orderId) {
        return orders.stream()
                .filter(order -> order.getId().equalsIgnoreCase(orderId))
                .findFirst();
    }

    public CreateOrderResult createOrder(OrderRequest request) {
        if (request == null || request.getProductId() == null || request.getProductId().isBlank() || request.getQuantity() < 1
                || request.getCustomerName() == null || request.getCustomerName().isBlank()) {
            return new CreateOrderResult(OrderCreateStatus.INVALID_REQUEST, null);
        }

        ProductLookupResult lookupResult = productClient.getProductById(request.getProductId().trim());
        if (lookupResult.getStatus() == ProductLookupStatus.SERVICE_UNAVAILABLE) {
            return new CreateOrderResult(OrderCreateStatus.PRODUCT_SERVICE_UNAVAILABLE, null);
        }

        Optional<ProductDto> productOpt = lookupResult.getProduct();
        if (productOpt.isEmpty()) {
            return new CreateOrderResult(OrderCreateStatus.PRODUCT_NOT_FOUND, null);
        }

        ProductDto product = productOpt.get();
        if (!product.isAvailable()) {
            return new CreateOrderResult(OrderCreateStatus.PRODUCT_UNAVAILABLE, null);
        }

        BigDecimal totalPrice = product.getPrice().multiply(BigDecimal.valueOf(request.getQuantity()));

        Order order = new Order(
                UUID.randomUUID().toString(),
                product.getId(),
                request.getQuantity(),
                request.getCustomerName().trim(),
                request.getShippingAddress() == null ? "" : request.getShippingAddress().trim(),
                totalPrice,
                OrderStatus.CREATED,
                Instant.now()
        );
        orders.add(order);
        return new CreateOrderResult(OrderCreateStatus.CREATED, order);
    }
}
