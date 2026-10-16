package com.chinacreator.gzcm.engine.security.rolechannel;

import com.chinacreator.gzcm.common.security.decision.DataScopeContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 请求数据域键面的<b>单源门禁</b>（#142/B4-3d-3，差距 D-B4-20；与 D-B4-17 的
 * {@code RlsExecutionContractGateTest}、D-B4-25 的 {@code ClsColumnSurfaceContractGateTest} 同族）。
 *
 * <p><b>钉什么</b>：{@code "dataScope"} 与 {@code "projectIds"} 两个字面量在
 * {@code ecos_backend/**&#47;src/main/java/**} 内<b>只允许</b>出现在单源
 * {@link DataScopeContract} 里。生产者（{@code SecurityDecisionService}）、入口
 * （{@code DecisionController}）、AI 载荷（{@code ToolAdjudicationRequest}）一律委托常量。</p>
 *
 * <p><b>为什么只钉这两个</b>：{@code "departmentId"} 在其它无关域是常用列名/字段名
 * （组织、人员、资产目录等主源码里大量出现），把它钉进本门禁会得到一份与策略键面无关的
 * 违例清单，反而稀释信号 —— 该键的收敛由"写出侧只剩 {@code toResourceSegment} 一处"
 * 结构性保证（{@code DecisionDataScopePayloadTest} 断发出形态）。这是<b>刻意取舍</b>，
 * 不是遗漏；后续若要扩 {@code departmentId}，须先给无关命中做属主判定再扩白名单。</p>
 *
 * <p><b>只扫主源码</b>：测试侧允许出现键名字面量作为独立见证（否则改常量名时
 * 生产者与断言同步漂移＝假绿），见 {@code DataScopeContractTest} 类注释。</p>
 *
 * <p><b>假绿防护</b>：扫描根解析不到文件即失败（"0 个文件"不等于"0 处违例"）；
 * 同时复核策略单源 {@code data_access.rego} 确实在读这两个键，防止门禁与策略同时漂移成
 * "两边都不再有该键"这种自洽空转。</p>
 */
class DataScopeSingleSourceGateTest {

    /** 唯一允许持有该键面字面量的主源码文件（按仓根相对路径比对，跨机不依赖绝对路径）。 */
    private static final String SOLE_OWNER_SUFFIX =
            "runtime/common-api/src/main/java/com/chinacreator/gzcm/common/security/decision/DataScopeContract.java";

    private static Path ecosBackendRoot() throws IOException {
        return OpaOracleReplaySupport.repoFile("ecos_backend/pom.xml").getParent();
    }

    @Test
    @DisplayName("dataScope/projectIds 字面量单源：主源码内除 DataScopeContract 外零持有")
    void guardrailScopeKeysHaveSingleOwner() throws IOException {
        Path root = ecosBackendRoot();
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(p -> p.getFileName().toString().endsWith(".java"))
                    .filter(p -> p.toString().replace('\\', '/').contains("/src/main/java/"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/target/"))
                    .toList();
        }
        // 假绿防护①：扫描根必须真吃到文件
        assertTrue(files.size() > 500,
                "主源码扫描根解析到的文件数异常（" + files.size() + "）——「读不到」不得当成「无违例」: " + root);

        List<String> violations = new ArrayList<>();
        for (Path f : files) {
            String norm = f.toString().replace('\\', '/');
            if (norm.endsWith(SOLE_OWNER_SUFFIX)) continue;
            List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.contains("\"dataScope\"") || line.contains("\"projectIds\"")) {
                    violations.add(norm.substring(norm.indexOf("ecos_backend/"))
                            + ":" + (i + 1) + " → " + line.trim());
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "发现第二套数据域键面字面量（键名两侧各写各的正是 D-B4-25/D-B4-20 的根因）:\n"
                        + String.join("\n", violations));
    }

    @Test
    @DisplayName("单源常量取值与策略侧读取的键名逐字一致（防门禁与策略同时漂移）")
    void contractKeysMatchPolicySource() throws IOException {
        String rego = OpaOracleReplaySupport.regoText();
        // 假绿防护②：策略全文必须仍在读这两个键，否则"零违例"只是键面消失的假象
        assertTrue(rego.contains("\"" + DataScopeContract.KEY_DATA_SCOPE + "\""),
                "data_access.rego 不再引用 dataScope ⇒ 本门禁与 D-B4-20 的验收依据同时失效");
        assertTrue(rego.contains("\"" + DataScopeContract.KEY_PROJECT_IDS + "\""));
        assertTrue(rego.contains("\"" + DataScopeContract.KEY_DEPARTMENT_ID + "\""));
        assertEquals("dataScope", DataScopeContract.KEY_DATA_SCOPE);
    }

    @Test
    @DisplayName("三处委托位真实存在（门禁不得靠无人引用而空转变绿）")
    void delegatedCallersAreWired() throws IOException {
        String svc = Files.readString(OpaOracleReplaySupport.repoFile(
                "ecos_backend/engine/security-engine/security-engine-impl/src/main/java/"
                        + "com/chinacreator/gzcm/engine/security/decision/SecurityDecisionService.java"));
        String ctl = Files.readString(OpaOracleReplaySupport.repoFile(
                "ecos_backend/engine/security-engine/security-engine-impl/src/main/java/"
                        + "com/chinacreator/gzcm/engine/security/controller/DecisionController.java"));
        String tool = Files.readString(OpaOracleReplaySupport.repoFile(
                "ecos_backend/engine/ai-engine/ai-engine-api/src/main/java/"
                        + "com/chinacreator/gzcm/engine/ai/guardrail/ToolAdjudicationRequest.java"));
        assertTrue(svc.contains("DataScopeContract.toResourceSegment"), "生产者未委托写出侧单源");
        assertTrue(ctl.contains("DataScopeContract.read"), "入口未委托读数侧单源");
        assertTrue(tool.contains("DataScopeContract.KEY_PROJECT_IDS")
                        && tool.contains("DataScopeContract.KEY_DATA_SCOPE"),
                "AI 载荷未委托键面（那意味着第二套字面量回来了）");
    }
}
