package com.ecommerce.orderprocessing.controller;

import com.ecommerce.orderprocessing.dto.*;
import com.ecommerce.orderprocessing.entity.OrderStatus;
import com.ecommerce.orderprocessing.exception.OrderCancellationException;
import com.ecommerce.orderprocessing.exception.OrderNotFoundException;
import com.ecommerce.orderprocessing.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    private OrderResponse sampleResponse;

    @BeforeEach
    void setUp() {
        sampleResponse = OrderResponse.builder()
                .orderId(1L)
                .customerId(1001L)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("72000.00"))
                .createdAt(LocalDateTime.now())
                .items(List.of(
                        OrderItemResponse.builder()
                                .id(1L)
                                .productName("Laptop")
                                .quantity(1)
                                .price(new BigDecimal("70000.00"))
                                .build()
                ))
                .build();
    }

    // ---- POST /api/orders ----

    @Test
    void createOrder_shouldReturn201WithResponse() throws Exception {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId(1001L)
                .items(List.of(new OrderItemRequest("Laptop", 1, new BigDecimal("70000"))))
                .build();

        when(orderService.createOrder(any(CreateOrderRequest.class))).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(72000.00));
    }

    @Test
    void createOrder_withMissingCustomerId_shouldReturn400() throws Exception {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(new OrderItemRequest("Laptop", 1, new BigDecimal("70000"))))
                .build();

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void createOrder_withEmptyItems_shouldReturn400() throws Exception {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId(1001L)
                .items(List.of())
                .build();

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ---- GET /api/orders/{id} ----

    @Test
    void getOrderById_shouldReturn200WithOrder() throws Exception {
        when(orderService.getOrderById(1L)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.customerId").value(1001));
    }

    @Test
    void getOrderById_whenNotFound_shouldReturn404() throws Exception {
        when(orderService.getOrderById(99L)).thenThrow(new OrderNotFoundException(99L));

        mockMvc.perform(get("/api/orders/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Order not found with id: 99"));
    }

    // ---- PUT /api/orders/{id}/cancel ----

    @Test
    void cancelOrder_shouldReturn200() throws Exception {
        sampleResponse.setStatus(OrderStatus.CANCELLED);
        when(orderService.cancelOrder(1L)).thenReturn(sampleResponse);

        mockMvc.perform(put("/api/orders/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelOrder_whenNotCancellable_shouldReturn409() throws Exception {
        when(orderService.cancelOrder(1L))
                .thenThrow(new OrderCancellationException(1L, OrderStatus.SHIPPED));

        mockMvc.perform(put("/api/orders/1/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    // ---- GET /api/orders ----

    @Test
    void getAllOrders_shouldReturn200WithPage() throws Exception {
        when(orderService.getAllOrders(isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleResponse)));

        mockMvc.perform(get("/api/orders?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].orderId").value(1));
    }

    @Test
    void getAllOrders_withStatusFilter_shouldReturnFiltered() throws Exception {
        when(orderService.getAllOrders(eq(OrderStatus.PENDING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleResponse)));

        mockMvc.perform(get("/api/orders?status=PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }
}
