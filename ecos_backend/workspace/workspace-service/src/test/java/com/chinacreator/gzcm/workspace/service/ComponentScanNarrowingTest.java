package com.chinacreator.gzcm.workspace.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 卷07 F07-16（W180）—— :18090 启动器扫描面守门（X-6 / X-7 的冻结基线）。
 *
 * <p>AGENTS.md 的"新增 Controller 必过三滤波器"与本册三层纪律都要求
 * workspace 独立进程的装配面<b>可枚举、可对比</b>：gateway 单体态
 * （{@code GatewayApplication}）与 workspace 独立态（本类）扫的不是同一堆包，
 * 生产可达面就会漂移（X-3/X-6）。本类把当前的跨模块扫描集<b>冻结成显式清单</b>：
 * 任何新增的 engine/buszhi/sysman 包扫描都会在这里失败，除非先按 R-29/F07-16
 * 走完 DecisionGateway（两态 invoker）改造并同步基线。</p>
 */
class ComponentScanNarrowingTest {

    /**
     * 冻结基线（卷07 F07-16 落地前的事实快照，只允许收缩不允许增长）。
     *
     * <p>每一项的存在理由：
     * <ul>
     *   <li>{@code workspace} / {@code common} / {@code runtime} / {@code runtime.llm}
     *       —— workspace 自有面 + 横切底座（合规）</li>
     *   <li>{@code buszhi} —— ObjectRuntimeService / StateMachineEngine 真实 bean
     *       （R-29 已裁决 objects/query 面留在 :18090）</li>
     *   <li>{@code engine.ontology} —— IFunctionPropertyEvaluator / IActionHookExecutor
     *       真实 bean（PMO-50 决策 2 G4 断链）</li>
     *   <li>{@code sysman.config} —— SysConfigService 具体类被引擎 controller 注入</li>
     *   <li>{@code engine.data.service} / {@code engine.data.repository}
     *       —— ICopilotService 实现链</li>
     *   <li>{@code engine.data.dao} —— R2 数据访问收口后 CopilotServiceImpl 构造依赖的
     *       CopilotDao 等 JdbcTemplate @Repository（2026-10-11 契约核验实修，
     *       按本类「确属必要请同步更新本基线」出口挂 W180）</li>
     * </ul>
     */
    private static final Set<String> FROZEN_SCAN_BASELINE = Set.of(
            "com.chinacreator.gzcm.workspace",
            "com.chinacreator.gzcm.buszhi",
            "com.chinacreator.gzcm.common",
            "com.chinacreator.gzcm.engine.ontology",
            "com.chinacreator.gzcm.sysman.config",
            "com.chinacreator.gzcm.engine.data.service",
            "com.chinacreator.gzcm.engine.data.repository",
            "com.chinacreator.gzcm.engine.data.dao",
            "com.chinacreator.gzcm.runtime",
            "com.chinacreator.gzcm.runtime.llm"
    );

    /** 完全属于 workspace 自有 + 底座的包 —— 收窄终态只允许这些。 */
    private static final Set<String> OWN_OR_RUNTIME = Set.of(
            "com.chinacreator.gzcm.workspace",
            "com.chinacreator.gzcm.common",
            "com.chinacreator.gzcm.runtime",
            "com.chinacreator.gzcm.runtime.llm"
    );

    private static ComponentScan scan() {
        ComponentScan ann = WorkspaceServiceApplication.class.getAnnotation(ComponentScan.class);
        assertNotNull(ann, "WorkspaceServiceApplication 必须显式声明 @ComponentScan（否则扫描面不可审计）");
        return ann;
    }

    @Test
    @DisplayName("扫描面必须等于冻结基线，且绝不包含 gateway 包")
    void workspaceApplicationScansOnlyOwnPackagesAndRuntime() {
        Set<String> actual = new LinkedHashSet<>(Arrays.asList(scan().basePackages()));

        Set<String> grown = new LinkedHashSet<>(actual);
        grown.removeAll(FROZEN_SCAN_BASELINE);
        assertTrue(grown.isEmpty(),
                "F07-16 三层纪律：:18090 启动器新增了对其它模块包的组件扫描 " + grown
                        + "。跨引擎能力必须走 *-api 门面 + DecisionGateway 两态 invoker，"
                        + "而不是把对方实现包扫进本进程。确属必要请同步更新本基线并挂 W180。");

        Set<String> removed = new LinkedHashSet<>(FROZEN_SCAN_BASELINE);
        removed.removeAll(actual);
        if (!removed.isEmpty()) {
            System.out.println("[F07-16 收窄进度] 已相对基线撤掉的跨模块扫描包: " + removed
                    + " —— 这正是目标态方向，请同步把 FROZEN_SCAN_BASELINE 收紧。");
        }

        for (String p : actual) {
            assertFalse(p.startsWith("com.chinacreator.gzcm.gateway"),
                    ":18090 禁止扫描 gateway 包（monolith 装配面不得回流独立进程）: " + p);
        }

        // 收窄终态的进度可观测：还剩几个跨模块包
        List<String> crossModule = actual.stream().filter(p -> !OWN_OR_RUNTIME.contains(p)).toList();
        System.out.println("[F07-16 收窄进度] 跨模块扫描包 " + crossModule.size() + " / " + actual.size()
                + " 项: " + crossModule);
    }

    /** 排除过滤器禁止再出现编译期跨模块 class 引用（X-6 的隐藏依赖）。 */
    @Test
    @DisplayName("excludeFilters 必须是字符串/正则，禁 ASSIGNABLE_TYPE 引用他模块类")
    void excludeFiltersMustNotCarryCompileTimeCrossModuleRefs() {
        for (ComponentScan.Filter f : scan().excludeFilters()) {
            assertNotAssignableToOtherModule(f);
        }
    }

    private static void assertNotAssignableToOtherModule(ComponentScan.Filter f) {
        if (f.type() != FilterType.ASSIGNABLE_TYPE) return;
        for (Class<?> c : f.classes()) {
            String pkg = c.getPackageName();
            assertFalse(pkg.startsWith("com.chinacreator.gzcm.engine")
                            || pkg.startsWith("com.chinacreator.gzcm.buszhi")
                            || pkg.startsWith("com.chinacreator.gzcm.sysman")
                            || pkg.startsWith("com.chinacreator.gzcm.services")
                            || pkg.startsWith("com.chinacreator.gzcm.gateway"),
                    "@ComponentScan 排除项用 ASSIGNABLE_TYPE 引用了他模块类 " + c.getName()
                            + " ⇒ 本 boot 类编译期就依赖 *-impl，分层拆不掉。改 FilterType.REGEX + 包名字符串。");
        }
    }

    /** X-7：workspace 的 @MapperScan 必须只指向显式存在的收口包（禁三条并存通配）。 */
    @Test
    @DisplayName("@MapperScan 的 workspace 段必须收成显式 mapper 包")
    void workspaceMapperScanIsExplicitNotDeadGlobs() {
        MapperScan ms = WorkspaceServiceApplication.class.getAnnotation(MapperScan.class);
        assertNotNull(ms, "WorkspaceServiceApplication 必须声明 @MapperScan");
        List<String> workspacePatterns = Arrays.stream(ms.value())
                .filter(p -> p.startsWith("com.chinacreator.gzcm.workspace"))
                .toList();
        assertEquals(List.of("com.chinacreator.gzcm.workspace.mapper"), workspacePatterns,
                "X-7：workspace 曾有 **.repository/**.dao/**.mapper 三条通配但 0 个 mapper 接口。"
                        + "现收为显式单包 com.chinacreator.gzcm.workspace.mapper，"
                        + "新增 mapper 必须落该包；通配"
                        + "让\"没有 mapper\"看起来像\"已收口\"。");
    }
}
