package com.chinacreator.gzcm.engine.kb.task;

import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * K1 结构化抽取任务注册器 — 在 kb-engine 启动时将 KB_IMPORT 执行器与解析器
 * 注册到 ITaskManagementService。
 * <p>
 * 注册后，StructuredExtractController（async 路径）通过
 * {@code ITaskManagementService.submitTask(desc)} + {@code executeTask(taskId)}
 * 走 runtime-task 完整生命周期：submit → parse(KbImportTaskParser) → execute(KbImportTaskExecutor)。
 *
 * @author ECOS KB Team
 */
@Component
public class KbImportTaskRegistrar {

    private static final Logger log = LoggerFactory.getLogger(KbImportTaskRegistrar.class);
    private static final String TASK_TYPE = "KB_IMPORT";

    private final ITaskManagementService taskManagementService;
    private final KbImportTaskExecutor kbImportTaskExecutor;
    private final KbImportTaskParser kbImportTaskParser;

    public KbImportTaskRegistrar(ITaskManagementService taskManagementService,
                                 KbImportTaskExecutor kbImportTaskExecutor,
                                 KbImportTaskParser kbImportTaskParser) {
        this.taskManagementService = taskManagementService;
        this.kbImportTaskExecutor = kbImportTaskExecutor;
        this.kbImportTaskParser = kbImportTaskParser;
    }

    @PostConstruct
    public void register() {
        taskManagementService.registerExecutor(TASK_TYPE, kbImportTaskExecutor);
        taskManagementService.registerParser(TASK_TYPE, kbImportTaskParser);
        log.info("KB_IMPORT runtime-task registered: executorType=KB_IMPORT, parserType=KB_IMPORT");
    }
}
