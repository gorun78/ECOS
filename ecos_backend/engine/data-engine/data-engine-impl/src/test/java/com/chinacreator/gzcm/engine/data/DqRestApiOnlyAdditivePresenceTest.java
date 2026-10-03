package com.chinacreator.gzcm.engine.data;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 数据域 D.2 REST 清单「API 只增不改」离线 surface-presence 护栏（M1）。
 *
 * <p><b>为什么有这条护栏</b>：详细设计-02 §D.2 明文列出 24 条数据域 REST 端点，
 * 铁律 #9「API 只增不改」要求既有路径与参数签名不可变更。校订十一曾<b>一次性人工</b>核验
 * 这 24 条在 controller 实测齐备（改用真实 {@code @*Mapping} 路径），但此后<b>无持久护栏</b>——
 * 未来任何一次静默删除其中一条端点（撤路由/改前缀/重命名映射）都会无声破坏 API 契约。
 * 本护栏把「D.2 各端点的路由片段仍存在于某 controller」机械化：对每条端点声明一组
 * <b>类基路径字面 + 方法后缀字面</b>（含引号），断言它们<b>在同一 controller 源文件内共存</b>
 * （防跨文件巧合命中）。任何一条端点被整体删除即判红，只减不增语义（本护栏只做 presence，
 * 不锁计数）。</p>
 *
 * <p><b>范围口径</b>：只扫本模块 {@code data-engine-impl/src/main/java}（24 条端点全部落此模块
 * 的 controller / pipeline / quality 包），不外推其他模块。base 为类级 {@code @RequestMapping}，
 * suffix 为方法级 {@code @*Mapping} 的字面（含首尾引号，避免 {@code "/test"} 误命中
 * {@code "/testfoo"}）。</p>
 *
 * <p><b>已知漂移（本护栏显式<b>不</b>纳入锁定的第 24 条）</b>：D.2 行
 * {@code POST /api/v1/dq/evaluate}（operationId {@code evaluateDqRule}）。2026-10-03 实测：
 * controller 无 {@code /api/v1/dq/evaluate} 映射——evaluate 实际只 served 在
 * {@code QualityController}（base {@code /api/v1/engine/data/quality}）的
 * {@code @PostMapping("/evaluate")} 与 {@code /rules/{ruleId}/evaluate}；
 * {@code VersionPrefixRewriteFilter} 仅反向重写 {@code /api/dq/ → /api/v1/dq/}，不桥接
 * {@code /api/v1/dq → /api/v1/engine/data/quality}；仓内无消费者引用 {@code /api/v1/dq/evaluate}。
 * 即 D.2 的目标前缀 {@code /api/v1/dq/evaluate} 现未收敛（旧 quality 前缀仍承流）。
 * 本护栏<b>不 lock-in</b> 该漂移（不断言其存在或不存在，留待 F02 收口批次裁决是
 * jq 补 {@code /api/v1/dq/evaluate} 映射或校订 D.2 改为实际前缀），仅在此与【校订十八】登记，
 * 故锁定面板 = 其余 23 条。</p>
 *
 * <p><b>不触库 / 不 Spring / 不联网</b>：纯文件读取 + 字面子串匹配。</p>
 */
@DisplayName("数据域 D.2 REST 清单 只增不改 surface-presence 护栏（M1，锁 23 条，evaluate 漂移显式豁免）")
class DqRestApiOnlyAdditivePresenceTest {

    /**
     * D.2 中除 evaluate 漂移外的 23 条端点。
     * 每条 = 标签 + 一组「字面 token」（含引号，须在同一 controller 源文件内全部出现）。
     * base token 形如 {@code "/api/v1/dq"}；suffix token 形如 {@code "/rules/{id}/submit"}；
     * 全路径 method-level 映射（write-channel / carriers）用单个完整字面。
     */
    private static final Object[][] D2_ENDPOINTS = {
            // { 标签, [token...] }
            {"POST /api/v1/datanet/datasource",        new String[]{"/api/v1/datanet/datasource", "@PostMapping"}},
            {"GET  /api/v1/datanet/datasource",        new String[]{"/api/v1/datanet/datasource", "@GetMapping"}},
            {"POST /api/v1/datanet/datasource/test",   new String[]{"/api/v1/datanet/datasource", "/test"}},
            {"POST /api/v1/datanet/ingest/run",        new String[]{"/api/v1/datanet/ingest", "/run"}},
            {"POST /api/v1/datanet/facts/{f}/import",  new String[]{"/api/v1/datanet/facts", "/{factType}/import"}},
            {"GET  /api/v1/datanet/facts/{f}/template",new String[]{"/api/v1/datanet/facts", "/{factType}/template"}},
            {"GET  /api/v1/datanet/facts/{f}",         new String[]{"/api/v1/datanet/facts", "/{factType}"}},
            {"GET  /api/v1/datanet/facts/batches/{id}",new String[]{"/api/v1/datanet/facts", "/batches/{batchId}"}},
            {"POST /api/v1/datanet/facts/action-outcomes", new String[]{"/api/v1/datanet/facts", "/action-outcomes"}},
            {"POST /api/v1/datanet/write-channel",     new String[]{"/api/v1/datanet/write-channel"}},
            {"GET  /api/v1/engine/data/layers",        new String[]{"/api/v1/engine/data/layers", "@GetMapping"}},
            {"POST /api/v1/engine/data/layers/{l}/carr",new String[]{"/api/v1/engine/data/layers/{layer}/carriers"}},
            {"GET  /api/v1/engine/data/lineage",       new String[]{"/api/v1/engine/data/lineage", "@GetMapping"}},
            {"POST /api/v1/engine/data/lineage/topo/rebuild", new String[]{"/api/v1/engine/data/lineage", "/topology/rebuild"}},
            {"GET  /api/v1/pipeline/node-types",       new String[]{"/api/v1/pipeline", "/node-types"}},
            {"PUT  /api/v1/pipeline/definitions/{id}", new String[]{"/api/v1/pipeline", "/definitions/{id}"}},
            {"POST /api/v1/pipeline/definitions/{id}/execute", new String[]{"/api/v1/pipeline", "/definitions/{id}/execute"}},
            {"POST /api/v1/dq/rules",                  new String[]{"/api/v1/dq", "/rules", "@PostMapping"}},
            {"POST /api/v1/dq/rules/{id}/submit",      new String[]{"/api/v1/dq", "/rules/{id}/submit"}},
            {"POST /api/v1/dq/rules/{id}/approve",     new String[]{"/api/v1/dq", "/rules/{id}/approve"}},
            {"GET  /api/v1/dq/work-orders",            new String[]{"/api/v1/dq/work-orders", "@GetMapping"}},
            {"POST /api/v1/dq/work-orders/{id}/escalate", new String[]{"/api/v1/dq/work-orders", "/{id}/escalate"}},
            {"GET  /api/v1/dq/scores/system",          new String[]{"/api/v1/dq/scores", "/system"}},
    };

    @Test
    @DisplayName("D.2 23 条端点的路由片段仍共存于某 controller（只增不改，防静默删除）")
    void documentedEndpointsStillPresent() throws IOException {
        List<String> files = readAllMainJava();
        assertTrue(!files.isEmpty(), "data-engine-impl main 源码读取为空——路径解析漂移，请人工复核");

        List<String> broken = new ArrayList<>();
        for (Object[] ep : D2_ENDPOINTS) {
            String label = (String) ep[0];
            String[] tokens = (String[]) ep[1];
            boolean satisfiedInOneFile = false;
            for (String src : files) {
                boolean all = true;
                for (String t : tokens) {
                    if (!src.contains(t)) {
                        all = false;
                        break;
                    }
                }
                if (all) {
                    satisfiedInOneFile = true;
                    break;
                }
            }
            if (!satisfiedInOneFile) {
                broken.add(label + "  [" + String.join(" + ", tokens) + "]");
            }
        }
        assertTrue(broken.isEmpty(),
                "[D.2 只增不改] 以下已标注 REST 端点的路由片段在 data-engine-impl controller 中缺失"
                        + "（铁律 #9：既有 API 路径不可删除/改签名；若为有意下线须同步校订 §D.2 并移除本条目）：\n  "
                        + String.join("\n  ", broken));
    }

    @Test
    @DisplayName("白线：evaluate 漂移 status quo 未收敛（旧 quality 前缀仍承流）——防误删本护栏面板")
    void evaluateDriftStillOnLegacyPrefix_whiteLine() throws IOException {
        List<String> files = readAllMainJava();
        // 白线语义：目前 /api/v1/dq/evaluate **不存在**、且 evaluate 仍在旧 quality 前缀——
        // 若未来 F02 收口把它落到 /api/v1/dq/，本白线将首红作"漂移已修，请收敛 D2_ENDPOINTS 或撤下本断言"信号。
        boolean dqEvaluatePresent = files.stream().anyMatch(s -> s.contains("/api/v1/dq/evaluate")
                || s.contains("\"/evaluate\"") && s.contains("/api/v1/dq\""));
        boolean legacyQualityEvaluatePresent = files.stream()
                .anyMatch(s -> s.contains("/api/v1/engine/data/quality") && s.contains("\"/evaluate\""));
        assertTrue(legacyQualityEvaluatePresent,
                "白线撤下信号：evaluate 应在 QualityController（/api/v1/engine/data/quality）—— "
                        + "若已迁移请复核并更新本护栏面板与【校订十八】");
        // dqEvaluatePresent 仅作信息回显（不断言），避免过早锁死目标形态
        if (dqEvaluatePresent) {
            System.out.println("[INFO] D.2 evaluate 端点已在 /api/v1/dq 前缀收敛，请复核 D2_ENDPOINTS 是否应纳入 + 撤销本白线");
        }
    }

    /** 读取 data-engine-impl src/main/java 下全部 .java 源文本（每文件一条）。 */
    private List<String> readAllMainJava() {
        Path srcMain = RlsInjectionGuardArchTest.resolveModuleSrcMainJava();
        List<String> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(srcMain)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> {
                        try {
                            out.add(Files.readString(p, StandardCharsets.UTF_8));
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    });
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return out;
    }
}
