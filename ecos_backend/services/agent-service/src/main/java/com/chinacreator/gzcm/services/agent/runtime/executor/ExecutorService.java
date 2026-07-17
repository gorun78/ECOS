package com.chinacreator.gzcm.services.agent.runtime.executor;

import com.chinacreator.gzcm.services.agent.runtime.model.ExecutionResult;
import com.chinacreator.gzcm.services.agent.runtime.model.ExecutionTask;
import java.util.List;

public interface ExecutorService {
    ExecutionResult execute(ExecutionTask task);
    ExecutionResult executeWithTools(ExecutionTask task, List<String> toolIds);
}
