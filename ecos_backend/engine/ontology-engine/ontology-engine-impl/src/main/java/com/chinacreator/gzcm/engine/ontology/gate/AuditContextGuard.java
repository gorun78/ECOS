package com.chinacreator.gzcm.engine.ontology.gate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.chinacreator.gzcm.common.context.TraceContext;

/**
 * F03-06 审计上下文守卫（W81/C64，REQ-ONTO-04 / PRD-03 §4.1-2）—— P0。
 *
 * <p>现状违例（O-14①）：{@code FunctionController} 的 {@code callerId} 缺省时回落
 * {@code "anonymous"} 继续执行 ⇒ 审计主体被污染、无法归因。PRD-03 §4.1-2 要求
 * "审计上下文缺失 → <b>400 拒收</b>（无审计上下文即不执行）"。
 *
 * <p>本守卫只补"审计上下文守卫"这一半（<b>不动执行器/沙箱边界</b>，本册 F03-06 明列）：
 * 执行入口在 security/validator/engine 之前先 {@link #require(String)}，
 * 上下文不可归因即抛 {@link ResponseStatusException}(400)，不进入执行：
 * <ul>
 *   <li><b>operator 缺失</b>：{@code callerId} 为 null / 空白 / {@code "anonymous"}
 *       （即旧缺省哨兵）—— PRD §4.1-2 语义"无 operator"；</li>
 *   <li><b>traceId 缺失</b>：{@link TraceContext#current()} 为 null（MDC 无 traceId，
 *       entry filter 未跑 ⇒ 链路不可追踪）。</li>
 * </ul>
 *
 * <p>HTTP 状态 <b>400</b>，且异常 message 显式携带错误码字面
 * {@code ECOS-ONTO-060}（沿用 ECOS 错误码落消息字面惯例，同分册 02 ECOS-DATA-013 等），
 * 前端/D.6 可直取 code。
 */
@Component
public class AuditContextGuard {

    private static final Logger log = LoggerFactory.getLogger(AuditContextGuard.class);

    /** D.6 错误码：Function 审计上下文缺失（无 operator/traceId），400。 */
    public static final String CODE_AUDIT_CONTEXT_MISSING = "ECOS-ONTO-060";

    /** 旧缺省哨兵：缺 callerId 时代码 fallback 到这里（这是 O-14① 的违例根因）。 */
    private static final String ANONYMOUS_SENTINEL = "anonymous";

    /**
     * 断言审计上下文可归因；否则抛 400 {@value #CODE_AUDIT_CONTEXT_MISSING}。
     *
     * @param callerId 调用方主体 id（operator）；null/空白/anonymous 均视为缺失
     * @throws ResponseStatusException 400（message 含 {@code ECOS-ONTO-060}）
     */
    public void require(String callerId) {
        String operatorMissing = isOperatorMissing(callerId);
        boolean traceIdMissing = TraceContext.current() == null || TraceContext.current().isBlank();
        if (operatorMissing == null && !traceIdMissing) {
            return;
        }
        StringBuilder why = new StringBuilder();
        if (operatorMissing != null) {
            why.append(operatorMissing);
        }
        if (traceIdMissing) {
            if (why.length() > 0) {
                why.append(", ");
            }
            why.append("traceId missing (MDC/TraceContext 未由 entry filter 写入)");
        }
        log.warn("function execute rejected: audit context missing -> {} code={}", why, CODE_AUDIT_CONTEXT_MISSING);
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                CODE_AUDIT_CONTEXT_MISSING + " 审计上下文缺失（" + why + "），拒绝执行");
    }

    /** @return 缺 operator 的原因说明；非缺失返回 null。 */
    private static String isOperatorMissing(String callerId) {
        if (callerId == null) {
            return "operator (callerId) is null";
        }
        String t = callerId.trim();
        if (t.isEmpty()) {
            return "operator (callerId) is blank";
        }
        if (ANONYMOUS_SENTINEL.equalsIgnoreCase(t)) {
            return "operator (callerId) is the 'anonymous' sentinel (PRD §4.1-2 视为无上下文)";
        }
        return null;
    }
}
