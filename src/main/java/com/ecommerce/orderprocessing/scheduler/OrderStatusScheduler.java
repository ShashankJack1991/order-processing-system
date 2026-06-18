package com.ecommerce.orderprocessing.scheduler;

import com.ecommerce.orderprocessing.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderStatusScheduler {

    private final OrderService orderService;

    // Runs every 5 minutes
    @Scheduled(cron = "0 */5 * * * *")
    public void processPendingOrders() {
        log.info("Scheduler triggered: processing PENDING orders");
        orderService.processPendingOrders();
    }
}
