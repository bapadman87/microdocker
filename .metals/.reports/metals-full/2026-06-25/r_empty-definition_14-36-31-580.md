error id: file:///C:/Users/nandh/OneDrive/Desktop/ecomm_microservice/src/main/java/com/example/productservice/controller/ProductController.java:com/example/productservice/service/ProductService#
file:///C:/Users/nandh/OneDrive/Desktop/ecomm_microservice/src/main/java/com/example/productservice/controller/ProductController.java
empty definition using pc, found symbol in pc: com/example/productservice/service/ProductService#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 142
uri: file:///C:/Users/nandh/OneDrive/Desktop/ecomm_microservice/src/main/java/com/example/productservice/controller/ProductController.java
text:
```scala
package com.example.productservice.controller;

import com.example.productservice.model.Product;
import com.example.productservice.service.@@ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public List<Product> getAllProducts(@RequestParam(value = "query", required = false) String query) {
        return productService.searchProducts(query);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getProductById(@PathVariable("id") String id) {
        return productService.getProductById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: com/example/productservice/service/ProductService#