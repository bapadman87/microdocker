error id: file:///C:/Users/nandh/OneDrive/Desktop/ecomm_microservice/OrderService/src/main/java/com/example/orderservice/service/OrderService.java:_empty_/OrderStatus#CREATED#
file:///C:/Users/nandh/OneDrive/Desktop/ecomm_microservice/OrderService/src/main/java/com/example/orderservice/service/OrderService.java
empty definition using pc, found symbol in pc: _empty_/OrderStatus#CREATED#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 2343
uri: file:///C:/Users/nandh/OneDrive/Desktop/ecomm_microservice/OrderService/src/main/java/com/example/orderservice/service/OrderService.java
text:
```scala
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
 request.getCustomerName() == null || request.getCustomerName().isBlank()) {
            return new CreateOrderResult(OrderCreateStatus.INVALID_REQUEST, null);
        }

        Optional<ProductDto> productOpt = productClient.getProductById(request.getProductId().trim());
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
                OrderStatus.@@CREATED,
                Instant.now()
        );
        orders.add(order);
        return new CreateOrderResult(OrderCreateStatus.CREATED, order);
    }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: _empty_/OrderStatus#CREATED#