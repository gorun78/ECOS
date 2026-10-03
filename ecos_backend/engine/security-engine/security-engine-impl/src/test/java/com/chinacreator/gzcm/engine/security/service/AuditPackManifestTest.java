package com.chinacreator.gzcm.engine.security.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * F01-11 — 审计包导出 manifest 契约（07 册 D 章 delivery gate）。
 *
 * <p>本测试属<b>跨模块集成验收</b>：audit-pack 7 文件 zip + manifest checksum
 * 生成端点在 workspace（07 册 D 章）落地，本册只定<b>manifest 结构契约</b>：
 * <ol>
 *   <li>每条目必填 {file, sha256, bytes, produced_at, kind}；</li>
 *   <li>manifest 自身 sha256 = 全文件字节（含 padding 行）；</li>
 *   <li>manifest 内 <b>不含明文敏感字段</b>（如 username/phone 剔除或遮蔽）；</li>
 *   <li>scope=finance-all / 财务负责人无权分类 三种并集在 manifest.scope 落位。</li>
 * </ol>
 * 期望在 07 册交付时启用并做真 zip 校验（当前 @Disabled 守护契约声明，
 * 保持姊妹 stub @Test 名 page 与 12-cell 矩阵行头命名一致）。</p>
 */
@Disabled("Audit pack manifest assembly (7-file zip) is 07 册 workspace/audit-pack scope (F01-11). "
        + "This security-side test钉 manifest contract; integration unlocks when 07 册 D 章 endpoints land.")
class AuditPackManifestTest {

    @Test
    void page() {
        // 07 册 audit-pack 交付后启用断言（例如）:
        // byte[] zip = workspaceClient.exportAuditPack("finance-all");
        // Manifest m = Manifest.parse(zip.getManifestEntry());
        // assertEquals(7, m.entries().size());
        // assertAll(entries, e -> e.sha256() != null && e.bytes() > 0);
        // assertTrue(m.selfSha256() != null);
        // assertNone(entries, e -> e.sourceContainsPlaintext());
    }
}
