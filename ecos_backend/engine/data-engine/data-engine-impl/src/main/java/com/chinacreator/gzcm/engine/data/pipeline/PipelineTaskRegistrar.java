package com.chinacreator.gzcm.engine.data.pipeline;

import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Pipeline runtime-task 注册器 — 在 data-engine 启动时将 PIPELINE 执行器与解析器
 * 注册到 ITaskManagementService。
 * <p>
 * 注册后，PipelineController.executeDefinition() 通过
 * ITaskManagementService.submitAndExecute(desc) 走 runtime-task 完整生命周期：
 * submit → parse(PipelineTaskParser) → execute(PipelineTaskExecutor) → callback。
 *
 * @author DataBridge Datanet Team
 */
@Component
public class PipelineTaskRegistrar {

    private static final Logger log = LoggerFactory.getLogger(PipelineTaskRegistrar.class);
    private static final String PIPELINE = "PIPELINE";

    private final ITaskManagementService taskManagementService;
    private final PipelineTaskExecutor pipelineTaskExecutor;
    private final PipelineTaskParser pipelineTaskParser;

    @Autowired
    public PipelineTaskRegistrar(ITaskManagementService taskManagementService,
                                  PipelineTaskExecutor pipelineTaskExecutor,
                                  PipelineTaskParser pipelineTaskParser) {
        this.taskManagementService = taskManagementService;
        this.pipelineTaskExecutor = pipelineTaskExecutor;
        this.pipelineTaskParser = pipelineTaskParser;
    }

    @PostConstruct
    public void register() {
        taskManagementService.registerExecutor(PIPELINE, pipelineTaskExecutor);
        taskManagementService.registerParser(PIPELINE, pipelineTaskParser);
        log.info("Pipeline runtime-task registered: executorType=PIPELINE, parserType=PIPELINE");
    }
}
