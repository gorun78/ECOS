package com.chinacreator.gzcm.common.security.filter;

import com.chinacreator.gzcm.common.security.abac.AbacEvaluateResponseContract;
import com.chinacreator.gzcm.common.security.abac.AbacEvaluateResponseContract.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RowFilterVerdictContract} 的<b>读数语义</b>单测（#142/B4-3e-7，收口差距 <b>D-B4-42</b>）。
 *
 * <p>与静态门禁 {@code RowFilterVerdictSingleSourceTest} 两支缺一不可：只有静态门禁时，
 * 键面单源但把"引擎没答"翻成 DENY 照样全绿（那正是 §4.7.2 禁止的折叠）；只有本类时，
 * 下一版把消费腿改回内联 {@code get("allowed")} 也照样全绿（那正是 D-B4-42 的发生形态）。</p>
 *
 * <p><b>核心不变式（解除条件要求"写进测试而非注释"）</b>：同一条
 * {@code allowed=false} 的应答，{@code success=true} 与 {@code success=false} 两态
 * 必须读出<b>不同</b>裁决、给出<b>不同</b>诊断串。收口前两支消费腿都只看 {@code allowed}，
 * 于是"引擎抛异常"与"策略判了这一行拒绝"在行过滤面同值 ——
 * 前者被静默丢行后照样返回 200，后者被反射腿折成 403 {@code sandbox_block}。</p>
 */
class RowFilterVerdictContractTest {

    private static Map<String, Object> data(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    // ── 一、写面工厂 ⇄ 读面读数闭合 ────────────────────────────────────────

    @Test
    @DisplayName("工厂形态自检：evaluated(true/false) 分别读回 ALLOW/DENY，外形仍带 allowed")
    void factoryRoundTripIsSelfConsistent() {
        Map<String, Object> allow = RowFilterVerdictContract.evaluated(true, "org == dept_a");
        assertEquals(Verdict.ALLOW, RowFilterVerdictContract.verdictOf(allow));
        // 写面外形不变（API 只增不改）：消费方升级前读 allowed 也不会拿到 null
        assertEquals(Boolean.TRUE, allow.get(RowFilterVerdictContract.KEY_ALLOWED));
        assertEquals(Boolean.TRUE, allow.get(RowFilterVerdictContract.KEY_SUCCESS));
        assertEquals(RowFilterVerdictContract.VALUE_ENGINE_RHAI,
                allow.get(RowFilterVerdictContract.KEY_ENGINE));
        assertEquals("org == dept_a", allow.get(RowFilterVerdictContract.KEY_EXPRESSION));
        assertFalse(allow.containsKey(RowFilterVerdictContract.KEY_ERROR),
                "正常判定路径不得带 error 键 ⇒ 会被读成 UNEVALUATED");

        Map<String, Object> deny = RowFilterVerdictContract.evaluated(false, "org == blocked_dept");
        assertEquals(Verdict.DENY, RowFilterVerdictContract.verdictOf(deny));
        assertEquals(Boolean.FALSE, deny.get(RowFilterVerdictContract.KEY_ALLOWED));
        assertEquals(Boolean.TRUE, deny.get(RowFilterVerdictContract.KEY_SUCCESS));
    }

    @Test
    @DisplayName("工厂形态自检：evaluationFailed 仍写 allowed=false，但 success=false＋error")
    void failureShapeKeepsWireCompatibility() {
        Map<String, Object> failed = RowFilterVerdictContract.evaluationFailed("Evaluation failed");
        assertEquals(Boolean.FALSE, failed.get(RowFilterVerdictContract.KEY_ALLOWED),
                "外形不变：旧消费方读 allowed 仍得到布尔而非 null（API 只增不改）");
        assertEquals(Boolean.FALSE, failed.get(RowFilterVerdictContract.KEY_SUCCESS));
        assertEquals("Evaluation failed", failed.get(RowFilterVerdictContract.KEY_ERROR));
        assertEquals(RowFilterVerdictContract.VALUE_ENGINE_FALLBACK_FAILED,
                failed.get(RowFilterVerdictContract.KEY_ENGINE));
    }

    // ── 二、D-B4-42 的核心不变式：同 allowed=false，两态必异 ────────────────

    @Test
    @DisplayName("不变式：同一条 allowed=false，真拒绝与引擎没答必须读出不同裁决、不同诊断")
    void denyAndUnevaluatedAreNeverCollapseable() {
        Map<String, Object> policyDeny = RowFilterVerdictContract.evaluated(false, "role == admin");
        Map<String, Object> engineFailed = RowFilterVerdictContract.evaluationFailed("Evaluation failed");
        // 折叠的表象：两条应答的 allowed 完全同值
        assertEquals(policyDeny.get(RowFilterVerdictContract.KEY_ALLOWED),
                engineFailed.get(RowFilterVerdictContract.KEY_ALLOWED),
                "本断言锁住\"只看 allowed 必然折叠\"的前提，前提变了要同步重设判据");

        assertNotEquals(RowFilterVerdictContract.verdictOf(policyDeny),
                RowFilterVerdictContract.verdictOf(engineFailed),
                "D-B4-42 复发：引擎故障又被读成策略拒绝 ⇒ 丢行照样报 200");
        assertEquals(Verdict.DENY, RowFilterVerdictContract.verdictOf(policyDeny));
        assertEquals(Verdict.UNEVALUATED, RowFilterVerdictContract.verdictOf(engineFailed));

        String denyDiag = RowFilterVerdictContract.diagnose(policyDeny);
        String unevalDiag = RowFilterVerdictContract.diagnose(engineFailed);
        assertNotEquals(denyDiag, unevalDiag, "诊断串必须可分态：否则日志侧又把它们写成同一件事");
        assertTrue(unevalDiag.contains("Evaluation failed"),
                "不可用诊断必须带上引擎给出的故障说明: " + unevalDiag);
    }

    @Test
    @DisplayName("success=false 时即便谓词放行也算不可用（禁\"故障但看着像通过\"就放行）")
    void unevaluatedNeverLeaksAsAllow() {
        assertEquals(Verdict.UNEVALUATED, RowFilterVerdictContract.verdictOf(data(
                RowFilterVerdictContract.KEY_SUCCESS, false,
                RowFilterVerdictContract.KEY_ALLOWED, true)));
        assertEquals(Verdict.UNEVALUATED, RowFilterVerdictContract.verdictOf(data(
                RowFilterVerdictContract.KEY_SUCCESS, false)));
    }

    @Test
    @DisplayName("error 键优先：success=true＋error 仍属引擎自报故障")
    void errorKeyWinsOverSuccessFlag() {
        assertEquals(Verdict.UNEVALUATED, RowFilterVerdictContract.verdictOf(data(
                RowFilterVerdictContract.KEY_SUCCESS, true,
                RowFilterVerdictContract.KEY_ALLOWED, true,
                RowFilterVerdictContract.KEY_ERROR, "timeout")));
    }

    @Test
    @DisplayName("success 键缺失⇒不可用：应答形态不属于本契约，禁猜成放行或拒绝")
    void missingSuccessFlagIsUnevaluated() {
        assertEquals(Verdict.UNEVALUATED, RowFilterVerdictContract.verdictOf(
                data(RowFilterVerdictContract.KEY_ALLOWED, true)));
        assertEquals(Verdict.UNEVALUATED, RowFilterVerdictContract.verdictOf(
                data(RowFilterVerdictContract.KEY_ALLOWED, false)));
        // 收口前的老生产侧正是"只有 allowed 没有 success"这一代 ⇒ 必须落不可用而非放行
        assertEquals(Verdict.UNEVALUATED, RowFilterVerdictContract.verdictOf(new LinkedHashMap<>()));
    }

    // ── 三、别名键面：D-B4-42 自造的 pass／result 一律不认 ──────────────────

    @Test
    @DisplayName("不设别名键：pass／result 两个历史自造别名读出来都是 UNEVALUATED")
    void inventedAliasKeysAreNotHonored() {
        for (String alias : new String[] {"pass", "result"}) {
            assertEquals(Verdict.UNEVALUATED,
                    RowFilterVerdictContract.verdictOf(data(
                            RowFilterVerdictContract.KEY_SUCCESS, true, alias, true)),
                    "别名 " + alias + " 不得被认成放行（全仓 main 面该 envelope 零写点）");
        }
    }

    // ── 四、字符串布尔形态：词表与 ABAC 共用，未命中不兜底 ──────────────────

    @Test
    @DisplayName("字符串形态：trim＋大小写归一命中 ABAC 词表，未命中一律 UNEVALUATED")
    void stringBooleanSharesAbacVocabulary() {
        for (String allow : new String[] {"true", "TRUE", " allow ", "Permit"}) {
            assertEquals(Verdict.ALLOW, RowFilterVerdictContract.verdictOf(data(
                    RowFilterVerdictContract.KEY_SUCCESS, true,
                    RowFilterVerdictContract.KEY_ALLOWED, allow)), "放行词: " + allow);
        }
        for (String deny : new String[] {"false", " deny", "DENIED"}) {
            assertEquals(Verdict.DENY, RowFilterVerdictContract.verdictOf(data(
                    RowFilterVerdictContract.KEY_SUCCESS, true,
                    RowFilterVerdictContract.KEY_ALLOWED, deny)), "拒绝词: " + deny);
        }
        assertEquals(Verdict.UNEVALUATED, RowFilterVerdictContract.verdictOf(data(
                RowFilterVerdictContract.KEY_SUCCESS, true,
                RowFilterVerdictContract.KEY_ALLOWED, "maybe")));
        assertEquals(Verdict.UNEVALUATED, RowFilterVerdictContract.verdictOf(data(
                RowFilterVerdictContract.KEY_SUCCESS, true,
                RowFilterVerdictContract.KEY_ALLOWED, 1)), "数值 1 不得当成放行");
        // success 的字符串形态同样共用 ABAC 词表
        assertEquals(Verdict.ALLOW, RowFilterVerdictContract.verdictOf(data(
                RowFilterVerdictContract.KEY_SUCCESS, "true",
                RowFilterVerdictContract.KEY_ALLOWED, true)));
        assertEquals(Verdict.UNEVALUATED, RowFilterVerdictContract.verdictOf(data(
                RowFilterVerdictContract.KEY_SUCCESS, "yes",
                RowFilterVerdictContract.KEY_ALLOWED, true)));
    }

    @Test
    @DisplayName("词汇单源：行过滤复用 ABAC 的 Verdict 与词表，禁另立第二套")
    void vocabularyIsSharedNotRedefined() {
        // 属主必须是 ABAC 契约的内嵌枚举 —— 同名但另立一份定义即词表分裂
        assertEquals(AbacEvaluateResponseContract.class.getName() + "$Verdict",
                Verdict.class.getName());
        assertEquals(3, Verdict.values().length, "三态词汇被增删 ⇒ 消费腿的分支完备性失效");
        assertTrue(RowFilterVerdictContract.diagnose(null) != null);
    }

    // ── 五、非 Map 形态：缺 data 段不得抛异常也不得兜底成任一态 ──────────────

    @Test
    @DisplayName("null／非对象／段取错：一律 UNEVALUATED，诊断串指明类型")
    void nonMapShapesAreUnevaluated() {
        assertEquals(Verdict.UNEVALUATED, RowFilterVerdictContract.verdictOf(null));
        assertEquals(Verdict.UNEVALUATED, RowFilterVerdictContract.verdictOf("allowed=true"));
        // 消费方把整个 envelope（外层带 data）而不是 data 段交进来 ⇒ 不是"没放行"，是"取错了段"
        assertEquals(Verdict.UNEVALUATED, RowFilterVerdictContract.verdictOf(data(
                "data", RowFilterVerdictContract.evaluated(true, "x"))));
        assertTrue(RowFilterVerdictContract.diagnose(null).contains("null"));
        assertTrue(RowFilterVerdictContract.diagnose("allowed=true").contains("String"),
                "诊断必须能区分\"没答\"与\"答错形态\"");
    }
}
