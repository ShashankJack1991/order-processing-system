package com.ecommerce.orderprocessing.service;

import com.ecommerce.orderprocessing.dto.*;
import com.ecommerce.orderprocessing.entity.Order;
import com.ecommerce.orderprocessing.entity.OrderItem;
import com.ecommerce.orderprocessing.entity.OrderStatus;
import com.ecommerce.orderprocessing.exception.InvalidStatusTransitionException;
import com.ecommerce.orderprocessing.exception.OrderCancellationException;
import com.ecommerce.orderprocessing.exception.OrderNotFoundException;
import com.ecommerce.orderprocessing.repository.OrderRepository;
import com.ecommerce.orderprocessing.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Order sampleOrder;
    private OrderItem sampleItem;

    @BeforeEach
    void setUp() {
        sampleOrder = Order.builder()
                .id(1L)
                .customerId(1001L)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("72000.00"))
                .createdAt(LocalDateTime.now())
                .items(new ArrayList<>())
                .build();

        sampleItem = OrderItem.builder()
                .id(1L)
                .productName("Laptop")
                .quantity(1)
                .price(new BigDecimal("70000.00"))
                .order(sampleOrder)
                .build();

        sampleOrder.getItems().add(sampleItem);
    }

    // ---- createOrder ----

    @Test
    void createOrder_shouldCalculateTotalAndSaveWithPendingStatus() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId(1001L)
                .items(List.of(
                        new OrderItemRequest("Laptop", 1, new BigDecimal("70000")),
                        new OrderItemRequest("Mouse", 2, new BigDecimal("1000"))
                ))
                .build();

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o = Order.builder()
                    .id(1L)
                    .customerId(o.getCustomerId())
                    .totalAmount(o.getTotalAmount())
                    .status(o.getStatus())
                    .createdAt(LocalDateTime.now())
                    .items(o.getItems())
                    .build();
            return o;
        });

        OrderResponse response = orderService.createOrder(request);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.getTotalAmount()).isEqualByComparingTo("72000");
        assertThat(response.getItems()).hasSize(2);
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    // ---- getOrderById ----

    @Test
    void getOrderById_shouldReturnOrderWhenFound() {
        when(orderRepository.findByIdWithItems(1L)).thenReturn(Optional.of(sampleOrder));

        OrderResponse response = orderService.getOrderById(1L);

        assertThat(response.getOrderId()).isEqualTo(1L);
        assertThat(response.getCustomerId()).isEqualTo(1001L);
    }

    @Test
    void getOrderById_shouldThrowWhenNotFound() {
        when(orderRepository.findByIdWithItems(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(99L))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ---- updateOrderStatus ----

    @Test
    void updateStatus_pendingToProcessing_shouldSucceed() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(sampleOrder));
        when(orderRepository.save(any())).thenReturn(sampleOrder);

        OrderResponse response = orderService.updateOrderStatus(1L,
                new UpdateStatusRequest(OrderStatus.PROCESSING));

        assertThat(response).isNotNull();
        verify(orderRepository).save(sampleOrder);
    }

    @Test
    void updateStatus_invalidTransition_shouldThrow() {
        sampleOrder.setStatus(OrderStatus.DELIVERED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(sampleOrder));

        assertThatThrownBy(() ->
                orderService.updateOrderStatus(1L, new UpdateStatusRequest(OrderStatus.PENDING)))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void updateStatus_processingToShipped_shouldSucceed() {
        sampleOrder.setStatus(OrderStatus.PROCESSING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(sampleOrder));
        when(orderRepository.save(any())).thenReturn(sampleOrder);

        orderService.updateOrderStatus(1L, new UpdateStatusRequest(OrderStatus.SHIPPED));

        verify(orderRepository).save(sampleOrder);
        assertThat(sampleOrder.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    // ---- cancelOrder ----

    @Test
    void cancelOrder_whenPending_shouldSucceed() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(sampleOrder));
        when(orderRepository.save(any())).thenReturn(sampleOrder);

        OrderResponse response = orderService.cancelOrder(1L);

        assertThat(sampleOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancelOrder_whenNotPending_shouldThrow() {
        sampleOrder.setStatus(OrderStatus.SHIPPED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(sampleOrder));

        assertThatThrownBy(() -> orderService.cancelOrder(1L))
                .isInstanceOf(OrderCancellationException.class)
                .hasMessageContaining("SHIPPED");
    }

    // ---- processPendingOrders (scheduler) ----

    @Test
    void processPendingOrders_shouldMovePendingToProcessing() {
        List<Order> pending = List.of(sampleOrder);
        when(orderRepository.findByStatus(OrderStatus.PENDING)).thenReturn(pending);
        when(orderRepository.saveAll(anyList())).thenReturn(pending);

        orderService.processPendingOrders();

        assertThat(sampleOrder.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        verify(orderRepository).saveAll(pending);
    }

    @Test
    void processPendingOrders_whenNoPendingOrders_shouldDoNothing() {
        when(orderRepository.findByStatus(OrderStatus.PENDING)).thenReturn(List.of());

        orderService.processPendingOrders();

        verify(orderRepository, never()).saveAll(anyList());
    }

    // ---- getAllOrders ----

    @Test
    void getAllOrders_withStatusFilter_shouldReturnFiltered() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Order> page = new PageImpl<>(List.of(sampleOrder));
        when(orderRepository.findByStatus(OrderStatus.PENDING, pageable)).thenReturn(page);

        Page<OrderResponse> result = orderService.getAllOrders(OrderStatus.PENDING, pageable);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void getAllOrders_withoutStatusFilter_shouldReturnAll() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Order> page = new PageImpl<>(List.of(sampleOrder));
        when(orderRepository.findAll(pageable)).thenReturn(page);

        Page<OrderResponse> result = orderService.getAllOrders(null, pageable);

        assertThat(result.getContent()).hasSize(1);
    }
}
