package com.example.orderservice.service;

import com.example.orderservice.model.ProductDto;

import java.util.Optional;

public class ProductLookupResult {
    private final ProductLookupStatus status;
    private final ProductDto product;

    public ProductLookupResult(ProductLookupStatus status, ProductDto product) {
        this.status = status;
        this.product = product;
    }

    public ProductLookupStatus getStatus() {
        return status;
    }

    public Optional<ProductDto> getProduct() {
        return Optional.ofNullable(product);
    }
}
