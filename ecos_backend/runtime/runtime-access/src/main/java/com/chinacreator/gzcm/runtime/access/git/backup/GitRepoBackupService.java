package com.chinacreator.gzcm.runtime.access.git.backup;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.runtime.access.git.GitRepositoryService;
import com.chinacreator.gzcm.runtime.access.git.entity.GitRepository;
import com.chinacreator.gzcm.runtime.access.storage.MinioStorageService;

/**
 * Git 仓库备份（§2.9.9 G7「无备份」收口；#147G7）。
 *
 * <p><b>做什么</b>：把注册表指向的本地仓库目录<b>整目录复制</b>到备份根
 * （{@code ecos.git.backup.root}，缺省 {@code ~/ecos-git-backups}），形成
 * {@code <root>/<repositoryId>/<yyyyMMddHHmmss>/}；随后若 MinIO 门面可用，
 * 把该目录打包为 {@code git-backups/<repositoryId>/<ts>.zip} 上传，返回对象名。</p>
 *
 * <p><b>不做什么（如实声明）</b>：这不是增量备份、不做保留策略/轮转、不校验远端可达性；
 * 备份产物是**复制语义**（含工作树与 .git），不是 git bundle/快照一致性视图——
 * 对"归档正确性"的强一致要求应在冻结期执行（配合 {@code PublishFreezeGuard}）。</p>
 *
 * @author ECOS Runtime #147G7
 */
@Service
public class GitRepoBackupService {

    private static final Logger log = LoggerFactory.getLogger(GitRepoBackupService.class);

    /** 备份根（System 属性 / 环境变量）。 */
    public static final String PROP_ROOT = "ecos.git.backup.root";
    public static final String ENV_ROOT = "ECOS_GIT_BACKUP_ROOT";
    /** MinIO 对象前缀。 */
    public static final String MINIO_PREFIX = "git-backups/";

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final GitRepositoryService repositoryService;
    private final ObjectProvider<MinioStorageService> minio;

    public GitRepoBackupService(GitRepositoryService repositoryService,
                                ObjectProvider<MinioStorageService> minio) {
        this.repositoryService = repositoryService;
        this.minio = minio;
    }

    /**
     * 备份单个仓库。
     *
     * @return 备份结果（本地目录、文件数、内容哈希、MinIO 对象名或 null）
     * @throws IOException 源仓库不存在/复制失败（调用方须显式报错，禁吞成"备份成功"）
     */
    public BackupResult backup(String repositoryId) throws IOException {
        GitRepository repo;
        try {
            repo = repositoryService.getRepositoryById(repositoryId);
        } catch (GitRepositoryService.GitRepositoryException e) {
            throw new IOException("仓库未注册或注册表不可用: " + repositoryId, e);
        }
        if (repo == null) {
            throw new IOException("仓库未注册: " + repositoryId);
        }
        String localPath = repo.getLocalPath();
        if (localPath == null || localPath.isBlank()) {
            throw new IOException("仓库本地路径缺失，无法备份: " + repositoryId);
        }
        Path source = Paths.get(localPath);
        if (!Files.isDirectory(source)) {
            throw new IOException("备份源不存在: " + source);
        }

        Path target = backupRoot().resolve(repositoryId).resolve(LocalDateTime.now().format(TS));
        Files.createDirectories(target);
        long files = copyTree(source, target);
        String digest = sha256OfTree(target);
        String objectName = uploadToMinio(repositoryId, target);
        log.info("Git 备份完成: repo={}, target={}, files={}, minio={}", repositoryId, target, files, objectName);
        return new BackupResult(repositoryId, target.toString(), files, digest, objectName);
    }

    private Path backupRoot() {
        String configured = System.getProperty(PROP_ROOT);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(ENV_ROOT);
        }
        if (configured == null || configured.isBlank()) {
            configured = Paths.get(System.getProperty("user.home"), "ecos-git-backups").toString();
        }
        return Paths.get(configured);
    }

    private static long copyTree(Path source, Path target) throws IOException {
        try (Stream<Path> s = Files.walk(source)) {
            long[] count = {0};
            for (Path p : s.toList()) {
                Path dest = target.resolve(source.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(dest);
                } else {
                    Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
                    count[0]++;
                }
            }
            return count[0];
        }
    }

    /** 树哈希：按相对路径排序后逐个更新 digest（确定性，便于比对两次备份）。 */
    static String sha256OfTree(Path root) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
        try (Stream<Path> s = Files.walk(root)) {
            for (Path p : s.filter(Files::isRegularFile).sorted().toList()) {
                md.update(root.relativize(p).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                md.update(Files.readAllBytes(p));
            }
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String uploadToMinio(String repositoryId, Path dir) {
        MinioStorageService storage = minio == null ? null : minio.getIfAvailable();
        if (storage == null) {
            return null; // 未装配：本地备份已成立，返回值显式 null（禁假装已上传）
        }
        try {
            String objectName = MINIO_PREFIX + repositoryId + "/" + dir.getFileName() + ".zip";
            byte[] zipped = ZipUtil.zipDirectory(dir);
            storage.putObject(objectName, zipped, "application/zip");
            return objectName;
        } catch (RuntimeException | IOException e) {
            log.warn("MinIO 备份上传失败（本地备份已成立，不回滚）: repo={}, err={}", repositoryId, e.getMessage());
            return null;
        }
    }

    /** 备份结果。 */
    public record BackupResult(String repositoryId, String localPath, long files, String treeSha256,
                               String minioObject) {
    }
}
