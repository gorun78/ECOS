package com.chinacreator.gzcm.engine.ai.service;

import com.chinacreator.gzcm.common.security.filter.RowFilterVerdictContract;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * {@link ToolExecutorService} 反射腿（R1.3 沙盒审查）的测试替身（#142/B4-3e-7，D-B4-42 分态验收）。
 *
 * <p><b>为什么必须是"顶层 public 类 + 类名以 SecuritySandboxService 结尾"</b>：
 * 生产侧真身位于 security-engine-impl，编译期对 ai-engine 不可见，
 * 咽喉用 {@code getBeansOfType(Object.class)} 按类名后缀找 bean，
 * 再 {@code getClass().getMethod("evaluateFilter", String.class, Map.class, String.class)} 反射调用。
 * 包私有嵌套类会被 {@code getMethod} 拒掉（IllegalAccessException 系），
 * 于是"替身根本没被用上"会伪装成"服务不可用 ⇒ FAIL_CLOSED"，把本批要验的分态验成恒真。
 * 真签名锚定在 security-engine 侧的
 * {@code SecuritySandboxRowFilterVerdictTest#reflectionLegContractIsPinned}。</p>
 *
 * <p>三个应答都由 {@link RowFilterVerdictContract} 工厂组装 —— 替身不自造键面，
 * 否则键面漂移时本类仍绿而生产早已失配。</p>
 */
public class SandboxStubSecuritySandboxService {

    /** 应答段生成器：入参为被测表达式，返回契约工厂产出的应答段。 */
    private final Function<String, Map<String, Object>> responder;

    /** 反射腿实际收到的参数留痕，用于断言"确实走到了沙盒"。 */
    public volatile String lastExpression;
    public volatile Map<String, Object> lastRowData;
    public volatile String lastUserRole;
    public volatile int calls;

    public SandboxStubSecuritySandboxService(Function<String, Map<String, Object>> responder) {
        this.responder = responder;
    }

    /** 放行应答（判定完成＋allowed=true）。 */
    public static SandboxStubSecuritySandboxService allow() {
        return new SandboxStubSecuritySandboxService(
                expr -> RowFilterVerdictContract.evaluated(true, expr));
    }

    /** 策略拒绝应答（判定完成＋allowed=false，success=true）。 */
    public static SandboxStubSecuritySandboxService policyDeny() {
        return new SandboxStubSecuritySandboxService(
                expr -> RowFilterVerdictContract.evaluated(false, expr));
    }

    /** 引擎故障应答（外形与拒绝一致，但 success=false＋error）。 */
    public static SandboxStubSecuritySandboxService engineFailure() {
        return new SandboxStubSecuritySandboxService(
                expr -> RowFilterVerdictContract.evaluationFailed("Evaluation error: stub boom"));
    }

    /** 收口前的旧生产形态：只有 allowed，没有 success —— 必须读成不可用。 */
    public static SandboxStubSecuritySandboxService legacyAllowedOnlyShape() {
        return new SandboxStubSecuritySandboxService(expr -> {
            Map<String, Object> old = new LinkedHashMap<>();
            old.put(RowFilterVerdictContract.KEY_ALLOWED, true);
            return old;
        });
    }

    /** 与生产侧同签名：反射按 {@code (String, Map, String)} 查找本方法。 */
    public Map<String, Object> evaluateFilter(String expression,
                                             Map<String, Object> rowData,
                                             String userRole) {
        calls++;
        lastExpression = expression;
        lastRowData = rowData;
        lastUserRole = userRole;
        return responder.apply(expression);
    }
}
