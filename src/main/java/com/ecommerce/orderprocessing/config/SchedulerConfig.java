package com.ecommerce.orderprocessing.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulerConfig {
    // Scheduling is enabled here and in the main application class.
    // Configure thread pool via application.yml if concurrent scheduling is needed.
}
