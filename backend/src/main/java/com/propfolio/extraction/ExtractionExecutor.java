package com.propfolio.extraction;

import java.util.concurrent.Executor;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Where readings run: two at a time on a small thread pool, which keeps well inside OpenAI rate
 * limits for a small landlord. With {@code app.extraction.inline=true} (tests) they run on the
 * calling thread instead.
 *
 * <p>Deliberately not an {@code Executor} bean, so Spring Boot's own task executor is unaffected.
 */
@Component
public class ExtractionExecutor implements DisposableBean {

    private final Executor executor;
    private final ThreadPoolTaskExecutor pool;

    public ExtractionExecutor(@Value("${app.extraction.inline:false}") boolean inline) {
        if (inline) {
            this.pool = null;
            this.executor = new SyncTaskExecutor();
        } else {
            this.pool = new ThreadPoolTaskExecutor();
            pool.setCorePoolSize(2);
            pool.setMaxPoolSize(2);
            pool.setQueueCapacity(500);
            pool.setThreadNamePrefix("bill-reader-");
            pool.setWaitForTasksToCompleteOnShutdown(true);
            pool.setAwaitTerminationSeconds(30);
            pool.initialize();
            this.executor = pool;
        }
    }

    public void execute(Runnable task) {
        executor.execute(task);
    }

    @Override
    public void destroy() {
        if (pool != null) {
            pool.shutdown();
        }
    }
}
