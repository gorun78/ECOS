package com.chinacreator.gzcm.services.agent.runtime.spi;

import com.chinacreator.gzcm.runtime.llm.agent.spi.model.AgentExecutionPlan;
import com.chinacreator.gzcm.runtime.llm.agent.spi.model.AgentExecutionResult;
import com.chinacreator.gzcm.runtime.llm.agent.spi.model.AgentExecutionTask;
import com.chinacreator.gzcm.runtime.llm.agent.spi.model.AgentGoal;
import com.chinacreator.gzcm.runtime.llm.agent.spi.model.AgentMemoryContext;
import com.chinacreator.gzcm.runtime.llm.agent.spi.model.AgentMemoryLayer;
import com.chinacreator.gzcm.runtime.llm.agent.spi.model.AgentMemoryQuery;
import com.chinacreator.gzcm.runtime.llm.agent.spi.model.AgentMemoryRecord;
import com.chinacreator.gzcm.runtime.llm.agent.spi.model.AgentToolSpec;
import com.chinacreator.gzcm.runtime.llm.agent.spi.model.AgentToolType;
import com.chinacreator.gzcm.services.agent.runtime.model.MemoryRecord;
import com.chinacreator.gzcm.services.agent.runtime.model.ToolDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * 接缝义务 1 —— SPI 契约模型 ↔ agent-service runtime 模型的单向转换。
 *
 * <p>转换只发生在 provider 侧（本类）：ai-engine 只见 SPI 模型，
 * 从而在 {@code ai-engine-impl → agent-service} 的 compile 依赖删除后仍可编译
 * （铁律 §2.1，ARCH_SPEC C257）。</p>
 *
 * <p>枚举互转采「按名解析 + 显式失败」：两侧枚举若漂移，宁可抛错也不静默丢分层/丢类型。</p>
 *
 * @author ecos-factory
 * @since 详细设计-06 §八 M0
 */
final class AgentRuntimeSpiMapping {

    private AgentRuntimeSpiMapping() {
    }

    // ── goal / plan / task / result ────────────────────────────────

    static com.chinacreator.gzcm.services.agent.runtime.model.Goal toProviderGoal(AgentGoal src) {
        if (src == null) {
            return null;
        }
        com.chinacreator.gzcm.services.agent.runtime.model.Goal goal =
                new com.chinacreator.gzcm.services.agent.runtime.model.Goal();
        goal.setId(src.getId());
        goal.setDescription(src.getDescription());
        goal.setPriority(src.getPriority());
        goal.setConstraints(src.getConstraints());
        return goal;
    }

    static AgentExecutionPlan toSpiPlan(com.chinacreator.gzcm.services.agent.runtime.model.ExecutionPlan src) {
        if (src == null) {
            return null;
        }
        AgentExecutionPlan plan = new AgentExecutionPlan();
        plan.setId(src.getId());
        plan.setGoalId(src.getGoalId());
        plan.setStatus(src.getStatus() == null ? null : src.getStatus().name());
        List<AgentExecutionTask> tasks = new ArrayList<>();
        if (src.getTasks() != null) {
            for (com.chinacreator.gzcm.services.agent.runtime.model.ExecutionTask t : src.getTasks()) {
                tasks.add(toSpiTask(t));
            }
        }
        plan.setTasks(tasks);
        return plan;
    }

    static com.chinacreator.gzcm.services.agent.runtime.model.ExecutionPlan toProviderPlan(
            AgentExecutionPlan src) {
        if (src == null) {
            return null;
        }
        com.chinacreator.gzcm.services.agent.runtime.model.ExecutionPlan plan =
                new com.chinacreator.gzcm.services.agent.runtime.model.ExecutionPlan();
        plan.setId(src.getId());
        plan.setGoalId(src.getGoalId());
        if (src.getStatus() != null) {
            plan.setStatus(com.chinacreator.gzcm.services.agent.runtime.model.PlanStatus.valueOf(src.getStatus()));
        }
        List<com.chinacreator.gzcm.services.agent.runtime.model.ExecutionTask> tasks = new ArrayList<>();
        if (src.getTasks() != null) {
            for (AgentExecutionTask t : src.getTasks()) {
                tasks.add(toProviderTask(t));
            }
        }
        plan.setTasks(tasks);
        return plan;
    }

    static com.chinacreator.gzcm.services.agent.runtime.model.ExecutionTask toProviderTask(AgentExecutionTask src) {
        if (src == null) {
            return null;
        }
        com.chinacreator.gzcm.services.agent.runtime.model.ExecutionTask task =
                new com.chinacreator.gzcm.services.agent.runtime.model.ExecutionTask();
        task.setId(src.getId());
        task.setPlanId(src.getPlanId());
        task.setAgentId(src.getAgentId());
        task.setSessionId(src.getSessionId());
        task.setInstruction(src.getInstruction());
        task.setToolType(toProviderToolType(src.getToolType()));
        task.setToolParams(src.getToolParams());
        task.setStatus(src.getStatus() == null ? null
                : com.chinacreator.gzcm.services.agent.runtime.model.TaskStatus.valueOf(src.getStatus()));
        task.setResult(toProviderResult(src.getResult()));
        return task;
    }

    static AgentExecutionTask toSpiTask(com.chinacreator.gzcm.services.agent.runtime.model.ExecutionTask src) {
        if (src == null) {
            return null;
        }
        AgentExecutionTask task = new AgentExecutionTask();
        task.setId(src.getId());
        task.setPlanId(src.getPlanId());
        task.setAgentId(src.getAgentId());
        task.setSessionId(src.getSessionId());
        task.setInstruction(src.getInstruction());
        task.setToolType(toSpiToolType(src.getToolType()));
        task.setToolParams(src.getToolParams());
        task.setStatus(src.getStatus() == null ? null : src.getStatus().name());
        task.setResult(toSpiResult(src.getResult()));
        return task;
    }

    static AgentExecutionResult toSpiResult(com.chinacreator.gzcm.services.agent.runtime.model.ExecutionResult src) {
        if (src == null) {
            return null;
        }
        AgentExecutionResult result = new AgentExecutionResult();
        result.setId(src.getId());
        result.setTaskId(src.getTaskId());
        result.setOutput(src.getOutput());
        result.setSuccess(src.isSuccess());
        result.setMetrics(src.getMetrics());
        return result;
    }

    static com.chinacreator.gzcm.services.agent.runtime.model.ExecutionResult toProviderResult(
            AgentExecutionResult src) {
        if (src == null) {
            return null;
        }
        com.chinacreator.gzcm.services.agent.runtime.model.ExecutionResult result =
                new com.chinacreator.gzcm.services.agent.runtime.model.ExecutionResult();
        result.setId(src.getId());
        result.setTaskId(src.getTaskId());
        result.setOutput(src.getOutput());
        result.setSuccess(src.isSuccess());
        result.setMetrics(src.getMetrics());
        return result;
    }

    // ── memory ─────────────────────────────────────────────────────

    static MemoryRecord toProviderRecord(AgentMemoryRecord src) {
        if (src == null) {
            return null;
        }
        MemoryRecord record = new MemoryRecord();
        record.setId(src.getId());
        record.setAgentId(src.getAgentId());
        record.setSessionId(src.getSessionId());
        record.setContent(src.getContent());
        record.setEmbedding(src.getEmbedding());
        record.setTimestamp(src.getTimestamp());
        if (src.getLayer() != null) {
            record.setLayer(com.chinacreator.gzcm.services.agent.runtime.model.MemoryLayer
                    .valueOf(src.getLayer().name()));
        }
        return record;
    }

    static AgentMemoryRecord toSpiRecord(MemoryRecord src) {
        if (src == null) {
            return null;
        }
        AgentMemoryRecord record = new AgentMemoryRecord();
        record.setId(src.getId());
        record.setAgentId(src.getAgentId());
        record.setSessionId(src.getSessionId());
        record.setContent(src.getContent());
        record.setEmbedding(src.getEmbedding());
        record.setTimestamp(src.getTimestamp());
        if (src.getLayer() != null) {
            record.setLayer(AgentMemoryLayer.valueOf(src.getLayer().name()));
        }
        return record;
    }

    static List<AgentMemoryRecord> toSpiRecords(List<MemoryRecord> src) {
        List<AgentMemoryRecord> out = new ArrayList<>();
        if (src != null) {
            for (MemoryRecord r : src) {
                out.add(toSpiRecord(r));
            }
        }
        return out;
    }

    static com.chinacreator.gzcm.services.agent.runtime.model.MemoryQuery toProviderQuery(AgentMemoryQuery src) {
        if (src == null) {
            return null;
        }
        com.chinacreator.gzcm.services.agent.runtime.model.MemoryQuery query =
                new com.chinacreator.gzcm.services.agent.runtime.model.MemoryQuery();
        query.setAgentId(src.getAgentId());
        query.setSessionId(src.getSessionId());
        query.setKeywords(src.getKeywords());
        query.setVector(src.getVector());
        query.setTopK(src.getTopK());
        if (src.getLayer() != null) {
            query.setLayer(com.chinacreator.gzcm.services.agent.runtime.model.MemoryLayer
                    .valueOf(src.getLayer().name()));
        }
        return query;
    }

    static AgentMemoryContext toSpiContext(com.chinacreator.gzcm.services.agent.runtime.model.MemoryContext src) {
        if (src == null) {
            return null;
        }
        AgentMemoryContext ctx = new AgentMemoryContext();
        ctx.setAgentId(src.getAgentId());
        ctx.setSessionId(src.getSessionId());
        ctx.setWorkingMemory(src.getWorkingMemory() == null ? null : toSpiRecords(src.getWorkingMemory()));
        ctx.setSessionMemory(src.getSessionMemory() == null ? null : toSpiRecords(src.getSessionMemory()));
        ctx.setLongTermMemory(src.getLongTermMemory() == null ? null : toSpiRecords(src.getLongTermMemory()));
        ctx.setEnterpriseMemory(src.getEnterpriseMemory() == null ? null : toSpiRecords(src.getEnterpriseMemory()));
        return ctx;
    }

    // ── tool ───────────────────────────────────────────────────────

    static AgentToolSpec toSpiTool(ToolDefinition src) {
        if (src == null) {
            return null;
        }
        AgentToolSpec spec = new AgentToolSpec();
        spec.setCode(src.getCode());
        spec.setName(src.getName());
        spec.setEndpoint(src.getEndpoint());
        spec.setStatus(src.getStatus());
        spec.setParameters(src.getParameters());
        spec.setType(toSpiToolType(src.getType()));
        return spec;
    }

    static List<AgentToolSpec> toSpiTools(List<ToolDefinition> src) {
        List<AgentToolSpec> out = new ArrayList<>();
        if (src != null) {
            for (ToolDefinition t : src) {
                out.add(toSpiTool(t));
            }
        }
        return out;
    }

    static AgentToolType toSpiToolType(com.chinacreator.gzcm.services.agent.runtime.model.ToolType src) {
        return src == null ? null : AgentToolType.valueOf(src.name());
    }

    static com.chinacreator.gzcm.services.agent.runtime.model.ToolType toProviderToolType(AgentToolType src) {
        return src == null ? null
                : com.chinacreator.gzcm.services.agent.runtime.model.ToolType.valueOf(src.name());
    }
}
