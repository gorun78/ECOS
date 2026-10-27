package com.chinacreator.gzcm.engine.kb.arch;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #146h D-7 收口棘轮门（2026-10-27）：kb 抽取族 + kg 同步日志族禁止再读/写裸名表。
 *
 * <p><b>断点事实（dev PG 实查取证，详见映射台账 §D-7 v1.0.8）</b>：</p>
 * <ul>
 *   <li>裸表 {@code kg_sync_log}（V115）／{@code kb_extract_watermark}（V136）／
 *       {@code kb_extract_audit}（V142）／{@code kb_extract_candidate}（V140）
 *       dev 库面<b>均不存在</b>（to_regclass 全 NULL，四枚 V 脚本从未实跑）；</li>
 *   <li>V179 并建的合规新表 {@code ecos_kg_sync_log}／{@code ecos_kb_extract_watermark}／
 *       {@code ecos_kb_extract_audit}（{@code VARCHAR(36)} 应用侧 UUID PK，ST）在位；</li>
 *   <li>#146h 前全部生产 DAO 仍写/读裸名 ⇒ live 运行时必然「表不存在」报错（双腿断点）。</li>
 * </ul>
 *
 * <p><b>裁决（#146h 落地）</b>：读端/写端统一切 {@code ecos_} 前缀合规范表（V179 承载口径），
 * 裸名 V115/V136/V140/V142 判 knownLegacy <b>停再跑</b>（不 DROP、不迁移数据——四表从来 0 行）。
 * 本门禁钉的是「生产 SQL 字符串回潮裸名」：对 kb-engine-impl 与 workspace-impl 的
 * {@code src/main} 逐行扫非注释行，命中裸名即红。
 * Javadoc/注释里以 {@code kg_sync_log} 等叙述历史载体不构成违例（扫描跳过注释行）。</p>
 *
 * <p><b>扫描口径刻意收窄</b>：{@code graph_node/graph_edge/kb_ontology_snapshot}
 * 虽也是裸名但 dev 库面在位承接 live 数据（不在 D-7 断点面），不入本门词表，
 * 防止口径扩张误杀在位腿。</p>
 *
 * <p><b>变异取证</b>（#146h 批）：把 {@code KgSyncLogDao.insertLog()} 写腿临时回退裸名
 * {@code kg_sync_log} → 本门红（命中 1 行）→ 逐字节复原（git diff 净空）→ 复跑绿
 * ⇒ 判据可变红、非恒绿。</p>
 */
@DisplayName("D-7：kb 抽取族 / kg 同步日志族禁再回潮裸名表（棘轮门）")
class KbExtractBareTableGateTest {

    /**
     * 裸名四表（已判 knownLegacy 停再跑）。用负向先行断言排除合规前缀：
     * {@code (?<!ecos_)kg_sync_log} 命中裸 {@code kg_sync_log} 但不命中
     * {@code ecos_kg_sync_log}；同理 {@code (?<!ecos_)kb_extract_(audit|watermark|candidate)}
     * 不命中 {@code ecos_kb_extract_*}。
     */
    private static final Pattern BARE_TABLE = Pattern.compile(
            "(?<!ecos_)[\"\\.\\s]kg_sync_log(?![A-Za-z_])"
            + "|(?<!ecos_)[\"\\.\\s]kb_extract_(audit|watermark|candidate)(?![A-Za-z_])");

    private static final List<String> SCAN_ROOTS = List.of(
            "ecos_backend/engine/kb-engine/kb-engine-impl/src/main",
            "ecos_backend/workspace/workspace-impl/src/main");

    @Test
    void no_bare_name_sql_in_production_sources() throws IOException {
        Path root = repoRoot();
        List<String> hits = new ArrayList<>();
        AtomicInteger filesScanned = new AtomicInteger();
        for (String rel : SCAN_ROOTS) {
            Path dir = root.resolve(rel);
            if (!Files.isDirectory(dir)) {
                fail("扫描根缺失: " + dir);
            }
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains("target"))
                    .forEach(p -> {
                        try {
                            filesScanned.incrementAndGet();
                            int lineNo = 0;
                            for (String line : Files.readAllLines(p)) {
                                lineNo++;
                                String trimmed = line.trim();
                                // 跳过纯注释行（// 或 * 或 /*）；行尾注释不在本腿口径内
                                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                                    continue;
                                }
                                if (BARE_TABLE.matcher(line).find()) {
                                    hits.add(root.relativize(p).toString().replace('\\', '/') + "#" + lineNo);
                                }
                            }
                        } catch (IOException e) {
                            fail("读文件失败: " + p + " (" + e.getMessage() + ")");
                        }
                    });
            } catch (IOException e) {
                fail("扫描失败: " + dir + " (" + e.getMessage() + ")");
            }
        }
        // 可发现性自检：根在位且扫到了文件（防 surefire cwd 漂移导致静默恒绿）
        assertTrue0(filesScanned.get() > 50, "扫描文件数过少（=" + filesScanned.get() + "），cwd 可能漂移");
        assertTrue0(hits.isEmpty(), "裸名表回潮（D-7 knownLegacy 四表禁再跑）: " + hits);
    }

    private static void assertTrue0(boolean cond, String msg) {
        if (!cond) {
            fail(msg);
        }
    }

    /** 从 user.dir 上溯 .git 定位仓库根。 */
    private static Path repoRoot() {
        Path cur = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (cur != null) {
            if (Files.isRegularFile(cur.resolve(".git")) || Files.isDirectory(cur.resolve(".git"))) {
                return cur;
            }
            cur = cur.getParent();
        }
        throw new IllegalStateException("未能定位仓库根（缺 .git）");
    }
}
