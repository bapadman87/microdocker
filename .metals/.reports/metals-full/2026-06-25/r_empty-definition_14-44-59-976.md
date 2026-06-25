error id: file:///C:/Users/nandh/OneDrive/Desktop/ecomm_microservice/OrderService/src/test/java/com/example/orderservice/OrderControllerTest.java:_empty_/ProductLookupStatus#NOT_FOUND#
file:///C:/Users/nandh/OneDrive/Desktop/ecomm_microservice/OrderService/src/test/java/com/example/orderservice/OrderControllerTest.java
empty definition using pc, found symbol in pc: _empty_/ProductLookupStatus#NOT_FOUND#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 2362
uri: file:///C:/Users/nandh/OneDrive/Desktop/ecomm_microservice/OrderService/src/test/java/com/example/orderservice/OrderControllerTest.java
text:
```scala
package com.example.orderservice;

import com.example.orderservice.model.ProductDto;
import com.example.orderservice.service.ProductClient;
import com.example.orderservice.service.ProductLookupResult;
import com.example.orderservice.service.ProductLookupStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductClient productClient;

    @Test
    void shouldCreateOrder() throws Exception {
        when(productClient.getProductById(eq("P1001")))
                .thenReturn(new ProductLookupResult(ProductLookupStatus.FOUND,
                        new ProductDto("P1001", "Wireless Mouse", "Ergonomic wireless mouse", new BigDecimal("29.99"), true)));

        String requestBody = "{\n" +
                "  \"productId\": \"P1001\",\n" +
                "  \"quantity\": 2,\n" +
                "  \"customerName\": \"Jane Doe\",\n" +
                "  \"shippingAddress\": \"123 Main St\"\n" +
                "}";

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    void shouldReturnNotFoundForMissingProduct() throws Exception {
        when(productClient.getProductById(eq("P9999")))
                .thenReturn(new ProductLookupResult(ProductLookupStatus.@@NOT_FOUND, null));

        String requestBody = "{\n" +
                "  \"productId\": \"P9999\",\n" +
                "  \"quantity\": 1,\n" +
                "  \"customerName\": \"Jane Doe\",\n" +
                "  \"shippingAddress\": \"123 Main St\"\n" +
                "}";

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnServiceUnavailableWhenProductServiceIsDown() throws Exception {
        when(productClient.getProductById(eq("P1001")))
                .thenReturn(new ProductLookupResult(ProductLookupStatus.SERVICE_UNAVAILABLE, null));

        String requestBody = "{\n" +
                "  \"productId\": \"P1001\",\n" +
                "  \"quantity\": 2,\n" +
                "  \"customerName\": \"Jane Doe\",\n" +
                "  \"shippingAddress\": \"123 Main St\"\n" +
                "}";

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isServiceUnavailable());
    }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: _empty_/ProductLookupStatus#NOT_FOUND#