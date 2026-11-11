package com.chinacreator.gzcm.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 契约清单（classpath:/contract/ops.json）——路径与参数名的唯一来源。
 *
 * <p>硬约束：命令实现<b>不得</b>出现路径字面量，只能 {@link #pathOf(String)} 取；
 * 清单与契约的绑定由 {@code ContractManifestTest} 与 gateway {@code CliContractParityTest} 双重守护。</p>
 */
public final class OperationRegistry {

    public record Operation(String operationId, String method, String path, String status, String contract) {
    }

    public record Flag(String flag, String operationId, String parameter, String description) {
    }

    public record PathParam(String flag, String operationId, String parameter, String description) {
    }

    public record BodyParam(String flag, String operationId, String parameter, String description) {
    }

    public record Command(String name, String description, String accessMode, String automationLevel,
                          List<String> operationIds, List<Flag> flags, List<PathParam> pathParams,
                          List<BodyParam> bodyParams, boolean requiresApproval, boolean wired) {
    }

    private final Map<String, Operation> operations;
    private final List<Command> commands;

    private OperationRegistry(Map<String, Operation> operations, List<Command> commands) {
        this.operations = operations;
        this.commands = commands;
    }

    public static OperationRegistry load() {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = OperationRegistry.class.getResourceAsStream("/contract/ops.json")) {
            if (in == null) {
                throw new CliException(ExitCodes.FAILED,
                        "classpath 缺少 contract/ops.json（构建产物不完整）");
            }
            JsonNode root = mapper.readTree(in);
            Map<String, Operation> ops = new LinkedHashMap<>();
            for (JsonNode n : root.path("operations")) {
                Operation op = new Operation(
                        n.path("operationId").asText(),
                        n.path("method").asText(),
                        n.path("path").asText(),
                        n.path("status").asText(),
                        n.path("contract").asText());
                ops.put(op.operationId(), op);
            }
            List<Command> cmds = new ArrayList<>();
            for (JsonNode n : root.path("commands")) {
                List<String> ids = new ArrayList<>();
                n.path("operationIds").forEach(id -> ids.add(id.asText()));
                List<Flag> flags = new ArrayList<>();
                for (JsonNode f : n.path("flags")) {
                    flags.add(new Flag(
                            f.path("flag").asText(),
                            f.path("operationId").asText(),
                            f.path("parameter").asText(),
                            f.path("description").asText()));
                }
                List<PathParam> pathParams = new ArrayList<>();
                for (JsonNode p : n.path("pathParams")) {
                    pathParams.add(new PathParam(
                            p.path("flag").asText(),
                            p.path("operationId").asText(),
                            p.path("parameter").asText(),
                            p.path("description").asText()));
                }
                List<BodyParam> bodyParams = new ArrayList<>();
                for (JsonNode b : n.path("bodyParams")) {
                    bodyParams.add(new BodyParam(
                            b.path("flag").asText(),
                            b.path("operationId").asText(),
                            b.path("parameter").asText(),
                            b.path("description").asText()));
                }
                cmds.add(new Command(
                        n.path("name").asText(),
                        n.path("description").asText(),
                        n.path("accessMode").asText(),
                        n.path("automationLevel").asText("L0"),
                        ids,
                        flags,
                        pathParams,
                        bodyParams,
                        n.path("requiresApproval").asBoolean(false),
                        n.path("wired").asBoolean(true)));
            }
            return new OperationRegistry(ops, cmds);
        } catch (CliException e) {
            throw e;
        } catch (Exception e) {
            throw new CliException(ExitCodes.FAILED, "契约清单不可读: " + e.getClass().getSimpleName());
        }
    }

    public Operation operation(String operationId) {
        Operation op = operations.get(operationId);
        if (op == null) {
            throw new CliException(ExitCodes.FAILED,
                    "命令映射到契约中不存在的 operationId: " + operationId);
        }
        return op;
    }

    public String pathOf(String operationId) {
        return operation(operationId).path();
    }

    public List<Command> commands() {
        return List.copyOf(commands);
    }

    /** 只读命令的参数映射（flag → 契约 query 参数名）。 */
    public Map<String, Flag> flagsOf(String commandName) {
        for (Command c : commands) {
            if (c.name().equals(commandName)) {
                Map<String, Flag> map = new LinkedHashMap<>();
                c.flags().forEach(f -> map.put(f.flag(), f));
                return map;
            }
        }
        throw new CliException(ExitCodes.USAGE, "未知命令: " + commandName);
    }

    /** 命令的 path 参数映射（flag → 契约路径参数名）。 */
    public Map<String, PathParam> pathParamsOf(String commandName) {
        for (Command c : commands) {
            if (c.name().equals(commandName)) {
                Map<String, PathParam> map = new LinkedHashMap<>();
                c.pathParams().forEach(p -> map.put(p.flag(), p));
                return map;
            }
        }
        throw new CliException(ExitCodes.USAGE, "未知命令: " + commandName);
    }

    /** 命令的请求体参数映射（flag → 契约 body 字段名）。 */
    public Map<String, BodyParam> bodyParamsOf(String commandName) {
        for (Command c : commands) {
            if (c.name().equals(commandName)) {
                Map<String, BodyParam> map = new LinkedHashMap<>();
                c.bodyParams().forEach(b -> map.put(b.flag(), b));
                return map;
            }
        }
        throw new CliException(ExitCodes.USAGE, "未知命令: " + commandName);
    }

    public boolean has(String operationId) {
        return operations.containsKey(operationId);
    }
}
