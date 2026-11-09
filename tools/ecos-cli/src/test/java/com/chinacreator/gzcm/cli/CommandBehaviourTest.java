package com.chinacreator.gzcm.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    @DisplayName("doctor 连接不可达：gateway 检查 FAIL，整体不得 SUCCESS")
    void doctorUnreachableIsRed() {
        OperationRegistry registry = OperationRegistry.load();
        CliOptions options = CliOptions.parse(new String[] {"doctor", "--timeout", "800"}, 1);
        HttpTransport transport = new HttpTransport("http://127.0.0.1:1", "t", 800);

        Map<String, Object> out = DoctorCommand.run(registry, options, transport);

        assertEquals("FAILED", out.get("status"));
        assertFalse((Boolean) out.get("ok"));
        Map<?, ?> data = (Map<?, ?>) out.get("data");
        List<?> checks = (List<?>) data.get("checks");
        assertEquals("FAIL", ((Map<?, ?>) checks.get(0)).get("status"));
    }

    @Test
    @DisplayName("capabilities list：命令全部 READ，且每个 operationId 都能在清单解析出路径")
    void capabilitiesIsReadOnlyAndResolvable() {
        OperationRegistry registry = OperationRegistry.load();
        CliOptions options = CliOptions.parse(new String[] {"capabilities", "list"}, 2);
        Map<String, Object> out = CapabilitiesCommand.run(registry, options,
                new HttpTransport("http://127.0.0.1:1", "t", 500));

        assertEquals("SUCCESS", out.get("status"));
        List<?> commands = (List<?>) ((Map<?, ?>) out.get("data")).get("commands");
        assertEquals(3, commands.size());
        for (Object c : commands) {
            Map<?, ?> row = (Map<?, ?>) c;
            assertEquals("READ", row.get("accessMode"));
            for (Object op : (List<?>) row.get("operations")) {
                String path = (String) ((Map<?, ?>) op).get("path");
                assertTrue(path.startsWith("/api/") || path.startsWith("/actuator/"), "路径越界: " + path);
            }
        }
    }
}
