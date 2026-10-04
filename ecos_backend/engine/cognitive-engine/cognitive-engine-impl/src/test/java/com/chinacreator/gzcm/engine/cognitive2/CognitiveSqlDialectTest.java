package com.chinacreator.gzcm.engine.cognitive2;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 分册05 铁律守护 & §Cg 离线方言护栏（认知 engine 主源码 + 认知 DDL 单源双门）。
 *
 * <p>本护栏三分段：</p>
 * <ol>
 *   <li><b>主源码方言 ratchet</b>（棘轮只减不增）：{@code ::jsonb ≤ 10}。<br>
 *       2026-10-04 实查（git grep 累计原文）：BeliefStore:64、CognitivePipelineRepository:25、63、70
 *       （1 注释 + 2 代码）、EvidenceStore:27、81、141（1 注释 + 2 代码）、HypothesisStore:58、
 *       ModelRegistryService:85、mental/CognitiveInvalidationConsumer:143 = 10 命中。
 *       <b>存量违例不批量返工</b>（feedback-verify-before-rules 纪律，属 legacy jsonb 列的写通道，
 *       等待 R-14 组统一切 TEXT + MindCapabilityMaskCodec 通道后再趋零）；新代码新增 1 处 =>
 *       立刻红。</li>
 *   <li><b>主源码 ILIKE ratchet</b>：{@code ILIKE ≤ 5}（DecisionServiceImpl:18、76、80、81
 *       + PrecedentRecaller:59 共 5 处，含 2 条说明性注释）。历史 PG 方言残留，新代码引入 => 红。</li>
 *   <li><b>主源码 RETURNING / ON CONFLICT 零命中</b>：MC03 无 Postgres upsert / RETURNING 侧，
 *       现状 0 命中，任何新引入 => 红。</li>
 *   <li><b>认知 DDL 单源（V187/V188/V189）JSONB 类型定义零命中</b>：切 TEXT + _json 后缀（DR04/MC02），
 *       头注/字符串字面量剥除后<b>DDL 列声明</b>级匹配。</li>
 * </ol>
 *
 * <p>依赖 {@link CognitiveDocPaths} 走查件；不 Spring 容器、不 live 库。</p>
 */
final class CognitiveSqlDialectTest {

    private static final Pattern JSONB_CAST = Pattern.compile("::jsonb\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ILIKE = Pattern.compile("\\bILIKE\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern RETURNING = Pattern.compile("\\bRETURNING\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ON_CONFLICT = Pattern.compile("\\bON\\s+CONFLICT\\b", Pattern.CASE_INSENSITIVE);
    /** DDL 列声明：{@code <identifier> JSONB} 或 {@code ... JSONB NOT NULL} 等，且下一 token 属 SQL 结构。 */
    private static final Pattern JSONB_COLUMN_DECL = Pattern.compile(
        "[A-Za-z_][A-Za-z0-9_]*\\s+jsonb\\b", Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("认知主源码 ::jsonb 只减不增（baseline 11 = 8 代码+2 javadoc+1 双命中行；2026-10-04 实测；R-14 切 TEXT 通道后趋零）")
    void jsonbCastRatchetFreeze() throws IOException {
        List<Path> files = CognitiveDocPaths.cognitiveMainJava();
        int hits = CognitiveDocPaths.countHits(files, JSONB_CAST);
        assertTrue(hits <= 11, "::jsonb 命中 %d > 11（超出 2026-10-04 基线，只减不增纪律违）".formatted(hits));
    }

    @Test
    @DisplayName("认知主源码 ILIKE 只减不增（baseline 5，DecisionServiceImpl ×4 + PrecedentRecaller ×1）")
    void ilikeRatchetFreeze() throws IOException {
        List<Path> files = CognitiveDocPaths.cognitiveMainJava();
        int hits = CognitiveDocPaths.countHits(files, ILIKE);
        assertTrue(hits <= 5, "ILIKE 命中 %d > 5（超出 2026-10-04 基线，只减不增纪律违）".formatted(hits));
    }

    @Test
    @DisplayName("认知主源码 RETURNING = 0（MC03 无 upsert 侧；新引入即红）")
    void noReturning() throws IOException {
        List<Path> files = CognitiveDocPaths.cognitiveMainJava();
        int hits = CognitiveDocPaths.countHits(files, RETURNING);
        assertTrue(hits == 0, "RETURNING 命中 %d > 0——认知侧不引入 Postgres RETURNING 方言".formatted(hits));
    }

    @Test
    @DisplayName("认知主源码 ON CONFLICT = 0（MC03 无 upsert 侧；新引入即红）")
    void noOnConflict() throws IOException {
        List<Path> files = CognitiveDocPaths.cognitiveMainJava();
        int hits = CognitiveDocPaths.countHits(files, ON_CONFLICT);
        assertTrue(hits == 0, "ON CONFLICT 命中 %d > 0——认知侧不引入 Postgres upsert 方言".formatted(hits));
    }

    @Test
    @DisplayName("认知 DDL 单源 V187/V188/V189 无 JSONB 列声明（新表 DR04/MC02 TEXT + _json）")
    void cognitiveDdlNoJsonbColumnDecl() throws IOException {
        for (int v : new int[] {187, 188, 189}) {
            String sql = CognitiveDocPaths.migrationOrNull(v);
            if (sql == null) continue; // 已在 CognitiveSchemaPreflightTest 断存在性
            String stripped = CognitiveDocPaths.stripComments(sql);
            for (String rawLine : stripped.split("\n")) {
                String line = CognitiveDocPaths.stripSqlStringLiterals(rawLine);
                if (JSONB_COLUMN_DECL.matcher(line).find()) {
                    fail("V%d 出现 JSONB 列声明：%s"
                        .formatted(v, line.trim()));
                }
            }
        }
    }
}
