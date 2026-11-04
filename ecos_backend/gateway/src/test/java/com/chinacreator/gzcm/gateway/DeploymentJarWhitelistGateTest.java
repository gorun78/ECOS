package com.chinacreator.gzcm.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 部署 JAR 白名单门（R7~R11 补册 §3.1 DD-1；#147AZ）。
 *
 * <p><b>它证明什么</b>：`_win_tasks/start-backend.ps1` 的「模块 → 端口 → JAR」表与
 * 微服务 v2 的 <b>7 个部署 JAR</b> 基线逐项一致——名称、端口、产物路径三者都不许漂。
 * 补册 §3.1 原先把这一项登记为「待补」：`ArchitectureTest` 只数模块总数（12），
 * 数不出「哪个模块是可部署 JAR、它叫什么、落在哪个端口、JAR 路径是否还指得对」。</p>
 *
 * <p><b>为什么钉脚本而不是钉构建产物</b>：`target/*.jar` 是构建中间态，干净检出时不存在，
 * 拿它做门禁会在 CI 里假红；而启动脚本是<b>本机/CI 拉起运行时的唯一入口</b>，把它的映射表
 * 钉死就能保证「拉起的就是这 7 个」。JAR 是否<b>新鲜</b>（内含最新字节码）属另一件事，
 * 由 `-Decos.verify.fatjar` 冒烟腿（#147W）负责，本门不管。</p>
 *
 * <p><b>它不证明什么</b>：不证明这 7 个 JAR 已构建、也不证明它们能启动。</p>
 *
 * @author ECOS gateway #147AZ
 */
class DeploymentJarWhitelistGateTest {

    /** 7 个部署 JAR 的基线：模块名 → 端口 → 产物相对路径（相对 ecos_backend/）。 */
    private static final Map<String, String[]> DEPLOYMENT_BASELINE = new LinkedHashMap<>();

    static {
        DEPLOYMENT_BASELINE.put("gateway", new String[]{"8080", "gateway\\target\\gateway-1.0.0-SNAPSHOT.jar"});
        DEPLOYMENT_BASELINE.put("sysman", new String[]{"18081", "services\\sysman\\target\\sysman-service-1.0.0-SNAPSHOT.jar"});
        DEPLOYMENT_BASELINE.put("datanet", new String[]{"18082", "services\\datanet\\target\\datanet-service-1.0.0-SNAPSHOT.jar"});
        DEPLOYMENT_BASELINE.put("buszhi", new String[]{"18083", "services\\buszhi\\target\\buszhi-service-1.0.0-SNAPSHOT.jar"});
        DEPLOYMENT_BASELINE.put("aiming", new String[]{"18084", "services\\aiming\\target\\aiming-service-1.0.0-SNAPSHOT.jar"});
        DEPLOYMENT_BASELINE.put("dccheng", new String[]{"18086", "services\\dccheng\\target\\dccheng-service-1.0.0-SNAPSHOT.jar"});
        DEPLOYMENT_BASELINE.put("workspace", new String[]{"18090", "workspace\\workspace-service\\target\\workspace-service-1.0.0-SNAPSHOT.jar"});
    }

    /** 匹配 `'name' = @{ Port = 8080;  Jar = "$Backend\...\x.jar" }`。 */
    private static final Pattern ENTRY = Pattern.compile(
            "'([a-z\\-]+)'\\s*=\\s*@\\{\\s*Port\\s*=\\s*(\\d+);\\s*Jar\\s*=\\s*\"\\$Backend\\\\([^\"]+)\"\\s*}");

    private static Path launchScript() {
        // surefire 工作目录 = ecos_backend/gateway，上溯两级为仓库根；
        // ecos.launch.script 仅用于变异自证（喂一份被改坏的副本），生产/CI 恒走默认路径。
        String override = System.getProperty("ecos.launch.script");
        Path script = override != null
                ? Paths.get(override)
                : Paths.get("..", "..", "_win_tasks", "start-backend.ps1").normalize();
        assertTrue(Files.isRegularFile(script),
                "找不到启动脚本（工作目录应为 ecos_backend/gateway）: " + script.toAbsolutePath());
        return script;
    }

    @Test
    @DisplayName("启动脚本的模块表与 7 个部署 JAR 基线逐项一致（名称/端口/产物路径）")
    void deploymentJarTableMatchesBaseline() throws IOException {
        String script = Files.readString(launchScript(), StandardCharsets.UTF_8).replace('/', '\\');

        Map<String, String[]> parsed = new LinkedHashMap<>();
        Matcher m = ENTRY.matcher(script);
        while (m.find()) {
            parsed.put(m.group(1), new String[]{m.group(2), m.group(3)});
        }

        for (Map.Entry<String, String[]> expected : DEPLOYMENT_BASELINE.entrySet()) {
            String module = expected.getKey();
            String[] want = expected.getValue();
            String[] got = parsed.get(module);
            assertTrue(got != null, "启动脚本缺少部署模块 '" + module + "'（实际有: " + parsed.keySet() + "）");
            assertEquals(want[0], got[0], module + " 的端口漂了");
            assertEquals(want[1], got[1], module + " 的 JAR 产物路径漂了（改名/改目录必须同批改本门与白名单）");
        }

        // 反向：生产模块之外的条目只允许 *-boot 调试引擎（13 选 N 口径）
        List<String> extra = parsed.keySet().stream()
                .filter(k -> !DEPLOYMENT_BASELINE.containsKey(k))
                .filter(k -> !k.endsWith("-boot"))
                .toList();
        assertEquals(List.of(), extra, "启动脚本出现了既非 7 个部署 JAR、也非 *-boot 调试引擎的条目");
        assertEquals(DEPLOYMENT_BASELINE.size() + 6, parsed.size(),
                "启动脚本条目数应为 7 生产 + 6 boot = 13（新增模块必须走裁决并同批改本门）");
    }
}
