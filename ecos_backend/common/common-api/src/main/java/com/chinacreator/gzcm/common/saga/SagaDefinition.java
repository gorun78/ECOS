package com.chinacreator.gzcm.common.saga;

import java.util.List;
import java.util.function.Consumer;

public class SagaDefinition {
    private final String sagaType;
    private final List<SagaStep> steps;

    public SagaDefinition(String sagaType, List<SagaStep> steps) {
        this.sagaType = sagaType;
        this.steps = steps;
    }

    public String getSagaType() { return sagaType; }
    public List<SagaStep> getSteps() { return steps; }

    public static class SagaStep {
        private final String name;
        private final String targetService;
        private final String action;
        private final Consumer<SagaContext> execute;
        private final Consumer<SagaContext> compensate;

        public SagaStep(String name, String targetService, String action,
                        Consumer<SagaContext> execute, Consumer<SagaContext> compensate) {
            this.name = name;
            this.targetService = targetService;
            this.action = action;
            this.execute = execute;
            this.compensate = compensate;
        }

        public String getName() { return name; }
        public String getTargetService() { return targetService; }
        public String getAction() { return action; }
        public Consumer<SagaContext> getExecute() { return execute; }
        public Consumer<SagaContext> getCompensate() { return compensate; }
    }
}
