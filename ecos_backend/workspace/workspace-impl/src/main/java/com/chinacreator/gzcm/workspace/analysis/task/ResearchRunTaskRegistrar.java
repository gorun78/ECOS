package com.chinacreator.gzcm.workspace.analysis.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;

import jakarta.annotation.PostConstruct;

/**
 * RESEARCH_RUN runtime-task 注册器：启动时把执行器/解析器注册进底座，
 * 使 {@code submitTask → parse → execute → callback} 全生命周期可用。
 *
 * <p>{@link ObjectProvider} 软依赖：runtime-task 未装配时仅 WARN，不阻断启动；
 * 异步入口在 {@code ResearchRunAsyncDispatcher.dispatch} 处诚实 501（禁假异步）。</p>
 */
@Component
public class ResearchRunTaskRegistrar {

    private static final Logger log = LoggerFactory.getLogger(ResearchRunTaskRegistrar.class);

    private final ObjectProvider<ITaskManagementService> taskManagementProvider;
    private final ResearchRunTaskExecutor executor;
    private final ResearchRunTaskParser parser;

    public ResearchRunTaskRegistrar(ObjectProvider<ITaskManagementService> taskManagementProvider,
                                    ResearchRunTaskExecutor executor,
                                    ResearchRunTaskParser parser) {
        this.taskManagementProvider = taskManagementProvider;
        this.executor = executor;
        this.parser = parser;
    }

    @PostConstruct
    public void register() {
        ITaskManagementService taskService = taskManagementProvider.getIfAvailable();
        if (taskService == null) {
            log.warn("ITaskManagementService 未装配：RESEARCH_RUN 执行器/解析器未注册（异步入口将 501）");
            return;
        }
        taskService.registerExecutor(ResearchRunAsyncDispatcher.TASK_TYPE, executor);
        taskService.registerParser(ResearchRunAsyncDispatcher.TASK_TYPE, parser);
        log.info("RESEARCH_RUN 异步任务已注册（taskType={}）", ResearchRunAsyncDispatcher.TASK_TYPE);
    }
}
