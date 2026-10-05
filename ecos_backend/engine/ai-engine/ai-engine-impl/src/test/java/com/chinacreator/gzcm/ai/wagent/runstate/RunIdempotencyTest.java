package com.chinacreator.gzcm.ai.wagent.runstate;

import com.chinacreator.gzcm.ai.wagent.orchestrator.RunIdempotency;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分册10 F10-11 Run 幂等键离线单测：同 (tenantId, initiatedBy, idempotencyKey) 派生同 sha256 hex，
 * 异输入派生异哈希；isValidKey 仅认 64 位小写 hex。
 */
class RunIdempotencyTest {

    /** F10-11：幂等键确定性——同输入同 hash，异输入异 hash。 */
    @Test
    void sameKeyDerivesSameHash() {
        String h1 = RunIdempotency.deriveKey("tenant-1", "user-1", "idem-key-1");
        String h2 = RunIdempotency.deriveKey("tenant-1", "user-1", "idem-key-1");
        assertEquals(h1, h2, "同三元组必须派生同一 sha256");
        assertTrue(RunIdempotency.isValidKey(h1), "派生结果本身必须是合法 64 位 hex 键");

        assertNotEquals(h1, RunIdempotency.deriveKey("tenant-2", "user-1", "idem-key-1"),
                "tenantId 不同 ⇒ 键必不同");
        assertNotEquals(h1, RunIdempotency.deriveKey("tenant-1", "user-2", "idem-key-1"),
                "initiatedBy 不同 ⇒ 键必不同");
        assertNotEquals(h1, RunIdempotency.deriveKey("tenant-1", "user-1", "idem-key-2"),
                "idempotencyKey 不同 ⇒ 键必不同");

        // 空 idempotencyKey 禁派生（F10-11 强制要求）。
        assertThrows(IllegalArgumentException.class,
                () -> RunIdempotency.deriveKey("tenant-1", "user-1", ""));
    }

    /** F10-11：isValidKey 长度 ≠64 或含非 hex 字符 ⇒ false。 */
    @Test
    void invalidKeyRejected() {
        assertTrue(RunIdempotency.isValidKey("a".repeat(64)));
        assertTrue(RunIdempotency.isValidKey("0123456789abcdef".repeat(4)), "0-9 a-f 组合合法");

        assertFalse(RunIdempotency.isValidKey("a".repeat(63)), "长度 63 ≠ 64 ⇒ 非法");
        assertFalse(RunIdempotency.isValidKey("a".repeat(65)), "长度 65 ≠ 64 ⇒ 非法");
        assertFalse(RunIdempotency.isValidKey("g".repeat(64)), "非 hex 字符 ⇒ 非法");
        assertFalse(RunIdempotency.isValidKey("A".repeat(64)), "大写 hex 不在白名单（源实现仅认小写）⇒ 非法");
        assertFalse(RunIdempotency.isValidKey(null), "null ⇒ 非法");
        assertFalse(RunIdempotency.isValidKey(""), "空串 ⇒ 非法");
    }
}
