package com.chinacreator.gzcm.engine.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F02-01 / §7.1 W52 / C41（P0 安全：数据源凭证明文入库，控制域不可豁免）验收名之一
 * {@code DatasourceLegacyPasswordMigrationTest} 的<b>离线 DML 契约护栏</b>（M1）。
 *
 * <p>doc 行 97 验收名 {@code DatasourceLegacyPasswordMigrationTest：以 2 行明文样例跑迁移，
 * 断言幂等 + 断言 connection_config LIKE '%password%' = 0}——其中<b>以样例行实跑 psql 且断言
 * 库内 {@code LIKE = 0}</b>属<b>实跑库授权闸</b>（IR02 手动 psql，见 §14.4 未授权项）。
 * 但其<b>前置 DML 契约</b>离线可验：单源 {@code V164.1__datasource_credential_split.sql}
 * 的迁移语义（脚本头注自证）不对 {@code connection_config} 内明文做重加密/洗数据
 * （IR03 只占位不删改），只做 <b>列就位 + 幂等哨兵标记</b>。本护栏把该 DML 契约固化：</p>
 *
 * <ol>
 *   <li><b>双镜像列就位</b>（ST07 schema 归属 + E.1 R-1 a）：
 *       {@code ALTER TABLE ecos_data.td_datasource ADD COLUMN IF NOT EXISTS credential_encrypted}
 *       与 {@code public.td_datasource} 各一，共 2 处，且表标识符均带 schema 限定（无裸表名）；</li>
 *   <li><b>明文检测判据</b>：两个 UPDATE 的 WHERE 均含
 *       {@code connection_config LIKE '%"password"%'}（2 处）——迁移只命中 config 内确含 password 键的行；</li>
 *   <li><b>幂等 guard（只减不增重跑前提）</b>：每个 UPDATE 的 WHERE 均含
 *       {@code (credential_encrypted IS NULL OR credential_encrypted = '')}（2 处）
 *       ——已置位行重跑 0 行受影响（doc"断言幂等"的库内可判定前提）；</li>
 *   <li><b>IR03 只占位不洗明文（白线，防退化假绿）</b>：{@code SET credential_encrypted = '__PENDING_REENCRYPT__'}
 *       精确命中 2 处，即两处 UPDATE 都写<b>固定哨兵字面</b>而非任何源自 {@code connection_config}
 *       的表达式；一旦未来改成从 config 拷贝明文进新列，本护栏即红。</li>
 * </ol>
 *
 * <p><b>与 {@code DatasourceCredentialCipherTest} 正交</b>：该护栏锁<b>业务代码面</b>的
 * AES-256-GCM 加解密往返 + 无 egress 拒明文 + 读路径永不回显（C41 编码侧）；本护栏锁
 * <b>迁移脚本面</b>的列就位 / 幂等 / 哨兵 DML 契约（C41 迁移侧）。二者合起来才覆盖 W52 的两条
 * 验收名，本护栏补上此前对账（校订二十三）仅映射到编码侧的遗漏。<br>
 * <b>不触库 / 不 Spring / 不联网</b>：纯文件读取，行首 {@code --} 注释先行剔除后再计数（顶注/纪律
 * 说明命中不计）。落 {@code data-engine-impl} 单测 M1；走本模块既有
 * {@link RlsInjectionGuardArchTest#resolveModuleSrcMainJava()} 上溯仓根，与
 * {@link BareTableNameRatchetArchTest} / {@link DdlComplianceLintTest} 同一片单源目录 + 同口语径。</p>
 */
@DisplayName("F02-01 W52/C41 存量明文凭证迁移 DML 契约护栏（M1 / 离线 / V164.1）")
class DatasourceLegacyPasswordMigrationTest {

    private static final String SCRIPT = "V164.1__datasource_credential_split.sql";
    /** 哨兵字面（V164.1 头注 + 两 UPDATE 精确字面，勿含尾随空格）。 */
    private static final String SENTINEL = "'__PENDING_REENCRYPT__'";
    /** 幂等 guard 精确字面（重跑 0 行受影响的前提）。 */
    private static final String IDEMPOTENT_GUARD = "(credential_encrypted IS NULL OR credential_encrypted = '')";
    /** 明文检测判据精确字面。 */
    private static final String PLAINTEXT_PREDICATE = "connection_config LIKE '%\"password\"%'";
    /** 设置哨兵的精确子句（IR03：只写固定字面，不拷 config 明文）。 */
    private static final String SET_SENTINEL = "SET credential_encrypted = " + SENTINEL;
    /** 新增密文列精确子句。 */
    private static final String ADD_COL = "ADD COLUMN IF NOT EXISTS credential_encrypted";

    @Test
    @DisplayName("V164.1 迁移 DML 契约：双镜像列就位 + 幂等 guard + 明文判据 + 哨兵不洗明文")
    void v164_1_legacyCredentialMigrationDmlContract() throws IOException {
        Path script = resolveMigrationDir().resolve(SCRIPT);
        assertTrue(Files.isRegularFile(script),
                "W52/C41 迁移单源脚本缺失: " + script + "（F02-01 凭证密文化列就位应实存）");

        // 行首 -- 注释先行剔除（顶注/回滚说明/纪律自审命中不计），只数可执行 SQL。
        String sql = stripLineComments(Files.readString(script));

        // ① 双镜像列就位：ecos_data + public 各一，共 2 处新增密文列（均 schema 限定）。
        int addCol = count(sql, ADD_COL);
        assertEquals(2, addCol,
                "V164.1 应在 ecos_data.td_datasource 与 public.td_datasource 各加 1 列 credential_encrypted（共 "
                        + 2 + " 处 ADD COLUMN IF NOT EXISTS），实测 " + addCol);
        assertTrue(sql.contains("ALTER TABLE ecos_data.td_datasource " + ADD_COL),
                "缺 ecos_data.td_datasource " + ADD_COL + "（ST07 引擎控制域权威归属侧）");
        assertTrue(sql.contains("ALTER TABLE public.td_datasource    " + ADD_COL),
                "缺 public.td_datasource " + ADD_COL + "（D-7 双镜像承数侧）；白线撤下信号");

        // ② 明文检测判据：两个 UPDATE 均命中 config 内确含 password 键的行（共 2 处）。
        int plain = count(sql, PLAINTEXT_PREDICATE);
        assertEquals(2, plain,
                "两条 UPDATE（ecos_data + public）的 WHERE 应各含一次 "
                        + PLAINTEXT_PREDICATE + "（共 2 处），实测 " + plain);

        // ③ 幂等 guard：两条 UPDATE 均限定"仅未置位行"（共 2 处）——由此重跑 0 行受影响。
        int idem = count(sql, IDEMPOTENT_GUARD);
        assertEquals(2, idem,
                "两条 UPDATE 的 WHERE 应各含一次幂等 guard " + IDEMPOTENT_GUARD + "（共 2 处，重跑 0 行前提），实测 " + idem);

        // ④ IR03 只占位不洗明文（白线）：两处 UPDATE 都写固定哨兵字面，而非拷贝 config 明文。
        int setData = count(sql, SET_SENTINEL);
        assertEquals(2, setData,
                "两条 UPDATE 应各 `SET credential_encrypted = " + SENTINEL + "`（共 2 处，占位哨兵），实测 " + setData
                        + "；若改成从 connection_config 派生表达式写入新列（洗明文）即破坏 IR03 只占位口径 → 本护栏判红");

        // 双镜像 UPDATE 落点白线：确认两侧承数表都打标（ecos_data 权威 + public 承数）。
        assertTrue(sql.contains("UPDATE ecos_data.td_datasource"), "缺 UPDATE ecos_data.td_datasource 哨兵标记");
        assertTrue(sql.contains("UPDATE public.td_datasource"), "缺 UPDATE public.td_datasource 哨兵标记");
    }

    /** 精确子串计数（case-sensitive；契约字面固定，防引号/结构改动后静默失配）。 */
    private static int count(String hay, String needle) {
        int c = 0;
        int i = 0;
        while ((i = hay.indexOf(needle, i)) != -1) { c++; i += needle.length(); }
        return c;
    }

    /** 逐行剔除行首 {@code --} 之后的注释段（保留可执行 SQL），返回拼接文本。 */
    private static String stripLineComments(String src) {
        StringBuilder sb = new StringBuilder(src.length());
        for (String line : src.split("\\R", -1)) {
            int idx = line.indexOf("--");
            sb.append(idx >= 0 ? line.substring(0, idx) : line).append('\n');
        }
        return sb.toString();
    }

    private static Path resolveMigrationDir() {
        Path srcMain = RlsInjectionGuardArchTest.resolveModuleSrcMainJava();
        Path cur = srcMain;
        for (int i = 0; i < 12 && cur != null; i++) {
            if (Files.isDirectory(cur.resolve("docs")) && Files.isDirectory(cur.resolve("ecos_backend"))) {
                return cur.resolve("ecos_backend/gateway/src/main/resources/db/migration");
            }
            cur = cur.getParent();
        }
        fail("无法从 " + srcMain + " 上溯到 ECOS 仓根，以定位 gateway/.../db/migration/");
        return null; // unreachable
    }
}
