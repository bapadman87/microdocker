package com.example.orderservice.service;

public enum OrderCreateStatus {
    CREATED,
    INVALID_REQUEST,
    PRODUCT_NOT_FOUND,
    PRODUCT_UNAVAILABLE,
    PRODUCT_SERVICE_UNAVAILABLE
}
