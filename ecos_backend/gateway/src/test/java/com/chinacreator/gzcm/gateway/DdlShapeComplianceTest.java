package com.chinacreator.gzcm.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分册09 §七 W218/W219/C200/C201 · 财务预测（fc）迁移脚本 DDL 合规形态走查（M0 红线，不实跑库）。
 *
 * <p>验收标识：</p>
 * <ul>
 *   <li>{@code mvn -Dtest=DdlShapeComplianceTest#rejectsJsonbInFcTables}（MC02：受控 JSON 只准 TEXT，禁 JSONB）</li>
 *   <li>{@code mvn -Dtest=DdlShapeComplianceTest#rejectsGenRandomUuidInFcTables}（MC01：主键 VARCHAR(36)
 *       应用侧 UUID，DDL 禁 {@code gen_random_uuid()}）</li>
 * </ul>
 *
 * <p>扫描范围：gateway 单源迁移目录下 5 个 fc 脚本（V214 视图 + V215~V218 四表）。
 * <b>文件必须存在</b>（R-64① 已批准落迁移脚本文件），且逐文件断言不含 JSONB / gen_random_uuid /
 * 任何 SERIAL 主键。本测试<b>不触库</b>（Flyway 禁用，脚本仅落文件）。</p>
 */
@DisplayName("C200/C201 fc 迁移脚本 DDL 合规形态（MC02 禁 JSONB · MC01 禁 gen_random_uuid/SERIAL）")
class DdlShapeComplianceTest {

    private static final String[] FC_SCRIPTS = {
            "V214__fc_caliber_reference_view.sql",
            "V215__fc_run.sql",
            "V216__fc_result_detail.sql",
            "V217__fc_backtest.sql",
            "V218__fc_action_ext.sql",
    };

    /** 受控 JSON 只准 TEXT：若出现 JSONB 列类型即红（MC02）。 */
    private static final Pattern JSONB =
            Pattern.compile("(?i)\\bJSONB\\b");
    /** MC01：主键应用侧 VARCHAR(36) UUID，DDL 禁 gen_random_uuid()。 */
    private static final Pattern GEN_UUID =
            Pattern.compile("(?i)gen_random_uuid\\s*\\(");
    /** MC01/MC02：主键禁 SERIAL/BIGSERIAL。 */
    private static final Pattern SERIAL =
            Pattern.compile("(?i)\\b(?:BIGSERIAL|SERIAL)\\b");

    @Test
    @DisplayName("C200/MC02 · fc 迁移脚本不含 JSONB（受控 JSON 只准 TEXT）")
    void rejectsJsonbInFcTables() {
        List<String> offenders = scan(JSONB);
        assertTrue(offenders.isEmpty(),
                "MC02 违规：fc 迁移脚本含 JSONB（应改 TEXT）。明细:\n  " + String.join("\n  ", offenders));
    }

    @Test
    @DisplayName("C201/MC01 · fc 迁移脚本不含 gen_random_uuid / SERIAL 主键")
    void rejectsGenRandomUuidInFcTables() {
        List<String> jsonBOrSerial = new ArrayList<>(scan(GEN_UUID));
        jsonBOrSerial.addAll(scan(SERIAL));
        assertTrue(jsonBOrSerial.isEmpty(),
                "MC01 违规：fc 迁移脚本含 gen_random_uuid()/SERIAL（主键应 VARCHAR(36) 应用侧 UUID）。明细:\n  "
                        + String.join("\n  ", jsonBOrSerial));
    }

    // ─────────────────────────── 公共辅助 ─────────────────────────────

    private List<String> scan(Pattern rule) {
        Path dir = migrationDir();
        List<String> offenders = new ArrayList<>();
        for (String script : FC_SCRIPTS) {
            Path p = dir.resolve(script);
            assertTrue(Files.isRegularFile(p), "fc 迁移脚本缺失（R-64① 应落文件）: " + p);
            try {
                String content = Files.readString(p, StandardCharsets.UTF_8);
                int lineNo = 0;
                for (String raw : content.split("\r\n|\n|\r", -1)) {
                    lineNo++;
                    String line = stripComment(raw);
                    if (rule.matcher(line).find()) {
                        offenders.add(script + ":" + lineNo + " " + raw.trim());
                    }
                }
            } catch (IOException e) {
                throw new java.io.UncheckedIOException("读 fc 脚本失败: " + p, e);
            }
        }
        return offenders;
    }

    /** 剔除行尾 -- 注释，避免注释里出现的 JSONB/gen_random_uuid 字样误命中。 */
    private static String stripComment(String line) {
        int idx = line.indexOf("--");
        return idx >= 0 ? line.substring(0, idx) : line;
    }

    private static Path migrationDir() {
        Path ecos = ecosBackendDir();
        Path dir = ecos.resolve("gateway/src/main/resources/db/migration");
        assertNotNull(dir, "迁移单源目录不存在: " + dir);
        assertTrue(Files.isDirectory(dir), "迁移单源目录不存在: " + dir);
        try (Stream<Path> walk = Files.list(dir)) {
            assertTrue(walk.anyMatch(x -> x.getFileName().toString().startsWith("V215__fc_run")),
                    "migration 目录未见 fc 脚本，路径可能错: " + dir);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return dir;
    }

    /** 上溯到 ecos_backend 目录本身（其下含 gateway）。 */
    private static Path ecosBackendDir() {
        Path cur = Path.of("").toAbsolutePath();
        while (cur != null && !Files.exists(cur.resolve("ecos_backend"))) {
            cur = cur.getParent();
        }
        Path ecos = cur.resolve("ecos_backend");
        assertTrue(Files.isDirectory(ecos), "找不到 ecos_backend 后端根: 起点 " + Path.of("").toAbsolutePath());
        return ecos;
    }
}
