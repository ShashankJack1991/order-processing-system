package com.ecommerce.orderprocessing.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HomeController {

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> home() {
        return ResponseEntity.ok(Map.of(
                "application", "Order Processing System",
                "status", "running",
                "endpoints", Map.of(
                        "orders", "/api/orders",
                        "createOrder", "POST /api/orders",
                        "getOrder", "GET /api/orders/{id}",
                        "cancelOrder", "PUT /api/orders/{id}/cancel"
                )
        ));
    }
}
