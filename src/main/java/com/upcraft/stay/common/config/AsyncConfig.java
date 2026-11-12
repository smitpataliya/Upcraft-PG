package com.upcraft.stay.common.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configures bounded asynchronous execution for non-blocking notification dispatch.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Bounded executor isolating external push/email/messaging provider latency from HTTP request threads.
     *
     * @param appProperties typed notification pool settings
     * @return configured executor bean
     */
    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor(AppProperties appProperties) {
        AppProperties.Notifications config = appProperties.getNotifications();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(config.getAsyncCorePoolSize());
        executor.setMaxPoolSize(config.getAsyncMaxPoolSize());
        executor.setQueueCapacity(config.getAsyncQueueCapacity());
        executor.setThreadNamePrefix("notif-dispatch-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}

