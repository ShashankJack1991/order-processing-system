package com.ecommerce.orderprocessing.service.impl;

import com.ecommerce.orderprocessing.dto.*;
import com.ecommerce.orderprocessing.entity.Order;
import com.ecommerce.orderprocessing.entity.OrderItem;
import com.ecommerce.orderprocessing.entity.OrderStatus;
import com.ecommerce.orderprocessing.exception.InvalidStatusTransitionException;
import com.ecommerce.orderprocessing.exception.OrderCancellationException;
import com.ecommerce.orderprocessing.exception.OrderNotFoundException;
import com.ecommerce.orderprocessing.repository.OrderRepository;
import com.ecommerce.orderprocessing.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;

    // Valid transitions map: current status -> allowed next statuses
    private static final Map<OrderStatus, Set<OrderStatus>> VALID_TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        VALID_TRANSITIONS.put(OrderStatus.PENDING, Set.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED));
        VALID_TRANSITIONS.put(OrderStatus.PROCESSING, Set.of(OrderStatus.SHIPPED));
        VALID_TRANSITIONS.put(OrderStatus.SHIPPED, Set.of(OrderStatus.DELIVERED));
        VALID_TRANSITIONS.put(OrderStatus.DELIVERED, Set.of());
        VALID_TRANSITIONS.put(OrderStatus.CANCELLED, Set.of());
    }

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        log.debug("Creating order for customerId: {}", request.getCustomerId());

        Order order = Order.builder()
                .customerId(request.getCustomerId())
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemRequest itemReq : request.getItems()) {
            OrderItem item = OrderItem.builder()
                    .productName(itemReq.getProductName())
                    .quantity(itemReq.getQuantity())
                    .price(itemReq.getPrice())
                    .build();
            order.addItem(item);
            total = total.add(itemReq.getPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity())));
        }
        order.setTotalAmount(total);

        Order saved = orderRepository.save(order);
        log.info("Order created with id: {}", saved.getId());
        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        return mapToResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse updateOrderStatus(Long id, UpdateStatusRequest request) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));

        validateTransition(order.getStatus(), request.getStatus());
        order.setStatus(request.getStatus());
        Order updated = orderRepository.save(order);
        log.info("Order {} status updated: {} -> {}", id, order.getStatus(), request.getStatus());
        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new OrderCancellationException(id, order.getStatus());
        }
        order.setStatus(OrderStatus.CANCELLED);
        Order cancelled = orderRepository.save(order);
        log.info("Order {} cancelled", id);
        return mapToResponse(cancelled);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrders(OrderStatus status, Pageable pageable) {
        Page<Order> page = (status != null)
                ? orderRepository.findByStatus(status, pageable)
                : orderRepository.findAll(pageable);
        return page.map(this::mapToResponse);
    }

    @Override
    @Transactional
    public void processPendingOrders() {
        List<Order> pendingOrders = orderRepository.findByStatus(OrderStatus.PENDING);
        if (pendingOrders.isEmpty()) {
            log.debug("No PENDING orders to process");
            return;
        }
        pendingOrders.forEach(order -> order.setStatus(OrderStatus.PROCESSING));
        orderRepository.saveAll(pendingOrders);
        log.info("Scheduler: {} PENDING orders moved to PROCESSING", pendingOrders.size());
    }

    private void validateTransition(OrderStatus current, OrderStatus next) {
        Set<OrderStatus> allowed = VALID_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(next)) {
            throw new InvalidStatusTransitionException(current, next);
        }
    }

    private OrderResponse mapToResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(item -> OrderItemResponse.builder()
                        .id(item.getId())
                        .productName(item.getProductName())
                        .quantity(item.getQuantity())
                        .price(item.getPrice())
                        .build())
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .orderId(order.getId())
                .customerId(order.getCustomerId())
                .items(itemResponses)
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
