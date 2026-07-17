package com.chinacreator.gzcm.services.apigateway.saga;

import com.chinacreator.gzcm.common.saga.SagaContext;
import com.chinacreator.gzcm.common.saga.SagaContext.SagaStatus;
import com.chinacreator.gzcm.common.saga.SagaDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
public class SagaOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(SagaOrchestrator.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbcTemplate;

    public SagaOrchestrator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public SagaContext execute(SagaDefinition definition, Map<String, Object> inputData) {
        SagaContext context = new SagaContext(definition.getSagaType(), inputData);
        persistSagaState(context, definition.getSteps().size());
        log.info("Starting saga {} with {} steps", context.getSagaId(), definition.getSteps().size());

        for (int i = 0; i < definition.getSteps().size(); i++) {
            SagaDefinition.SagaStep step = definition.getSteps().get(i);
            context.setCurrentStep(i);
            context.setStatus(SagaStatus.EXECUTING);
            updateSagaState(context);
            try {
                log.info("Executing step {}/{}: {} on {}", i + 1, definition.getSteps().size(), step.getName(), step.getTargetService());
                step.getExecute().accept(context);
            } catch (Exception e) {
                log.error("Step {} failed: {}. Starting compensation.", step.getName(), e.getMessage());
                context.setStatus(SagaStatus.COMPENSATING);
                updateSagaState(context);
                compensate(definition, context, i);
                context.setStatus(SagaStatus.FAILED);
                updateSagaStateWithError(context, e.getMessage());
                return context;
            }
        }
        context.setStatus(SagaStatus.COMPLETED);
        updateSagaState(context);
        log.info("Saga {} completed successfully", context.getSagaId());
        return context;
    }

    private void compensate(SagaDefinition definition, SagaContext context, int failedStepIndex) {
        for (int i = failedStepIndex - 1; i >= 0; i--) {
            SagaDefinition.SagaStep step = definition.getSteps().get(i);
            try {
                log.info("Compensating step {}: {}", i, step.getName());
                if (step.getCompensate() != null) {
                    step.getCompensate().accept(context);
                }
            } catch (Exception e) {
                log.error("Compensation for step {} failed: {}", step.getName(), e.getMessage());
            }
        }
    }

    private void persistSagaState(SagaContext context, int totalSteps) {
        try {
            String inputDataJson = OBJECT_MAPPER.writeValueAsString(context.getInputData());
            jdbcTemplate.update(
                "INSERT INTO ecos_config.saga_instance (id, saga_type, status, current_step, total_steps, input_data, compensation_data) " +
                "VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb)",
                context.getSagaId(), context.getSagaType(), context.getStatus().name(),
                context.getCurrentStep(), totalSteps, inputDataJson, "{}"
            );
        } catch (Exception e) {
            log.error("Failed to persist saga state: {}", e.getMessage());
        }
    }

    private void updateSagaState(SagaContext context) {
        try {
            String compensationJson = OBJECT_MAPPER.writeValueAsString(context.getCompensationData());
            jdbcTemplate.update(
                "UPDATE ecos_config.saga_instance SET status = ?, current_step = ?, compensation_data = ?::jsonb, updated_at = ? WHERE id = ?",
                context.getStatus().name(), context.getCurrentStep(), compensationJson, Instant.now(), context.getSagaId()
            );
        } catch (Exception e) {
            log.error("Failed to update saga state: {}", e.getMessage());
        }
    }

    private void updateSagaStateWithError(SagaContext context, String errorMessage) {
        try {
            jdbcTemplate.update(
                "UPDATE ecos_config.saga_instance SET status = ?, error_message = ?, updated_at = ? WHERE id = ?",
                context.getStatus().name(), errorMessage, Instant.now(), context.getSagaId()
            );
        } catch (Exception e) {
            log.error("Failed to update saga state with error: {}", e.getMessage());
        }
    }
}
