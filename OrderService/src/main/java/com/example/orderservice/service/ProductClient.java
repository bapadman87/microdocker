package com.example.orderservice.service;

import com.example.orderservice.model.ProductDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class ProductClient {

    private final RestTemplate restTemplate;
    private final String productServiceUrl;

    public ProductClient(RestTemplate restTemplate,
                         @Value("${product.service.url}") String productServiceUrl) {
        this.restTemplate = restTemplate;
        this.productServiceUrl = productServiceUrl;
    }

    public ProductLookupResult getProductById(String productId) {
        try {
            ResponseEntity<ProductDto> response = restTemplate.getForEntity(
                    productServiceUrl + "/api/products/{id}", ProductDto.class, productId);
            if (response.getStatusCode().is2xxSuccessful()) {
                return new ProductLookupResult(ProductLookupStatus.FOUND, response.getBody());
            }
            if (response.getStatusCode().is4xxClientError()) {
                return new ProductLookupResult(ProductLookupStatus.NOT_FOUND, null);
            }
        } catch (HttpClientErrorException.NotFound ignored) {
            return new ProductLookupResult(ProductLookupStatus.NOT_FOUND, null);
        } catch (RestClientException ignored) {
            return new ProductLookupResult(ProductLookupStatus.SERVICE_UNAVAILABLE, null);
        }
        return new ProductLookupResult(ProductLookupStatus.SERVICE_UNAVAILABLE, null);
    }
}
