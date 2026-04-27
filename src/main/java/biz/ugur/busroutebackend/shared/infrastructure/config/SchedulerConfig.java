package biz.ugur.busroutebackend.shared.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
@Slf4j
public class SchedulerConfig {

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(16);
        scheduler.setThreadNamePrefix("scheduled-task-");
        scheduler.setRejectedExecutionHandler((task, executor) ->
                log.warn("[SCHEDULER] Task rejected — pool saturated (poolSize={}, queueSize={})",
                        executor.getPoolSize(), executor.getQueue().size()));
        scheduler.initialize();
        return scheduler;
    }
}