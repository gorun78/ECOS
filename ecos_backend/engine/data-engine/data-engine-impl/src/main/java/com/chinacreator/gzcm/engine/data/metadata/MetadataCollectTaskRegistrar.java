package com.chinacreator.gzcm.engine.data.metadata;

import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * PMO-37 METADATA_COLLECT 任务引擎注册器。
 * <p>
 * 在 data-engine 启动时将解析器/执行器注册到 ITaskManagementService
 * （模式同 PipelineTaskRegistrar）。
 *
 * @author DataBridge Datanet Team
 */
@Component
public class MetadataCollectTaskRegistrar {

    private static final Logger log = LoggerFactory.getLogger(MetadataCollectTaskRegistrar.class);

    private final ITaskManagementService taskManagementService;
    private final MetadataCollectTaskParser parser;
    private final MetadataCollectTaskExecutor executor;

    public MetadataCollectTaskRegistrar(ITaskManagementService taskManagementService,
                                        MetadataCollectTaskParser parser,
                                        MetadataCollectTaskExecutor executor) {
        this.taskManagementService = taskManagementService;
        this.parser = parser;
        this.executor = executor;
    }

    @PostConstruct
    public void register() {
        taskManagementService.registerExecutor(MetadataCollectTaskExecutor.EXECUTOR_TYPE, executor);
        taskManagementService.registerParser(MetadataCollectTaskParser.TASK_TYPE, parser);
        log.info("METADATA_COLLECT runtime-task registered: taskType={}", MetadataCollectTaskParser.TASK_TYPE);
    }
}
