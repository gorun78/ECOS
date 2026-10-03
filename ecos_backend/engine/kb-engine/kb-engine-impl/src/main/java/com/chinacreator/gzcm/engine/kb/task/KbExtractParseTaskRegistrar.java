package com.chinacreator.gzcm.engine.kb.task;

import com.chinacreator.gzcm.engine.kb.service.KnowledgeExtractionService;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 知识抽取解析任务注册器（F04-14）— 启动时将 {@code KB_EXTRACT_PARSE}
 * 执行器与解析器注册到 ITaskManagementService（submit → parse → execute
 * 全生命周期），禁自建 Executors/@Scheduled（K-38/K-39 纠正）。
 *
 * @author ECOS KB Team
 */
@Component
public class KbExtractParseTaskRegistrar {

    private static final Logger log = LoggerFactory.getLogger(KbExtractParseTaskRegistrar.class);
    private static final String TASK_TYPE = KnowledgeExtractionService.TASK_TYPE_EXTRACT_PARSE;

    private final ITaskManagementService taskManagementService;
    private final KbExtractParseTaskExecutor executor;
    private final KbExtractParseTaskParser parser;

    public KbExtractParseTaskRegistrar(ITaskManagementService taskManagementService,
                                       KbExtractParseTaskExecutor executor,
                                       KbExtractParseTaskParser parser) {
        this.taskManagementService = taskManagementService;
        this.executor = executor;
        this.parser = parser;
    }

    @PostConstruct
    public void register() {
        taskManagementService.registerExecutor(TASK_TYPE, executor);
        taskManagementService.registerParser(TASK_TYPE, parser);
        log.info("KB_EXTRACT_PARSE runtime-task registered: executorType={}", TASK_TYPE);
    }
}
