package com.chinacreator.gzcm.engine.kb.task;

import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 历史画像生成任务注册器（F04-06 / F04-14）— 启动时将 KB_PROFILE_GENERATE
 * 执行器与解析器注册到 ITaskManagementService，使画像生成走 runtime-task 完整生命周期
 * （submit → parse → execute），禁自建 Executors/@Scheduled（K-38/K-39 纠正）。
 *
 * @author ECOS KB Team
 */
@Component
public class KbProfileTaskRegistrar {

    private static final Logger log = LoggerFactory.getLogger(KbProfileTaskRegistrar.class);
    private static final String TASK_TYPE = "KB_PROFILE_GENERATE";

    private final ITaskManagementService taskManagementService;
    private final KbProfileTaskExecutor executor;
    private final KbProfileTaskParser parser;

    public KbProfileTaskRegistrar(ITaskManagementService taskManagementService,
                                  KbProfileTaskExecutor executor,
                                  KbProfileTaskParser parser) {
        this.taskManagementService = taskManagementService;
        this.executor = executor;
        this.parser = parser;
    }

    @PostConstruct
    public void register() {
        taskManagementService.registerExecutor(TASK_TYPE, executor);
        taskManagementService.registerParser(TASK_TYPE, parser);
        log.info("KB_PROFILE_GENERATE runtime-task registered: executorType={}", TASK_TYPE);
    }
}
