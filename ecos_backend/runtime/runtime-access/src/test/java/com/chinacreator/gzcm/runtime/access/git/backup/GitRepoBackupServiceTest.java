package com.chinacreator.gzcm.runtime.access.git.backup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

import com.chinacreator.gzcm.runtime.access.git.GitRepositoryService;
import com.chinacreator.gzcm.runtime.access.git.entity.GitRepository;
import com.chinacreator.gzcm.runtime.access.storage.MinioStorageService;

/**
 * Git 备份与发布冻结门（§2.9.9 G7；#147G7）。
 *
 * <p>覆盖：① 正常备份（目录复制 + 文件数 + 树哈希 64 位 + MinIO 未装配时显式为 null，
 * <b>不假装已上传</b>）；② 源不存在 ⇒ 抛错而非"备份成功"；③ 冻结期拒绝写、解冻后放行，
 * 且读面不受影响（门只拦写动作）。</p>
 *
 * @author ECOS Runtime #147G7
 */
class GitRepoBackupServiceTest {

    private static final class FakeRepoService implements GitRepositoryService {
        private final GitRepository repository;

        FakeRepoService(GitRepository repository) {
            this.repository = repository;
        }

        @Override
        public GitRepository getDefaultRepository() {
            return repository;
        }

        @Override
        public GitRepository getRepositoryById(String repositoryId) {
            return repository;
        }

        @Override
        public List<GitRepository> listRepositories() {
            return repository == null ? List.of() : List.of(repository);
        }

        @Override
        public void registerRepository(GitRepository repository) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public void unregisterRepository(String repositoryId) {
            throw new UnsupportedOperationException("not used");
        }
    }

    private static ObjectProvider<MinioStorageService> noMinio() {
        return new ObjectProvider<>() {
            @Override
            public MinioStorageService getObject(Object... args) {
                return null;
            }

            @Override
            public MinioStorageService getObject() {
                return null;
            }

            @Override
            public MinioStorageService getIfAvailable() {
                return null;
            }

            @Override
            public MinioStorageService getIfUnique() {
                return null;
            }
        };
    }

    private static GitRepository repoAt(String localPath) {
        GitRepository r = new GitRepository();
        r.setRepositoryId("ontology-model");
        r.setLocalPath(localPath);
        return r;
    }

    @AfterEach
    void clearProps() {
        System.clearProperty(GitRepoBackupService.PROP_ROOT);
        System.clearProperty(com.chinacreator.gzcm.runtime.access.git.publish.PublishFreezeGuard.PROP_FREEZE);
    }

    @Test
    @DisplayName("正常备份：目录复制到位、文件数正确、树哈希 64 位；MinIO 未装配返回 null（不假装已上传）")
    void backsUpDirectoryAndReportsHonestly(@TempDir Path tmp) throws Exception {
        Path source = Files.createDirectories(tmp.resolve("repo"));
        Files.writeString(source.resolve("a.json"), "{\"k\":1}");
        Files.createDirectories(source.resolve("sub"));
        Files.writeString(source.resolve("sub/b.md"), "# b");
        Path backupRoot = Files.createDirectories(tmp.resolve("backups"));
        System.setProperty(GitRepoBackupService.PROP_ROOT, backupRoot.toString());

        GitRepoBackupService service = new GitRepoBackupService(new FakeRepoService(repoAt(source.toString())), noMinio());
        GitRepoBackupService.BackupResult result = service.backup("ontology-model");

        assertEquals(2, result.files());
        assertTrue(Files.isRegularFile(Path.of(result.localPath()).resolve("sub/b.md")), "目录结构须原样复制");
        assertTrue(result.treeSha256().matches("[0-9a-f]{64}"));
        assertNull(result.minioObject(), "MinIO 未装配必须显式为 null，禁假装已上传");
    }

    @Test
    @DisplayName("源目录不存在：显式失败（禁把未备份报成备份成功）")
    void missingSourceFails(@TempDir Path tmp) {
        System.setProperty(GitRepoBackupService.PROP_ROOT, tmp.resolve("backups").toString());
        GitRepoBackupService service = new GitRepoBackupService(
                new FakeRepoService(repoAt(tmp.resolve("nope").toString())), noMinio());

        assertThrows(java.io.IOException.class, () -> service.backup("ontology-model"));
    }

    @Test
    @DisplayName("发布冻结：冻结期拒写且信息带机读码，解冻后放行；读面无闸")
    void freezeGuardBlocksWritesOnly() {
        System.setProperty(com.chinacreator.gzcm.runtime.access.git.publish.PublishFreezeGuard.PROP_FREEZE, "true");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> com.chinacreator.gzcm.runtime.access.git.publish.PublishFreezeGuard.assertNotFrozen("archive"));
        assertTrue(ex.getMessage().startsWith(
                com.chinacreator.gzcm.runtime.access.git.publish.PublishFreezeGuard.CODE_FROZEN));

        System.clearProperty(com.chinacreator.gzcm.runtime.access.git.publish.PublishFreezeGuard.PROP_FREEZE);
        com.chinacreator.gzcm.runtime.access.git.publish.PublishFreezeGuard.assertNotFrozen("archive");
    }
}
