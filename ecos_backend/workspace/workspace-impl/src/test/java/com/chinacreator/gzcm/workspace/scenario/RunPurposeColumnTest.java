package com.chinacreator.gzcm.workspace.scenario;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §8.3 接缝验收第 5 口 · F07-08/C154 命名列（DDL 只读形态断言）：
 * {@code RunPurposeColumnTest#runTypeEnumNotExtended}。
 *
 * <p>R-25 ① 定版（V209 头注逐字）：{@code FORMAL/SANDBOX} <b>不扩 run_type</b> 值域，
 * 新增<b>正交</b>列 {@code run_mode} 承载"是否隔离"。本条在 DDL 层断言同一决策已经在
 * V209 正确落地：
 * <ul>
 *   <li>正向 A：{@code run_mode} 列挂 {@code CHECK(IN ('FORMAL','SANDBOX'))} —— 演练隔离 DDL 面已落；</li>
 *   <li>正向 B：{@code idx_scen_exec_mode} 索引存在（E-4 五索引之一）；</li>
 *   <li>负向 A（主断言）：{@code run_type} <b>不</b>挂任何 {@code CHECK(run_type IN(…}} ——
 *       R-25 定版前权威值域归 PRD-08 §4.1，未来"顺手加 CHECK 锁词表"即本条红；</li>
 *   <li>负向 B：五值词表（DIAGNOSE/FORECAST/SIMULATE/STRATEGY/SAFEGUARD）不得以
 *       {@code IN(…'SIMULATE'…)} 形态出现在 V209 任何 CHECK 内。
 * </ul>
 *
 * <p>文本级 lint，注释剥离（V209 头注里含词表描述会假阳），不触 PG；
 * 列实跑属 §14.4 未授权项，本条不冒充 P-2 载体。</p>
 */
class RunPurposeColumnTest {

    private static final String V209_FILE = "V209__ecos_scenario_run_v2.sql";
    private static final Path MIGRATION_REL =
            Path.of("gateway/src/main/resources/db/migration");

    private static final Pattern COL_RUN_TYPE =
            Pattern.compile("\\brun_type\\s+VARCHAR\\(\\d+\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern COL_RUN_MODE =
            Pattern.compile("\\brun_mode\\s+VARCHAR\\(\\d+\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RUN_TYPE_CHECK_FORBIDDEN =
            Pattern.compile("\\bCHECK\\s*\\(\\s*run_type\\s+IN\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern RUN_MODE_CHECK_REQUIRED =
            Pattern.compile("\\bCHECK\\s*\\(\\s*run_mode\\s+IN\\s*\\(\\s*'FORMAL'\\s*,\\s*'SANDBOX'\\s*\\)",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern RUN_MODE_INDEX =
            Pattern.compile("\\bidx_scen_exec_mode\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FIVE_VALUE_IN_LIST =
            Pattern.compile("\\bIN\\s*\\([^)]*\\b(DIAGNOSE|FORECAST|SIMULATE|STRATEGY|SAFEGUARD)\\b[^)]*\\)",
                    Pattern.CASE_INSENSITIVE);

    @Test
    void runTypeEnumNotExtended() throws IOException {
        String stripped = stripSqlComments(
                Files.readString(locateV209(), StandardCharsets.UTF_8));

        // oracle：V209 不可为空白文件
        assertTrue(stripped.contains("CREATE TABLE"),
                "V209 应含 CREATE TABLE（oracle 正控：本门禁不基于空文件假绿）");
        assertTrue(COL_RUN_TYPE.matcher(stripped).find(),
                "V209 须含 run_type VARCHAR(n)（R-25 定版保留该列）");
        assertTrue(COL_RUN_MODE.matcher(stripped).find(),
                "V209 须含 run_mode VARCHAR(n)（R-25 ① 新加正交列）");

        // 主断言（负向 A）：run_type 不得挂 DDL 值域 CHECK
        assertFalse(RUN_TYPE_CHECK_FORBIDDEN.matcher(stripped).find(),
                "V209 禁止在 run_type 上挂 DDL 值域 CHECK——R-25 ① 定版前词表在 Java 侧单源，" +
                        "X-33/G2-5 三方值域并存，权威归属 PRD-08 §4.1 回写项，" +
                        "提前锁词表在 DDL 里违反定版节奏");

        // 负向 B：五值词表串不得以 IN(…) 形态出现
        assertFalse(FIVE_VALUE_IN_LIST.matcher(stripped).find(),
                "V209 的 IN(…) 出现 run_type 五值词表串——词表被 lock 进 DDL，" +
                        "与 R-25 ①『不扩 run_type 枚举』决策直接冲突");

        // 正向 A：run_mode 必须挂 CHECK('FORMAL','SANDBOX')
        assertTrue(RUN_MODE_CHECK_REQUIRED.matcher(stripped).find(),
                "V209 run_mode 必须挂 CHECK(run_mode IN ('FORMAL','SANDBOX'))——" +
                        "F07-08 演练隔离的 DDL 面，缺了仅靠 Java 侧护栏，非法值即可入库");

        // 正向 B：idx_scen_exec_mode 索引存在
        assertTrue(RUN_MODE_INDEX.matcher(stripped).find(),
                "V209 须含 idx_scen_exec_mode 索引（E-4 五索引之一）");
    }

    /**
     * 定位 V209：优先 CWD 上溯（maven surefire CWD = {@code workspace/workspace-impl}，
     * 向上两代 = {@code ecos_backend}，gateway 在其子目录），兜底从 class 文件位置反推。
     */
    private static Path locateV209() throws IOException {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path p = cwd; p != null; p = p.getParent()) {
            Path candidate = p.toAbsolutePath().normalize().resolve(MIGRATION_REL).resolve(V209_FILE);
            if (Files.isRegularFile(candidate)) return candidate;
        }
        try {
            Path cls = Path.of(RunPurposeColumnTest.class
                    .getResource("RunPurposeColumnTest.class").toURI());
            for (Path p = cls; p != null; p = p.getParent()) {
                Path candidate = p.toAbsolutePath().normalize().resolve(MIGRATION_REL).resolve(V209_FILE);
                if (Files.isRegularFile(candidate)) return candidate;
            }
        } catch (Exception ignore) { /* fall through */ }
        throw new IOException("cannot locate " + V209_FILE);
    }

    /**
     * 剥离 SQL 块注释与行注释（-- 起至行尾）。
     * V209 头注里含词表描述串，不剥离会造成假阳误报。
     */
    static String stripSqlComments(String raw) {
        String noBlock = raw.replaceAll("(?s)/\\*.*?\\*/", " ");
        StringBuilder out = new StringBuilder(noBlock.length());
        for (String ln : noBlock.split("\n", -1)) {
            int idx = ln.indexOf("--");
            if (idx >= 0) ln = ln.substring(0, idx);
            out.append(ln).append('\n');
        }
        return out.toString();
    }
}
