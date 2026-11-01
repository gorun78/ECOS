package com.chinacreator.gzcm.runtime.access.git.publish;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * 对账判定表——§2.9.6③「对账 8 类判定」的<b>唯一判据面</b>（#147D）。
 *
 * <p>纯函数、无副作用、不取数：输入 {@link ReconciliationInput}，输出
 * {@link ReconciliationVerdict} + 人读理由。取数（读权威库对象集、按 git ref 读回快照）
 * 属各引擎消费侧的职责，本类只保证「给到什么证据就判什么，缺证据绝不放行到
 * {@link ReconciliationVerdict#CONSISTENT}」。</p>
 *
 * <p><b>判定序（短路优先，序本身即口径，改动须同批改单测）</b>：</p>
 * <ol>
 *   <li>无三腿记录 → {@link ReconciliationVerdict#UNVERIFIABLE}（无从判定）；</li>
 *   <li>三腿不成套（任一腿 {@code FAILED}/{@code UNKNOWN}，或成功腿缺正向证据指针）
 *       → {@link ReconciliationVerdict#PARTIAL_PUBLISH}。此项<b>排在取数缺失之前</b>：
 *       腿是否成套是发布记录自身的结论，不需要外部证据；反过来若先判「读不到」，
 *       就会把「Git 成、DB 败」这种必须暴露的部分发布，降级成一句"没法验证"。</li>
 *   <li>版本引用已知不对齐 → {@link ReconciliationVerdict#VERSION_REF_MISMATCH}；</li>
 *   <li>仓库侧少了对象 → {@link ReconciliationVerdict#MISSING_OBJECT}；
 *       仓库侧多了对象 → {@link ReconciliationVerdict#EXTRA_OBJECT}；</li>
 *   <li>内容摘要不等 → {@link ReconciliationVerdict#CONTENT_DRIFT}；</li>
 *   <li>依赖已知不对齐 → {@link ReconciliationVerdict#DEPENDENCY_MISMATCH}；</li>
 *   <li>六项证据齐备且全部对齐 → {@link ReconciliationVerdict#CONSISTENT}；</li>
 *   <li>以上都不成立（证据不全但无确证差异）→ {@link ReconciliationVerdict#UNVERIFIABLE}。</li>
 * </ol>
 *
 * <p><b>「一致」是正面结论，不是缺省值</b>：第 7 步要求 {@link ReconciliationInput#hasFullEvidence()}
 * 为真，第 8 步才收不到证据的情形兜底成 {@code UNVERIFIABLE}——这正是本批要消灭的旧形态
 * （查不到即视为无差异）。</p>
 *
 * <p><b>本批的取证边界</b>：仓库侧内容按 ref 读回的能力在
 * {@code OntologyGitService}/{@code GitService} 面上尚不存在（详设只要求"历史版本走 Git"，
 * 未提供 read-at-ref），故第 4~6 步的真实消费侧目前只能拿到 {@code UNVERIFIABLE}。
 * 该缺口随 #147E 的「读写腿切 Git 归档单通道」补齐，台账如实登记，不在本批伪造成已对账。</p>
 *
 * @author ECOS Runtime底座 #147D
 */
public final class PublishReconciler {

    private PublishReconciler() {
    }

    /**
     * 判定结果：8 类判定 + 单源理由串。
     *
     * <p>{@code evidenceGaps} 只列「未取到的证据项」，与「确证的差异」分开——
     * 前者要补取数，后者要进恢复轨（且依 §2.9.6③ 须回到审批链，禁自动修复）。</p>
     */
    public static final class Result {

        private final ReconciliationVerdict verdict;
        private final String reason;
        private final Set<String> evidenceGaps;

        Result(ReconciliationVerdict verdict, String reason, Set<String> evidenceGaps) {
            this.verdict = verdict;
            this.reason = reason;
            this.evidenceGaps = evidenceGaps == null
                    ? Collections.emptySet()
                    : Collections.unmodifiableSet(new TreeSet<>(evidenceGaps));
        }

        public ReconciliationVerdict verdict() {
            return verdict;
        }

        public String reason() {
            return reason;
        }

        public Set<String> evidenceGaps() {
            return evidenceGaps;
        }

        @Override
        public String toString() {
            return verdict + "(" + reason + ")"
                    + (evidenceGaps.isEmpty() ? "" : " gaps=" + evidenceGaps);
        }
    }

    /** 按 §2.9.6③ 判定序对账。 */
    public static Result reconcile(ReconciliationInput input) {
        if (input == null) {
            return new Result(ReconciliationVerdict.UNVERIFIABLE,
                    "无对账输入（既不能判一致，也不能判差异）", Set.of("input"));
        }

        DualWriteOutcome outcome = input.outcome();
        if (outcome == null) {
            return new Result(ReconciliationVerdict.UNVERIFIABLE,
                    "无三腿发布记录，对账无从起手", Set.of("outcome"));
        }

        // 1) 三腿不成套 → 部分发布（先于取数缺失，见类注释判定序第 2 条）
        if (!isLegSetComplete(outcome)) {
            return new Result(ReconciliationVerdict.PARTIAL_PUBLISH,
                    "三腿不成套：" + outcome.legSummary(), Set.of());
        }

        // 2) 版本引用不对齐（已确证）
        if (Boolean.FALSE.equals(input.versionRefMatched())) {
            return new Result(ReconciliationVerdict.VERSION_REF_MISMATCH,
                    "权威库与仓库记录的不是同一版本引用：" + outcome.legSummary(), Set.of());
        }

        // 3) 对象集合差异（已确证）
        if (input.hasObjectKeyEvidence()) {
            Set<String> missing = difference(input.authoritativeObjectKeys(), input.repositoryObjectKeys());
            Set<String> extra = difference(input.repositoryObjectKeys(), input.authoritativeObjectKeys());
            if (!missing.isEmpty()) {
                return new Result(ReconciliationVerdict.MISSING_OBJECT,
                        "仓库快照缺少权威库对象 " + missing.size() + " 项", Set.of());
            }
            if (!extra.isEmpty()) {
                return new Result(ReconciliationVerdict.EXTRA_OBJECT,
                        "仓库快照多出权威库对象 " + extra.size() + " 项", Set.of());
            }
        }

        // 4) 内容漂移（已确证）
        if (input.hasContentEvidence()
                && !input.authoritativeContentDigest().equals(input.repositoryContentDigest())) {
            return new Result(ReconciliationVerdict.CONTENT_DRIFT,
                    "对象集合相同但内容摘要不等：权威库=" + input.authoritativeContentDigest()
                            + " 仓库=" + input.repositoryContentDigest(), Set.of());
        }

        // 5) 依赖不一致（已确证）
        if (Boolean.FALSE.equals(input.dependencyMatched())) {
            return new Result(ReconciliationVerdict.DEPENDENCY_MISMATCH,
                    "跨对象依赖在权威库与仓库侧指向不同目标", Set.of());
        }

        // 6) 一致：必须六项证据齐备，缺一项都不许报一致
        if (input.hasFullEvidence()) {
            return new Result(ReconciliationVerdict.CONSISTENT,
                    "对象集、内容摘要、版本引用、依赖四项均对齐：" + outcome.legSummary(), Set.of());
        }

        return new Result(ReconciliationVerdict.UNVERIFIABLE,
                "证据不全，禁按「无差异」处理", gapsOf(input));
    }

    /**
     * 三腿是否成套：三腿都「无否定证据且无未知」，且凡 {@code SUCCEEDED} 的腿都有正向证据指针。
     *
     * <p>后半句是「禁伪成功」的可执行形式：{@code gitLeg=SUCCEEDED} 而 {@code gitRef} 空、
     * {@code dbLeg=SUCCEEDED} 而 {@code versionRef} 空，都算不成套——回执必须可指向实物。</p>
     */
    private static boolean isLegSetComplete(DualWriteOutcome outcome) {
        if (!outcome.allLegsCleared()) {
            return false;
        }
        if (outcome.gitLeg().isSuccess() && !outcome.gitEvidencePresent()) {
            return false;
        }
        if (outcome.dbLeg().isSuccess() && !outcome.versionEvidencePresent()) {
            return false;
        }
        // 关键安全变更：审计腿「不适用」不放行（§2.9.6② fail-closed）
        return !outcome.securityCritical() || outcome.auditLeg().isSuccess();
    }

    /** {@code a - b}（保持 {@link LinkedHashSet} 的入参顺序，供命中数与人读串稳定）。 */
    private static Set<String> difference(Set<String> a, Set<String> b) {
        Set<String> out = new LinkedHashSet<>(a);
        out.removeAll(b);
        return out;
    }

    /** 列出未取到的证据项名（供恢复侧知道「该补哪一次取数」）。 */
    private static Set<String> gapsOf(ReconciliationInput input) {
        Set<String> gaps = new LinkedHashSet<>();
        if (input.authoritativeObjectKeys() == null) {
            gaps.add("authoritativeObjectKeys");
        }
        if (input.repositoryObjectKeys() == null) {
            gaps.add("repositoryObjectKeys");
        }
        if (input.authoritativeContentDigest() == null) {
            gaps.add("authoritativeContentDigest");
        }
        if (input.repositoryContentDigest() == null) {
            gaps.add("repositoryContentDigest");
        }
        if (input.versionRefMatched() == null) {
            gaps.add("versionRefMatched");
        }
        if (input.dependencyMatched() == null) {
            gaps.add("dependencyMatched");
        }
        return gaps;
    }
}
