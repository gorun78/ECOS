package com.chinacreator.gzcm.engine.security;

import com.chinacreator.gzcm.engine.security.rolechannel.V246SeedFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-B4-19（#142/B4-3e-90，2026-10-27 用户裁决「修种子+诚实边界改判」）：
 * V246 三类 INSERT 目标 schema 的字面形态门禁。
 *
 * <p><b>缺陷本体（差距文档 D-B4-19，B4-3b v1.0.15 起在册）</b>：
 * V246 的 INSERT 字面写向 {@code public.ecos_rls_policy / ecos_cls_policy /
 * ecos_security_asset}，而 dev 实测（2026-10-27，容器 ecos-postgres 库 sys_man）
 * 三张策略表<b>只</b>存在于 {@code ecos_control}（V164 建表单源在 V200 治理后落
 * ecos_control；{@code to_regclass('public.ecos_rls_policy')} 返回 NULL）。
 * 照旧字面实跑必逐条 {@code relation "public.ecos_rls_policy" does not exist}，
 * 这正是 §4.7.2 矩阵 live 侧「种子从未落库」的根因半。</p>
 *
 * <p><b>裁决与修法</b>：种子 schema 分叉收口 = 订正 seed 源文件字面为
 * {@code ecos_control.*}（只改 INSERT 目标schema段，值面零变更；dev 现态 22 行
 * legacy_text（seed-entmgmt-v2）在权威 schema 内，与本种子 id 无交集 ⇒ 落库无冲突
 * 面）。生产 DAO 不带 schema 限定名、按 {@code currentSchema} 解析（gateway/
 * workspace-service application.yml = ecos_control,…,public），本订正使
 * 「种子目标」与「生产读面」在同一 schema 闭合。</p>
 *
 * <p><b>四把反假绿锁</b>：</p>
 * <ol>
 *   <li>{@link #publicTargetsAreZeroAndSchemaTargetsExactlyThreeWhitelistedTables}
 *       —— {@code public.*} 目标计数=0 且 {@code <schema>.<table>} 目标恰为白名单
 *       三表；扫描根解析不到文件即失败（防空转）。</li>
 *   <li>{@link #insertCountsAre9Rls2Cls1Asset} —— 12 类 INSERT 计数 9/2/1
 *       钉死（与 V246 内容和各消费方解析下限口径一致）。</li>
 *   <li>{@link #fixtureAndConsistencyScanLiteralsMatchTheSeed} ——
 *       {@code V246SeedFixtures} 与 {@code SecuritySeedConsistencyTest} 的消费扫描串
 *       必须逐条出现在种子字面中（前缀字面与种子分叉时解析静默归空 = 本门禁的
 *       直接靶面，探针 P1 变红实证）。</li>
 *   <li>同批真实变异探针 P1：把 V246 一处目标改回 {@code public.ecos_rls_policy}
 *       ⇒ 本类 2 例红 + {@code SecuritySeedConsistencyTest#rlsSeedTemplatesPassWhitelist}
 *       红 + {@code V246SeedFixtures} 解析不足异常（还原后逐字复原，凭证见
 *       差距文档 v1.0.172 行）。</li>
 * </ol>
 *
 * <p><b>本门禁不主张</b>：种子已实跑（live 落库仍需三重前置授权门 ——
 * fc_* 事实表 / 演示主体与角色 seed / 迁移实跑授权，取证见差距文档 §4.7.2 行
 * 订正注 ⑪）；本类只钉「目标的 schema 字面形态」这一可静态判定面。</p>
 */
class V246SeedSchemaGateTest {

    /** 策略三表白名单：V164 单源在 V200 治理后的实际落位 schema。 */
    private static final Set<String> WHITELIST = Set.of(
            "ecos_control.ecos_rls_policy",
            "ecos_control.ecos_cls_policy",
            "ecos_control.ecos_security_asset");

    /** 消费方扫描前缀（与 V246SeedFixtures#RLS_INSERT_PREFIX / #CLS_INSERT_PREFIX 字面同源常量对照）。 */
    private static final List<String> CONSUMER_PREFIXES = List.of(
            "INSERT INTO ecos_control.ecos_rls_policy",
            "INSERT INTO ecos_control.ecos_cls_policy");

    @Test
    @DisplayName("public.* 目标零残留；schema 限定目标恰为白名单三表（扫描根缺失即失败）")
    void publicTargetsAreZeroAndSchemaTargetsExactlyThreeWhitelistedTables() throws IOException {
        String sql = readSeed();
        assertTrue(sql.contains("ecos_rls_policy")
                        && sql.contains("ecos_cls_policy")
                        && sql.contains("ecos_security_asset"),
                "V246 三类种子缺失——扫描根异常，禁把解析不到当成无违例");

        int publicTargets = countOccurrences(sql, "INSERT INTO public.ecos_security_asset")
                + countOccurrences(sql, "INSERT INTO public.ecos_rls_policy")
                + countOccurrences(sql, "INSERT INTO public.ecos_cls_policy");
        assertEquals(0, publicTargets,
                "V246 仍存在 public.* 目标（D-B4-19 分叉原形；dev 实测三表只在 ecos_control）");

        Set<String> scoped = new LinkedHashSet<>();
        Matcher m = Pattern.compile("INSERT INTO (\\w+)\\.(\\w+)").matcher(sql);
        while (m.find()) {
            scoped.add(m.group(1) + "." + m.group(2));
        }
        assertFalse(scoped.isEmpty(), "V246 未找到任何 schema 限定 INSERT 目标（扫描器失效）");
        for (String t : scoped) {
            assertTrue(WHITELIST.contains(t),
                    "V246 出现白名单外的 schema 限定 INSERT 目标: " + t);
        }
        assertEquals(new LinkedHashSet<>(WHITELIST), scoped,
                "V246 schema 限定目标集必须恰为策略三表（多一个=新拆分，少一个=种子丢失）");
    }

    @Test
    @DisplayName("INSERT 计数 9 rls / 2 cls / 1 asset 钉死")
    void insertCountsAre9Rls2Cls1Asset() throws IOException {
        String sql = readSeed();
        int rls = countOccurrences(sql, "INSERT INTO ecos_control.ecos_rls_policy");
        int cls = countOccurrences(sql, "INSERT INTO ecos_control.ecos_cls_policy");
        int asset = countOccurrences(sql, "INSERT INTO ecos_control.ecos_security_asset");
        assertEquals(9, rls, "V246 RLS 种子条数漂移（矩阵 4 行输入面收窄/扩面）: 实得 " + rls);
        assertEquals(2, cls, "V246 CLS 种子条数漂移: 实得 " + cls);
        assertEquals(1, asset, "V246 敏感目录 seed 组数漂移: 实得 " + asset);
    }

    @Test
    @DisplayName("消费方扫描前缀逐条命中种子字面（禁前缀分叉致解析静默归空）")
    void fixtureAndConsistencyScanLiteralsMatchTheSeed() throws IOException {
        String sql = readSeed();
        for (String prefix : CONSUMER_PREFIXES) {
            assertTrue(sql.contains(prefix),
                    "消费方扫描前缀未出现于 V246 字面（解析将静默归空=假绿）: " + prefix);
        }
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int s = haystack.indexOf(needle); s >= 0; s = haystack.indexOf(needle, s + 1)) {
            count++;
        }
        return count;
    }

    private static String readSeed() throws IOException {
        Path cur = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8 && cur != null; i++) {
            Path canonical = cur.resolve(
                    "docs/50-db_script/02-flyway-migration/gateway/V246__security_policy_seed.sql");
            if (Files.isRegularFile(canonical)) {
                return Files.readString(canonical);
            }
            cur = cur.getParent();
        }
        throw new IOException("未找到 V246__security_policy_seed.sql（门禁空转形态=假绿，显式失败）");
    }
}
