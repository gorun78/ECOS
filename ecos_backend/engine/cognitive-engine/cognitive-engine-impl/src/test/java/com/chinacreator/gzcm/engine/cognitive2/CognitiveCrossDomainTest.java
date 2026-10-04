package com.chinacreator.gzcm.engine.cognitive2;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 分册05 X-23 / 铁律 §2.4「跨引擎不操作对方表」(WR 业务跨域写白名单) 离线护栏。
 *
 * <p><b>审计事实源（2026-10-04 实查）</b>：认知 impl 主源码现存 9 处非白名单 DML 写——
 * <ul>
 *   <li>{@code sys_config} × 2（CognitiveConfigQueryService:56、:64）——主控制域跨写，R-14
 *       组切 sysman api 门面（B-5）前暂留；</li>
 *   <li>{@code kb_cognitive_pipeline} × 3（CognitivePipelineRepository:62、:68、:98）——ADR-8
 *       已明令 "kb_ 误名"，R-15-like 正名为 ecosystem cognitive 域权威名前的<b>过渡形态</b>；</li>
 *   <li>{@code ecos_decision} / {@code ecos_decision_causal_link} / {@code ecos_provenance_entry}
 *       / {@code ecos_scenario_run} × 4（DecisionServiceImpl:41、:62、:230 +
 *       CognitiveInvalidationConsumer:157）——无 schema 限定，实落 public（主控制）schema，
 *       目标态应位 ecos_cognitive（ST07），本批不越界代改。</li>
 * </ul>
 * 本护栏<b>只锁总量 ≤ 9，只减不增</b>：C.9 收口纪律同源——存量定性不擅迁，暴露面只减不增。
 * </p>
 */
final class CognitiveCrossDomainTest {

    /** 非白名单跨域/跨写 DML 目标（本表 <b>不包含</b> 属主认知域的 5 张白名单
     * {@code ecos_cognitive_{belief|hypothesis|evidence|model|run_invalidation}}）。
     */
    private static final Pattern OUT_OF_WHITELIST_WRITE = Pattern.compile(
        "\\b(INSERT\\s+INTO|UPDATE|DELETE\\s+FROM)\\s+"
            + "(?:\\w+\\.)?"
            + "(sys_config|kb_cognitive_pipeline|ecos_decision|ecos_decision_causal_link"
            + "|ecos_provenance_entry|ecos_scenario_run)\\b",
        Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("认知 impl 非白名单 DML 写只减不增（baseline 9：sys_config×2 + kb_cognitive_pipeline×3 "
        + "+ 无 schema 限定 4 张 = 9，2026-10-04 实测；新增 1 处即红）")
    void outOfWhitelistWritesRatchetFreeze() throws IOException {
        List<Path> files = CognitiveDocPaths.cognitiveMainJava();
        int hits = CognitiveDocPaths.countHits(files, OUT_OF_WHITELIST_WRITE);
        assertTrue(hits <= 9,
            "非白名单 cross-domain DML 写 %d > 9（超出 2026-10-04 基线；铁律 §2.4 认知不跨写 biz/主控）"
                .formatted(hits));
    }
}
