package com.chinacreator.gzcm.sysman.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D104 门禁：World Model 场景的 CRUD 必须读写 PostgreSQL，不得用进程内 Map 冒充存储。
 *
 * <p><b>缺陷形态（2026-11-15 活体实证）</b>：{@code ScenarioController} 四个端点全部操作
 * 进程内 Map，构造函数还塞一条编造的默认场景；{@code ecos_control.ecos_wm_scenario} 里
 * 实存 4 行真实业务场景从未被读到。{@code POST} 返回 {@code success:true} 加 UUID，但重启即丢。</p>
 *
 * <p><b>先剥注释再判定</b>：本门禁与被测类的 javadoc 都会提到旧形态（否则读代码的人不知道
 * 为什么禁），若直接全文匹配会把「解释缺陷」误判成「重现缺陷」。故先剥行注释与块注释。</p>
 *
 * <p><b>口径按「存储 vs 载荷」二分</b>：<br>
 * ① {@code ConcurrentHashMap} 全域禁用 —— 本控制器没有任何合法用途；<br>
 * ② 其余 Map 只禁<b>字段级声明</b>（缩进的 private 字段行里出现 Map 泛型），不禁方法内
 * 局部 {@code Map} 组响应体 —— 那是载荷不是存储（首版按「任何 new Map」判，把响应体
 * {@code result} 误判成红，属门禁误报）。</p>
 *
 * <p><b>探针史</b>：曾用全限定名 {@code new java.util.concurrent.ConcurrentHashMap<>()} 注入，
 * 首版断言只匹配短名 ⇒ 没转红（门禁自身假绿）；改为按 Map 实现名匹配后转红。</p>
 */
@DisplayName("D104：World Model 场景写面必须落库（禁内存 Map 冒充存储）")
class WorldModelScenarioNoMemoryStoreGateTest {

    private static final Path CONTROLLER = Path.of(
            "src/main/java/com/chinacreator/gzcm/sysman/controller/ScenarioController.java");

    private static String rawSource() throws Exception {
        Path p = Files.exists(CONTROLLER) ? CONTROLLER
                : Path.of("ecos_backend/services/sysman/impl/sysman-impl").resolve(CONTROLLER);
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    /** 剥掉行注释与块注释，只留可执行代码面。 */
    private static String codeOnly() throws Exception {
        String src = rawSource();
        StringBuilder out = new StringBuilder(src.length());
        int i = 0;
        int n = src.length();
        while (i < n) {
            if (src.startsWith("/*", i)) {
                int end = src.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 2;
                continue;
            }
            if (src.startsWith("//", i)) {
                int end = src.indexOf('\n', i + 2);
                i = end < 0 ? n : end;
                continue;
            }
            out.append(src.charAt(i++));
        }
        return out.toString();
    }

    /** 字段级 Map 声明：private [final] [限定名]XXMap<...> 字段名。 */
    private static boolean isMapFieldDecl(String line) {
        String t = line.trim();
        if (!t.endsWith(";")) {
            return false;   // 字段声明以分号结尾；方法签名以 { 结尾
        }
        if (!t.startsWith("private ")) {
            return false;
        }
        return t.contains("Map<");
    }

    private static List<String> mapFieldDecls(String code) {
        return code.lines().filter(WorldModelScenarioNoMemoryStoreGateTest::isMapFieldDecl)
                .collect(Collectors.toList());
    }

    @Test
    @DisplayName("可执行代码面不得有 ConcurrentHashMap，也不得有字段级 Map 存储")
    void noInMemoryWriteStores() throws Exception {
        String code = codeOnly();
        assertFalse(code.contains("ConcurrentHashMap"),
                "场景存储不得用 ConcurrentHashMap，读写一律走 WorldModelScenarioDao");
        List<String> fields = mapFieldDecls(code);
        assertTrue(fields.isEmpty(),
                "场景存储不得用字段级 Map（命中: " + fields + "），读写一律走 WorldModelScenarioDao");
        assertFalse(code.contains("default-scenario"),
                "不得再用 default-scenario 假数据兜底冒充真实场景");
        assertFalse(code.contains("默认场景"),
                "不得再构造假场景条目（假数据族）");
    }

    @Test
    @DisplayName("五个 CRUD 端点必须经由 scenarioDao（正向断言）")
    void endpointsUseDao() throws Exception {
        String code = codeOnly();
        assertTrue(code.contains("scenarioDao.list("), "列表必须查库");
        assertTrue(code.contains("scenarioDao.find("), "详情/存在性校验必须查库");
        assertTrue(code.contains("scenarioDao.insert("), "建场景必须落库");
        assertTrue(code.contains("scenarioDao.update("), "改场景必须落库");
        assertTrue(code.contains("scenarioDao.delete("), "删场景必须落库");
    }

    @Test
    @DisplayName("反向探针自检：短名 / 全限定名 / 字段级 Map 都命中，局部响应体不误判")
    void probeRecognizesLegacyShape() {
        String shortForm = "    private final ConcurrentHashMap<String, ScenarioEntity> store = new ConcurrentHashMap<>();";
        assertTrue(shortForm.contains("ConcurrentHashMap"), "短名形态必须命中");
        String fqcnForm = "    private final java.util.concurrent.ConcurrentHashMap<String, ScenarioEntity> store = new java.util.concurrent.ConcurrentHashMap<>();";
        assertTrue(fqcnForm.contains("ConcurrentHashMap"), "全限定名形态也必须命中");
        String mapField = "    private final Map<String, ScenarioEntity> store = new LinkedHashMap<>();";
        assertTrue(isMapFieldDecl(mapField), "字段级 Map 形态必须命中");
        String payloadLocal = "        Map<String, Object> result = new LinkedHashMap<>();";
        assertFalse(isMapFieldDecl(payloadLocal),
                "方法内响应体不得被误判（局部变量非 private 字段）");
        String legacyDefault = "defaultScenario.setId(\"default-scenario\");";
        assertTrue(legacyDefault.contains("default-scenario"), "假数据串必须命中");
        assertFalse(legacyDefault.contains("scenarioDao."),
                "旧写面不含 scenarioDao 前缀 => 正向断言在回退后必然转红");
    }
}
