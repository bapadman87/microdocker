error id: file:///C:/Users/nandh/OneDrive/Desktop/ecomm_microservice/OrderService/src/test/java/com/example/orderservice/OrderControllerTest.java:com/example/orderservice/service/ProductClient#
file:///C:/Users/nandh/OneDrive/Desktop/ecomm_microservice/OrderService/src/test/java/com/example/orderservice/OrderControllerTest.java
empty definition using pc, found symbol in pc: com/example/orderservice/service/ProductClient#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 128
uri: file:///C:/Users/nandh/OneDrive/Desktop/ecomm_microservice/OrderService/src/test/java/com/example/orderservice/OrderControllerTest.java
text:
```scala
package com.example.orderservice;

import com.example.orderservice.model.ProductDto;
import com.example.orderservice.service.@@ProductClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;

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
                .thenReturn(Optional.of(new ProductDto("P1001", "Wireless Mouse", "Ergonomic wireless mouse", new BigDecimal("29.99"), true)));

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
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: com/example/orderservice/service/ProductClient#