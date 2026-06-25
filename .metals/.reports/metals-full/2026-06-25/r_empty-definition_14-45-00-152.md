error id: file:///C:/Users/nandh/OneDrive/Desktop/ecomm_microservice/OrderService/src/main/java/com/example/orderservice/controller/OrderController.java:com/example/orderservice/service/OrderCreateStatus#
file:///C:/Users/nandh/OneDrive/Desktop/ecomm_microservice/OrderService/src/main/java/com/example/orderservice/controller/OrderController.java
empty definition using pc, found symbol in pc: com/example/orderservice/service/OrderCreateStatus#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 302
uri: file:///C:/Users/nandh/OneDrive/Desktop/ecomm_microservice/OrderService/src/main/java/com/example/orderservice/controller/OrderController.java
text:
```scala
package com.example.orderservice.controller;

import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderRequest;
import com.example.orderservice.service.CreateOrderResult;
import com.example.orderservice.service.OrderService;
import com.example.orderservice.service.@@OrderCreateStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public List<Order> getAllOrders() {
        return orderService.getAllOrders();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderById(@PathVariable("id") String id) {
        return orderService.getOrderById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Object> createOrder(@RequestBody OrderRequest orderRequest) {
        CreateOrderResult result = orderService.createOrder(orderRequest);
        return switch (result.getStatus()) {
            case CREATED -> ResponseEntity.status(201).body(result.getOrder().orElseThrow());
            case INVALID_REQUEST -> ResponseEntity.badRequest().body(errorResponse("Invalid order request."));
            case PRODUCT_NOT_FOUND -> ResponseEntity.status(404).body(errorResponse("Product not found."));
            case PRODUCT_UNAVAILABLE -> ResponseEntity.unprocessableEntity().body(errorResponse("Product is not available."));
            case PRODUCT_SERVICE_UNAVAILABLE -> ResponseEntity.status(503).body(errorResponse("Product service is unavailable."));
        };
    }

    private ErrorResponse errorResponse(String message) {
        return new ErrorResponse(message);
    }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: com/example/orderservice/service/OrderCreateStatus#