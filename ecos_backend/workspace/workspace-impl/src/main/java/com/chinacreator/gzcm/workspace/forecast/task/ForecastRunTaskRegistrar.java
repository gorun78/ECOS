package com.chinacreator.gzcm.workspace.forecast.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;

import jakarta.annotation.PostConstruct;

/**
 * FORECAST_RUN runtime-task 注册器（H-6，形态对齐 data-engine {@code PipelineTaskRegistrar} 先例）：
 * 启动时把 {@link ForecastRunTaskExecutor} / {@link ForecastRunTaskParser} 注册到
 * {@link ITaskManagementService}，使 {@code submitTask→parse→execute→callback} 全生命周期可用。
 *
 * <p>{@link ObjectProvider} 软依赖：runtime-task 底座未装配（单体裁剪/测试切片）时仅 WARN 留痕，
 * 不阻断启动；异步派发入口在 {@code ForecastRunAsyncDispatcher.dispatch} 处诚实 501（禁假异步）。</p>
 */
@Component
public class ForecastRunTaskRegistrar {

    private static final Logger log = LoggerFactory.getLogger(ForecastRunTaskRegistrar.class);

    private final ObjectProvider<ITaskManagementService> taskManagementProvider;
    private final ForecastRunTaskExecutor forecastRunTaskExecutor;
    private final ForecastRunTaskParser forecastRunTaskParser;

    public ForecastRunTaskRegistrar(ObjectProvider<ITaskManagementService> taskManagementProvider,
                                    ForecastRunTaskExecutor forecastRunTaskExecutor,
                                    ForecastRunTaskParser forecastRunTaskParser) {
        this.taskManagementProvider = taskManagementProvider;
        this.forecastRunTaskExecutor = forecastRunTaskExecutor;
        this.forecastRunTaskParser = forecastRunTaskParser;
    }

    @PostConstruct
    public void register() {
        ITaskManagementService taskService = taskManagementProvider.getIfAvailable();
        if (taskService == null) {
            log.warn("ITaskManagementService 未装配：FORECAST_RUN 执行器/解析器未注册（异步派发入口将 501 未上线）");
            return;
        }
        taskService.registerExecutor(ForecastRunAsyncDispatcher.TASK_TYPE, forecastRunTaskExecutor);
        taskService.registerParser(ForecastRunAsyncDispatcher.TASK_TYPE, forecastRunTaskParser);
        log.info("FORECAST_RUN runtime-task registered: executorType={}, parserType={}",
                ForecastRunAsyncDispatcher.TASK_TYPE, ForecastRunAsyncDispatcher.TASK_TYPE);
    }
}
