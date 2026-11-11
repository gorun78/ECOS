package com.chinacreator.gzcm.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chinacreator.gzcm.cli.commands.CapabilitiesCommand;
import com.chinacreator.gzcm.cli.commands.DoctorCommand;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CommandBehaviourTest {

    @Test
    @DisplayName("doctor --dry-run：四项检查全部 UNKNOWN 且不发请求，整体 SUCCESS")
    void doctorDryRunSendsNothing() {
        OperationRegistry registry = OperationRegistry.load();
        CliOptions options = CliOptions.parse(new String[] {"doctor", "--dry-run"}, 1);
        HttpTransport transport = new HttpTransport("http://127.0.0.1:1", "t", 500);

        Map<String, Object> out = DoctorCommand.run(registry, options, transport);

        assertEquals("SUCCESS", out.get("status"));
        Map<?, ?> data = (Map<?, ?>) out.get("data");
        List<?> checks = (List<?>) data.get("checks");
        assertEquals(4, checks.size());
        for (Object c : checks) {
            assertEquals("UNKNOWN", ((Map<?, ?>) c).get("status"));
        }
        assertEquals("http://127.0.0.1:1", data.get("baseUrl"));
    }

    @Test
    @DisplayName("doctor 连接不可达：gateway 检查 FAIL，整体 UNAVAILABLE（exit 3）而非 SUCCESS")
    void doctorUnreachableIsRed() {
        OperationRegistry registry = OperationRegistry.load();
        CliOptions options = CliOptions.parse(new String[] {"doctor", "--timeout", "800"}, 1);
        HttpTransport transport = new HttpTransport("http://127.0.0.1:1", "t", 800);

        Map<String, Object> out = DoctorCommand.run(registry, options, transport);

        assertEquals("UNAVAILABLE", out.get("status"));
        assertEquals(ExitCodes.UNAVAILABLE,
                ExitCodes.forStatus(CliStatus.valueOf(String.valueOf(out.get("status")))),
                "断网 doctor 必须退 3（UNAVAILABLE），不得降级成 FAILED/1");
        assertFalse((Boolean) out.get("ok"));
        Map<?, ?> data = (Map<?, ?>) out.get("data");
        List<?> checks = (List<?>) data.get("checks");
        assertEquals("FAIL", ((Map<?, ?>) checks.get(0)).get("status"));
    }

    @Test
    @DisplayName("capabilities list：14 条命令全登记，operationId 均可解析，L3 必带审批")
    void capabilitiesIsReadOnlyAndResolvable() {
        OperationRegistry registry = OperationRegistry.load();
        CliOptions options = CliOptions.parse(new String[] {"capabilities", "list"}, 2);
        Map<String, Object> out = CapabilitiesCommand.run(registry, options,
                new HttpTransport("http://127.0.0.1:1", "t", 500));

        assertEquals("SUCCESS", out.get("status"));
        List<?> commands = (List<?>) ((Map<?, ?>) out.get("data")).get("commands");
        assertEquals(17, commands.size(), "14 条设计命令 + doctor/capabilities list 两条 MVP 命令 + knowledge validate（D-WC-1）");
        // 计划 §一 完成判定：设计册 §10.3.5 的 14 条必须逐条在列（数量相等挡不住「删一条加一条」）
        List<String> design14 = List.of(
                "agent run", "agent inspect", "agent stop", "agent readiness",
                "ontology validate", "ontology publish", "knowledge extract", "knowledge publish",
                "cognition model validate", "cognition model run", "action commit",
                "model list", "model route", "doctor");
        List<String> actual = commands.stream()
                .map(c -> String.valueOf(((Map<?, ?>) c).get("name"))).toList();
        for (String name : design14) {
            assertTrue(actual.contains(name), "设计册 14 条命令缺失: " + name);
        }
        for (Object c : commands) {
            Map<?, ?> row = (Map<?, ?>) c;
            String name = String.valueOf(row.get("name"));
            String level = String.valueOf(row.get("automationLevel"));
            assertTrue(List.of("L0", "L1", "L2", "L3").contains(level), name + " 自动化等级非法: " + level);
            if (Boolean.TRUE.equals(row.get("requiresApproval"))) {
                assertEquals("L3", level, name + " 需要审批但不是 L3");
                assertEquals("WRITE", row.get("accessMode"), name + " 需要审批但不是写命令");
            }
            for (Object op : (List<?>) row.get("operations")) {
                String path = (String) ((Map<?, ?>) op).get("path");
                assertTrue(path.startsWith("/api/") || path.startsWith("/actuator/"), "路径越界: " + path);
            }
            // §一：契约未实现的命令不得自称可调用（wired=true 会误导用户，执行必然 UNAVAILABLE）
            // 纯本地命令（如 capabilities list）不挂契约端点，不参与该不变式
            List<?> ops = (List<?>) row.get("operations");
            if (!ops.isEmpty()) {
                boolean anyCallable = ops.stream().anyMatch(o -> {
                    String st = String.valueOf(((Map<?, ?>) o).get("status"));
                    return "implemented".equals(st) || "framework".equals(st);
                });
                assertEquals(anyCallable, Boolean.TRUE.equals(row.get("wired")),
                        name + " wired 与契约状态不一致（全 planned 的命令必须 wired=false）");
            }
        }
    }

    @Test
    @DisplayName("未接入命令输出 UNAVAILABLE（不返回空集合伪装成功）")
    void unwiredCommandsAreUnavailable() {
        OperationRegistry registry = OperationRegistry.load();
        for (CliCommand command : CliCommands.ALL) {
            if (command.wired()) {
                continue;
            }
            List<String> argv = new java.util.ArrayList<>(List.of(command.name().split(" ")));
            argv.addAll(List.of("--token", "t"));
            CliOptions options = CliOptions.parse(argv.toArray(new String[0]),
                    command.name().split(" ").length);
            Map<String, Object> out = com.chinacreator.gzcm.cli.commands.GenericCommandRunner.run(
                    registry, options, new HttpTransport("http://127.0.0.1:1", "t", 500), command);
            assertEquals("UNAVAILABLE", out.get("status"), command.name() + " 未接入却未报 UNAVAILABLE");
            assertNotNull(((Map<?, ?>) out.get("error")).get("code"), command.name() + " 缺错误码");
        }
    }

    @Test
    @DisplayName("cognition model run：UNAVAILABLE 的 reason 指向设计归属（执行权外部），不发请求")
    void cognitionModelRunDegradesWithDesignReason() {
        OperationRegistry registry = OperationRegistry.load();
        CliCommand run = CliCommands.find(CliCommands.COGNITION_MODEL_RUN);
        assertFalse(run.wired(), "cognition model run 在外部计算服务承接前不得接入");
        CliOptions options = CliOptions.parse(
                new String[] {"cognition", "model", "run", "--model-id", "m1", "--token", "t"}, 3);
        Map<String, Object> out = com.chinacreator.gzcm.cli.commands.GenericCommandRunner.run(
                registry, options, new HttpTransport("http://127.0.0.1:1", "t", 500), run);

        assertEquals("UNAVAILABLE", out.get("status"));
        Map<?, ?> error = (Map<?, ?>) out.get("error");
        String reason = String.valueOf(error.get("message"));
        assertTrue(reason.contains("§8.4.2"), "reason 必须点明设计归属依据: " + reason);
        assertTrue(reason.contains("planned"), "reason 必须点明契约状态: " + reason);
        List<?> refs = (List<?>) out.get("source_refs");
        assertEquals("startCognitiveModelRun",
                ((Map<?, ?>) refs.get(0)).get("operationId"), "source_refs 必须指向契约 operationId");
    }

    @Test
    @DisplayName("L3 已接入但缺 --approval-token ⇒ BLOCKED（本地不放行、不发请求）")
    void l3WithoutApprovalTokenIsBlocked() {
        OperationRegistry registry = OperationRegistry.load();
        // 用一份「已接入」的 L3 命令夹具：真命令在批 D 前 wired=false，走不到审批闸
        CliCommand syntheticL3 = new CliCommand("ontology publish", "fixture", CliCommands.ACCESS_WRITE,
                "L3", List.of("publishCandidate"), List.of(),
                List.of(new CliCommand.PathParam("--candidate-id", "publishCandidate", "candidateId", "id")),
                List.of(new CliCommand.BodyParam("--approval-token", "publishCandidate", "approval_token",
                        "token")),
                true, true);
        CliOptions options = CliOptions.parse(
                new String[] {"ontology", "publish", "--candidate-id", "c1", "--token", "t"}, 2);
        Map<String, Object> out = com.chinacreator.gzcm.cli.commands.GenericCommandRunner.run(
                registry, options, new HttpTransport("http://127.0.0.1:1", "t", 500), syntheticL3);
        assertEquals("BLOCKED", out.get("status"));
    }

    @Test
    @DisplayName("dry-run 打印调用计划且不构造真实请求（路径来自契约清单）")
    void dryRunShowsPlannedCallFromContract() {
        OperationRegistry registry = OperationRegistry.load();
        CliCommand inspect = CliCommands.find(CliCommands.AGENT_INSPECT);
        CliOptions options = CliOptions.parse(
                new String[] {"agent", "inspect", "--run-id", "R-1", "--dry-run"}, 2);
        Map<String, Object> out = com.chinacreator.gzcm.cli.commands.GenericCommandRunner.run(
                registry, options, new HttpTransport("http://127.0.0.1:1", "t", 500), inspect);
        assertEquals("SUCCESS", out.get("status"));
        String planned = String.valueOf(((Map<?, ?>) out.get("data")).get("plannedCall"));
        assertEquals("GET http://127.0.0.1:1" + registry.pathOf("getRun").replace("{runId}", "R-1"), planned);
    }
}
