package com.example.limitguard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    // tells spring to create an object, manage it, and make it available for other classes to use
    // stores that object under the name notificationExecutor
    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {

        // Creates a thread pool to handle background notification tasks
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Start with 3 threads ready to process notifications
        executor.setCorePoolSize(3);

        // Allow up to 5 threads when the workload increases
        executor.setMaxPoolSize(5);

        // Store up to 100 waiting tasks when all core threads are busy
        executor.setQueueCapacity(100);

        // Give background threads recognizable names for debugging
        executor.setThreadNamePrefix("notification-");

        // Wait for running tasks to finish during application shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // Wait up to 30 seconds for tasks to finish during shutdown.
        executor.setAwaitTerminationSeconds(30);

        // Initialize the thread pool
        executor.initialize();

        // Register the executor with Spring
        return executor;
    }
}
