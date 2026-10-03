package com.chinacreator.gzcm.engine.data;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.chinacreator.gzcm.engine.data.service.DataLineageService;

/**
 * C.9 性能与容量基线「血缘重建 单次 ≤2000 定义」离线护栏（source-scan）。
 *
 * <p>doc 行 410 C.9 行 4：单次 ≤2000 定义；增量优先；{@code LIMIT} 参数化。校訂二十九 曾判
 * "未落地硬上限、留待后续 PMO 批次"。本条按设计原文
 * （"加 {@code Math.min(limit, 2000)} 硬钳或默认 2000"）在
 * {@link DataLineageService#scanDefinitions()} 落地：SQL 走参数化
 * {@code LIMIT ?}、参数硬钳 {@code MAX_REBUILD_DEFINITIONS=2000}，超限 WARN 提示走增量
 * （增量语义本身属更大范围的重构，本窗只闭合可单窗口落地的"硬上限 + 参数化 + 提示"三面）。
 *
 * <p><b>为何走源码扫描而非 Mockito 走 {@code rebuildAndPersist}</b>：
 * {@code scanDefinitions} 是私有方法且内嵌于 synchronized {@code rebuildAndPersist}
 * 的重入式迭代，若走 Mockito 桩 JdbcTemplate 观察 SQL 参数需再桩 SqlLineageParser +
 * ObjectMapper 分支以让 2000 次 {@code scanNodesFromTable} 之后方法完整退出；
 * 本护栏锁的是"常量 + 应用位置"契约面（同 W43 {@code RouteManifestParityTest} /
 * W50 {@code SchemaInventoryGateTest} / W59 {@code DdlComplianceLintTest}
 * 的"源文本 anchor + 字面"离线护栏范式），非运行时行为。
 *
 * <p>断言（三证防静默空转）：
 * <ol>
 *   <li>源文件定义字面常量 {@code MAX_REBUILD_DEFINITIONS = 2000}；</li>
 *   <li>{@code scanDefinitions} 方法体内 SQL 含参数化占位符 {@code LIMIT ?}
 *       （IR05 禁 {@code LIMIT " + var} 拼接）；</li>
 *   <li>{@code scanDefinitions} 方法体明确把 {@code MAX_REBUILD_DEFINITIONS}
 *       作为 SQL 参数位传入（常量闲置 = 护栏空转静默绿的白线反证）。</li>
 * </ol>
 */
@DisplayName("C.9 行 4 血缘重建单次 ≤2000 定义 硬钳(source-scan)")
class LineageRebuildDefinitionCapArchTest {

    private static final String SRC_REL = "com/chinacreator/gzcm/engine/data/service/DataLineageService.java";

    @Test
    void scanDefinitionsHardCappedAt2000AndParameterized() throws IOException {
        Path src = RlsInjectionGuardArchTest.resolveModuleSrcMainJava().resolve(SRC_REL);
        assertTrue(Files.isRegularFile(src), "DataLineageService.java 应存在于源码树: " + src);
        String text = Files.readString(src, StandardCharsets.UTF_8);

        // 证 1：常量存在且字面 = 2000
        Matcher constM = Pattern.compile(
                "private\\s+static\\s+final\\s+int\\s+MAX_REBUILD_DEFINITIONS\\s*=\\s*2000\\s*;")
                .matcher(text);
        assertTrue(constM.find(),
                "应定义 private static final int MAX_REBUILD_DEFINITIONS = 2000;（C.9 行 4 硬钳基线）");

        // 定位 scanDefinitions 方法体
        Matcher methodM = Pattern.compile(
                "private\\s+List<\\s*Map<\\s*String\\s*,\\s*Object\\s*>>\\s+scanDefinitions\\s*\\(\\s*\\)\\s*\\{"
                        + "(.*?)\\n    \\}",
                Pattern.DOTALL).matcher(text);
        assertTrue(methodM.find(), "应找到方法体 private List<Map<String,Object>> scanDefinitions()");
        String body = methodM.group(1);

        // 证 2：SQL 参数化 —— 存在 `LIMIT ?` 占位符，且不存在 `LIMIT " + var` 拼接（IR05）
        assertTrue(Pattern.compile("LIMIT\\s*\\?").matcher(body).find(),
                "scanDefinitions SQL 应使用参数化占位符 `LIMIT ?`（C.9 行 4 / IR05）");
        assertFalse(Pattern.compile("LIMIT\\s*\"\\s*\\+").matcher(body).find(),
                "scanDefinitions SQL 禁 LIMIT 字面量 + Java 变量拼接（IR05 违规，已有的 D-23 家族风险）");

        // 证 3：MAX_REBUILD_DEFINITIONS 在 scanDefinitions 方法体真实出现（防止常数闲置）
        assertTrue(body.contains("MAX_REBUILD_DEFINITIONS"),
                "scanDefinitions 方法体应实引 MAX_REBUILD_DEFINITIONS（作为 SQL 参数或阈值比较），"
                        + "防止常量定义后未接入导致护栏空转");
    }
}
