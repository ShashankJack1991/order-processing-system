package com.ecommerce.orderprocessing.exception;

import com.ecommerce.orderprocessing.entity.OrderStatus;

public class OrderCancellationException extends RuntimeException {

    public OrderCancellationException(Long orderId, OrderStatus currentStatus) {
        super("Order " + orderId + " cannot be cancelled. Current status: " + currentStatus
                + ". Only PENDING orders can be cancelled.");
    }
}
