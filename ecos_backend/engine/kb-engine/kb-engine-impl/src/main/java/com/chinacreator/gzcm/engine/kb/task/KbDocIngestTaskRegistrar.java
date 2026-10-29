package com.chinacreator.gzcm.engine.kb.task;

import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 文档接入解析任务注册器 — 在 kb-engine 启动时将 KB_DOC_INGEST 执行器与解析器
 * 注册到 ITaskManagementService（#146z 附录A 第 11 条异步承流腿，形态对齐
 * {@code KbImportTaskRegistrar} 先例）。
 *
 * <p>注册后，契约端点 {@code POST /api/v1/knowledge/ingest/documents} 与
 * {@code POST /api/v1/knowledge/ingest/documents/{id}/retry} 经
 * {@code ITaskManagementService.submitTask(desc)}（async=true，真 202 语义，
 * 对齐 {@code KbProfileController} 的 KB_PROFILE_GENERATE 先例）走完整生命周期：
 * submit → parse(KbDocIngestTaskParser) → execute(KbDocIngestTaskExecutor)。</p>
 *
 * @author ECOS KB Team
 */
@Component
public class KbDocIngestTaskRegistrar {

    private static final Logger log = LoggerFactory.getLogger(KbDocIngestTaskRegistrar.class);

    private final ITaskManagementService taskManagementService;
    private final KbDocIngestTaskExecutor kbDocIngestTaskExecutor;
    private final KbDocIngestTaskParser kbDocIngestTaskParser;

    public KbDocIngestTaskRegistrar(ITaskManagementService taskManagementService,
                                    KbDocIngestTaskExecutor kbDocIngestTaskExecutor,
                                    KbDocIngestTaskParser kbDocIngestTaskParser) {
        this.taskManagementService = taskManagementService;
        this.kbDocIngestTaskExecutor = kbDocIngestTaskExecutor;
        this.kbDocIngestTaskParser = kbDocIngestTaskParser;
    }

    @PostConstruct
    public void register() {
        taskManagementService.registerExecutor(KbDocIngestTaskParser.TASK_TYPE, kbDocIngestTaskExecutor);
        taskManagementService.registerParser(KbDocIngestTaskParser.TASK_TYPE, kbDocIngestTaskParser);
        log.info("KB_DOC_INGEST runtime-task registered: executorType={}, parserType={}",
                KbDocIngestTaskParser.TASK_TYPE, KbDocIngestTaskParser.TASK_TYPE);
    }
}
