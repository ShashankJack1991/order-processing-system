package com.ecommerce.orderprocessing.service;

import com.ecommerce.orderprocessing.dto.CreateOrderRequest;
import com.ecommerce.orderprocessing.dto.OrderResponse;
import com.ecommerce.orderprocessing.dto.UpdateStatusRequest;
import com.ecommerce.orderprocessing.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {

    OrderResponse createOrder(CreateOrderRequest request);

    OrderResponse getOrderById(Long id);

    OrderResponse updateOrderStatus(Long id, UpdateStatusRequest request);

    OrderResponse cancelOrder(Long id);

    Page<OrderResponse> getAllOrders(OrderStatus status, Pageable pageable);

    void processPendingOrders();
}
