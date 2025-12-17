package com.paperradar.ingest.infra;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class IngestTaskExecutorConfig {

    @Bean
    public ThreadPoolTaskExecutor ingestTaskExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(1);
        exec.setMaxPoolSize(1);
        exec.setQueueCapacity(10);
        exec.setThreadNamePrefix("ingest-");
        exec.initialize();
        return exec;
    }
}

