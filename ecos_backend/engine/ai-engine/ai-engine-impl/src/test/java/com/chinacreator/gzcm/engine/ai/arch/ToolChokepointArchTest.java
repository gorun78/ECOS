package com.chinacreator.gzcm.engine.ai.arch;

import com.chinacreator.gzcm.engine.ai.service.ToolExecutorService;
import com.chinacreator.gzcm.engine.ai.service.ToolRegistry;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameContaining;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工具裁决咽喉防绕行守门（详细设计-06 F06-01 要点 5 · REQ-AI-01 · 铁律 §2.4-4/§2.4-6）。
 *
 * <p>F06-01 要点 5 原文："除 {@code ToolExecutorService} 外，任何类不得调用
 * {@code ToolRegistry#execute} 与 {@code *ToolExecutor#executeBuiltin}（防绕过咽喉）"。
 * 裁决咽喉（policyEvaluate → sandboxReview → dispatch → obligationsApply）只存在于
 * {@link ToolExecutorService#execute}，任何第二个入口都会扩大绕过面（禁新建
 * {@code ToolDispatcher}，要点 1），故以 bytecode 级 ArchUnit 规则固化。</p>
 *
 * <p>规则形态：{@code noClasses().that().doNotHaveFullyQualifiedName(ToolExecutorService)}
 * should {@code callMethodWhere}（owner = {@code ToolRegistry} 且方法名 = {@code execute}，
 * 或 owner simpleName 匹配 {@code *ToolExecutor*} 且方法名 = {@code executeBuiltin}）。</p>
 *
 * <p><b>正对照（防空转）</b>：{@link #toolExecutorServiceIsTheOnlyCallerOfToolRegistryExecute()}
 * 断言咽喉类自身确实调用 {@code ToolRegistry#execute}（现网 file:line =
 * {@code ToolExecutorService.java:172} 阶段 3 dispatch）。若正对照失败即咽喉形态已漂移，
 * 必须显式失败并触发规则复核，不允许规则悄悄变成永真。</p>
 *
 * <p>分析域 = {@code com.chinacreator.gzcm.engine.ai} 的<b>生产</b>类
 * （{@code ImportOption.DoNotIncludeTests} 排除 test-classes：测试夹具直调
 * {@code ToolRegistry#execute} 属合法 test double 手段，与
 * {@link DataAccessArchTest} 的 haveNameNotMatching 排除口径同源，不约束测试面）。</p>
 */
@AnalyzeClasses(packages = "com.chinacreator.gzcm.engine.ai",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ToolChokepointArchTest {

    /** 咽喉类 FQN（唯一豁免者，只增不缩）。 */
    private static final String CHOKEPOINT_FQN = ToolExecutorService.class.getName();

    /** 调用 ToolRegistry#execute（任意重载/任意参数）。 */
    private static final DescribedPredicate<JavaCall<?>> CALLS_TOOL_REGISTRY_EXECUTE =
            target(owner(name(ToolRegistry.class.getName())))
                    .and(target(name("execute")));

    /** 调用 *ToolExecutor*#executeBuiltin（内置工具执行的第二潜在入口）。 */
    private static final DescribedPredicate<JavaCall<?>> CALLS_TOOL_EXECUTOR_EXECUTE_BUILTIN =
            target(owner(simpleNameContaining("ToolExecutor")))
                    .and(target(name("executeBuiltin")));

    @ArchTest
    static final ArchRule noBypassOfToolExecutorService =
            noClasses().that()
                    .doNotHaveFullyQualifiedName(CHOKEPOINT_FQN)
                    .should().callMethodWhere(CALLS_TOOL_REGISTRY_EXECUTE.or(CALLS_TOOL_EXECUTOR_EXECUTE_BUILTIN))
                    .as("F06-01 要点 5：除 ToolExecutorService 外，任何类不得调用 ToolRegistry#execute "
                            + "与 *ToolExecutor#executeBuiltin（防绕过裁决咽喉）")
                    .because("裁决（ALLOW/DENY/FAIL_CLOSED + 审计）只在咽喉处发生一次；"
                            + "出现第二个执行入口 = 未裁决执行，违反铁律 §2.4-4/§2.4-6 与 §1.5-1 对账口径");

    /**
     * 正对照：咽喉类自身必须调用 {@code ToolRegistry#execute}（阶段 3 dispatch）。
     * 失败 = 咽喉形态漂移（改名/拆类/换直调），此时上方 noBypass 规则可能已空转，
     * 必须与规则同批修订并重新评审，禁止静默放宽。
     */
    @Test
    void toolExecutorServiceIsTheOnlyCallerOfToolRegistryExecute() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.chinacreator.gzcm.engine.ai");
        JavaClass chokepoint = classes.get(CHOKEPOINT_FQN);
        boolean callsRegistryExecute = chokepoint.getMethodCallsFromSelf().stream()
                .anyMatch(CALLS_TOOL_REGISTRY_EXECUTE::test);
        assertTrue(callsRegistryExecute,
                "正对照失败：" + CHOKEPOINT_FQN + " 不再调用 ToolRegistry#execute —— "
                        + "裁决咽喉形态已变化（F06-01），noBypassOfToolExecutorService 规则可能空转，"
                        + "请连同本测试一起修订");
    }
}
