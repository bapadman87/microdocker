package com.example.productservice.service;

import com.example.productservice.model.Product;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ProductService {
    private final List<Product> products = new ArrayList<>();

    @PostConstruct
    public void init() {
        products.add(new Product("P1001", "Wireless Mouse", "Ergonomic wireless mouse with USB receiver", new BigDecimal("29.99"), true));
        products.add(new Product("P1002", "Mechanical Keyboard", "Backlit mechanical keyboard with blue switches", new BigDecimal("89.99"), true));
        products.add(new Product("P1003", "USB-C Hub", "7-port USB-C hub with HDMI and Ethernet", new BigDecimal("49.95"), false));
        products.add(new Product("P1004", "Noise Cancelling Headphones", "Over-ear active noise cancelling headphones", new BigDecimal("149.99"), true));
    }

    public List<Product> getAllProducts() {
        return Collections.unmodifiableList(products);
    }

    public Optional<Product> getProductById(String productId) {
        return products.stream()
                .filter(product -> product.getId().equalsIgnoreCase(productId))
                .findFirst();
    }

    public List<Product> searchProducts(String query) {
        if (query == null || query.isBlank()) {
            return getAllProducts();
        }

        String normalized = query.trim().toLowerCase();
        return products.stream()
                .filter(product -> product.getName().toLowerCase().contains(normalized)
                        || product.getDescription().toLowerCase().contains(normalized)
                        || product.getId().toLowerCase().contains(normalized))
                .collect(Collectors.toList());
    }
}
