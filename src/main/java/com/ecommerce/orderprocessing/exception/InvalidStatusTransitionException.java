package com.ecommerce.orderprocessing.exception;

import com.ecommerce.orderprocessing.entity.OrderStatus;

public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(OrderStatus from, OrderStatus to) {
        super("Invalid status transition from " + from + " to " + to);
    }
}
