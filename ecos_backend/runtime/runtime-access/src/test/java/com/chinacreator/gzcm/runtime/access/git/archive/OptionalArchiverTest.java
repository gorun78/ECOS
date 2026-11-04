package com.chinacreator.gzcm.runtime.access.git.archive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.Iterator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 可选归档器强类型门的门禁（§2.9.9 VG-5 收口；#147BL）。
 *
 * <p>钉三条：① 归档器缺席（provider 为 null / provider 返回 null / 直接传 null）一律
 * {@code not_available} + 机读原因码，<b>绝不伪成功</b>；② 在场时七参数逐字委托、结果原样回传；
 * ③ 失败状态不被门掩盖。</p>
 *
 * <p>不引 Mockito：runtime-access 的测试依赖只有 JUnit，替身用覆写 {@code archive} 的子类 +
 * 极简 {@code ObjectProvider} 实现（生产路径是 {@code of(provider)}，必须覆盖到）。</p>
 */
class OptionalArchiverTest {

    /** 记录调用的替身：覆写 archive，不触碰 git。 */
    static class RecordingArchiver extends DesignAssetArchiver {
        int calls;
        String repositoryId;
        String assetType;
        String assetId;
        String versionNo;
        String content;
        String commitMessage;
        String author;
        final ArchiveOutcome outcome;

        RecordingArchiver(ArchiveOutcome outcome) {
            super(null, null);
            this.outcome = outcome;
        }

        @Override
        public ArchiveOutcome archive(String repositoryId, String assetType, String assetId,
                                      String versionNo, String content, String commitMessage, String author) {
            this.calls++;
            this.repositoryId = repositoryId;
            this.assetType = assetType;
            this.assetId = assetId;
            this.versionNo = versionNo;
            this.content = content;
            this.commitMessage = commitMessage;
            this.author = author;
            return outcome;
        }
    }

    /** 极简 ObjectProvider（只用到 getIfAvailable）。 */
    static class FixedProvider implements ObjectProvider<DesignAssetArchiver> {
        private final DesignAssetArchiver value;

        FixedProvider(DesignAssetArchiver value) {
            this.value = value;
        }

        @Override
        public DesignAssetArchiver getObject(Object... args) {
            return value;
        }

        @Override
        public DesignAssetArchiver getIfAvailable() {
            return value;
        }

        @Override
        public DesignAssetArchiver getIfUnique() {
            return value;
        }

        @Override
        public DesignAssetArchiver getObject() {
            return value;
        }

        @Override
        public Iterator<DesignAssetArchiver> iterator() {
            return value == null ? Collections.emptyIterator() : Collections.singleton(value).iterator();
        }
    }

    private static void assertUnavailable(OptionalArchiver gate) {
        assertFalse(gate.available());
        ArchiveOutcome outcome = gate.archive("repo", "type", "id", "1", "{}", "msg", "author");
        assertEquals(ArchiveOutcome.Status.NOT_AVAILABLE, outcome.status());
        assertTrue(String.valueOf(outcome.reason()).startsWith(OptionalArchiver.CODE_NOT_ASSEMBLED),
                "原因须带机读前缀（调用方可据此分态）: " + outcome.reason());
    }

    @Test
    @DisplayName("provider 缺失 / provider 返回 null / 直接 null ⇒ not_available + 机读原因码（禁伪成功）")
    void absentReturnsNotAvailable() {
        assertUnavailable(OptionalArchiver.of(null));
        assertUnavailable(OptionalArchiver.ofNullable(null));
        assertUnavailable(OptionalArchiver.of(new FixedProvider(null)));
    }

    @Test
    @DisplayName("of(provider) 在场 ⇒ 七参数逐字委托，归档结果原样回传")
    void presentDelegatesThroughProvider() {
        RecordingArchiver archiver = new RecordingArchiver(ArchiveOutcome.archived("abc123", "rules/r1/2"));
        OptionalArchiver gate = OptionalArchiver.of(new FixedProvider(archiver));
        assertTrue(gate.available());

        ArchiveOutcome outcome = gate.archive("repo", "rules", "r1", "2", "{\"a\":1}", "msg", "author");

        assertEquals(ArchiveOutcome.Status.ARCHIVED, outcome.status());
        assertEquals("abc123", outcome.commitId(), "门不得改写结果（含 commitId）");
        assertEquals(1, archiver.calls);
        assertEquals("repo", archiver.repositoryId);
        assertEquals("rules", archiver.assetType);
        assertEquals("r1", archiver.assetId);
        assertEquals("2", archiver.versionNo);
        assertEquals("{\"a\":1}", archiver.content);
        assertEquals("msg", archiver.commitMessage);
        assertEquals("author", archiver.author);
    }

    @Test
    @DisplayName("在场但归档失败 ⇒ 失败状态与原因照常回传（门不掩盖失败）")
    void failureStatusIsPropagated() {
        RecordingArchiver archiver = new RecordingArchiver(ArchiveOutcome.failed("git down"));
        ArchiveOutcome outcome = OptionalArchiver.ofNullable(archiver)
                .archive("repo", "type", "id", "3", "{}", "m", "a");

        assertEquals(ArchiveOutcome.Status.FAILED, outcome.status());
        assertEquals("git down", outcome.reason());
        assertEquals(1, archiver.calls);
    }
}
