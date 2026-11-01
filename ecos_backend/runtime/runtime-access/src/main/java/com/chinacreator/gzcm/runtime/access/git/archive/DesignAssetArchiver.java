package com.chinacreator.gzcm.runtime.access.git.archive;

import com.chinacreator.gzcm.runtime.access.git.GitRepositoryService;
import com.chinacreator.gzcm.runtime.access.git.GitService;
import com.chinacreator.gzcm.runtime.access.git.entity.GitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * 设计资产统一归档器（#147E，§2.9.5②「四版本」与 §2.9.7 全域接入）。
 *
 * <p><b>它解决的差距</b>：V300 种下 6 枚 {@code repositoryId}，但只有 {@code dw-pipeline} 与
 * {@code ontology-model} 有真实归档通道；{@code kb-asset-tree}/{@code cognitive-model}/
 * {@code agent-collab}/{@code scenario-def} 四枚在各自域内<b>零引用</b>——「注册了」却不「被消费」。
 * 本类为这四域提供同一套归档语义（写工作树 → commit → tag → 真实 commitId），
 * 免去每域各写一套 JGit/路径拼接（DD-3 红线）。</p>
 *
 * <p><b>诚实三态（禁伪成功）</b>：</p>
 * <ul>
 *   <li>{@link ArchiveOutcome.Status#ARCHIVED} — 有真实 {@code commitId} 回执；</li>
 *   <li>{@link ArchiveOutcome.Status#NOT_AVAILABLE} — 注册表未注册/仓库禁用/工作树未解析/
 *       Git 通道未装配。这是<b>合法的现状态</b>（配置或环境未就绪），不是业务失败；</li>
 *   <li>{@link ArchiveOutcome.Status#FAILED} — 写盘/提交/打标签确证失败。</li>
 * </ul>
 *
 * <p><b>非事务性声明</b>：Git 提交与业务库写入属不同持久化系统，本类<b>绝不回滚</b>业务库，
 * 也不因归档失败让业务写入失败（可用性优先）；调用方须把结果记入三腿记录，
 * 由 {@code PublishStateMachine}/{@code PublishReconciler} 判 {@code PARTIAL_FAILURE}，
 * 禁止把「业务成功 + 归档失败」报成完全成功（§2.9.6①）。</p>
 *
 * <p><b>路径</b>：{@code <repo>/<assetType>/<assetId>/<versionNo|latest>.json}；
 * 每段都过 path-traversal 守卫。</p>
 *
 * @author ECOS Runtime底座 #147E
 */
@Service
public class DesignAssetArchiver {

    private static final Logger log = LoggerFactory.getLogger(DesignAssetArchiver.class);

    /** 缺省提交作者（与 ontology 归档口径一致）。 */
    private static final String DEFAULT_AUTHOR_NAME = "system";
    private static final String DEFAULT_AUTHOR_EMAIL = "system@ecos.local";

    private final GitRepositoryService repositoryService;
    private final GitService gitService;

    public DesignAssetArchiver(GitRepositoryService repositoryService, GitService gitService) {
        this.repositoryService = repositoryService;
        this.gitService = gitService;
    }

    /**
     * 归档一份设计资产。
     *
     * @param repositoryId 注册表 repositoryId（六枚之一；禁客户端任意值）
     * @param assetType    资产类型目录（如 {@code rules}/{@code models}/{@code agents}/{@code scenarios}）
     * @param assetId      资产标识
     * @param versionNo    版本号（可空；空时文件名取 {@code latest}，且不打 tag）
     * @param content      已序列化内容（JSON/YAML 文本；空值拒绝）
     * @param message      提交信息（可空）
     * @param author       作者（可空）
     */
    public ArchiveOutcome archive(String repositoryId, String assetType, String assetId,
                                  String versionNo, String content, String message, String author) {
        String repoId = trimToNull(repositoryId);
        String type = trimToNull(assetType);
        String id = trimToNull(assetId);
        String ver = trimToNull(versionNo);
        if (repoId == null || type == null || id == null) {
            return ArchiveOutcome.notAvailable("缺少 repositoryId/assetType/assetId，拒绝归档（禁伪成功）");
        }
        if (content == null || content.isBlank()) {
            return ArchiveOutcome.notAvailable("缺少已序列化内容，拒绝归档（禁伪成功）");
        }
        try {
            requireSafeSegment(type);
            requireSafeSegment(id);
            if (ver != null) {
                requireSafeSegment(ver);
            }
        } catch (IllegalArgumentException e) {
            return ArchiveOutcome.failed("非法资产路径段（path-traversal 守卫拒绝）: " + e.getMessage());
        }

        GitRepository repository;
        try {
            repository = repositoryService.getRepositoryById(repoId);
        } catch (GitRepositoryService.GitRepositoryException e) {
            return ArchiveOutcome.notAvailable("仓库未注册/不可用: repositoryId=" + repoId);
        }
        if (repository == null || !repository.isEnabled()) {
            return ArchiveOutcome.notAvailable("仓库不存在或已禁用: repositoryId=" + repoId);
        }
        if (repository.getLocalPath() == null || repository.getLocalPath().isBlank()) {
            return ArchiveOutcome.notAvailable("仓库工作树未解析: repositoryId=" + repoId
                    + "（检查 sys_config:ecos_git_repo_root 与 GitRepoPathResolver 装配）");
        }

        String relPath = relativePath(type, id, ver != null ? ver : "latest");
        if (!repository.allows(relPath)) {
            return ArchiveOutcome.failed("路径被注册表纳入/排除清单拒绝（§2.9.5③）：" + relPath);
        }

        String msg = trimToNull(message) == null
                ? "Archive " + type + " " + id + (ver != null ? " " + ver : "")
                : message;
        String authorName = trimToNull(author) == null ? DEFAULT_AUTHOR_NAME : author;

        try {
            writeAsset(repository.getLocalPath(), type, id, ver, content);
        } catch (IOException e) {
            log.error("归档写入失败 repositoryId={} type={} id={} ver={}", repoId, type, id, ver, e);
            return ArchiveOutcome.failed("写入工作树失败: " + e.getMessage());
        }

        String commitId;
        try {
            commitId = gitService.commitAllChanges(repository, msg, authorName, DEFAULT_AUTHOR_EMAIL);
        } catch (GitService.GitException e) {
            log.error("归档提交失败 repositoryId={} type={} id={}", repoId, type, id, e);
            return ArchiveOutcome.failed("Git 提交失败: " + e.getMessage());
        }

        String tag = type + "/" + id + "/" + (ver != null ? ver : "latest");
        if (ver != null) {
            try {
                gitService.createTag(repository, tag, msg);
            } catch (GitService.GitException e) {
                log.error("归档打标签失败 repositoryId={} tag={}（commit 已完成）", repoId, tag, e);
                return ArchiveOutcome.failed("Git 打标签失败: " + e.getMessage()
                        + "（提交号 " + commitId + " 已产生）");
            }
        }
        log.info("设计资产归档成功 repositoryId={} type={} id={} ver={} commitId={} tag={}",
                repoId, type, id, ver, commitId, tag);
        return ArchiveOutcome.archived(commitId, tag);
    }

    /** 按 ref 读回资产内容（对账/回滚取证；路径不存在返回空）。 */
    public Optional<String> readAtRef(String repositoryId, String assetType, String assetId,
                                      String versionNo, String ref) {
        String repoId = trimToNull(repositoryId);
        String type = trimToNull(assetType);
        String id = trimToNull(assetId);
        String ver = trimToNull(versionNo) == null ? "latest" : trimToNull(versionNo);
        if (repoId == null || type == null || id == null) {
            return Optional.empty();
        }
        try {
            GitRepository repository = repositoryService.getRepositoryById(repoId);
            if (repository == null || repository.getLocalPath() == null) {
                return Optional.empty();
            }
            return gitService.readFileAtRef(repository, ref, relativePath(type, id, ver));
        } catch (GitRepositoryService.GitRepositoryException | GitService.GitException e) {
            log.warn("按 ref 读回失败 repositoryId={} type={} id={} ver={} ref={}: {}",
                    repoId, type, id, ver, ref, e.getMessage());
            return Optional.empty();
        }
    }

    private void writeAsset(String localPath, String type, String id, String ver, String content)
            throws IOException {
        Path dir = Paths.get(localPath, type, id).normalize();
        Path root = Paths.get(localPath).normalize().toAbsolutePath();
        if (!dir.toAbsolutePath().startsWith(root)) {
            throw new IOException("归档路径越出仓库根，已拒绝");
        }
        Files.createDirectories(dir);
        Path file = dir.resolve((ver != null ? ver : "latest") + ".json").normalize();
        if (!file.toAbsolutePath().startsWith(root)) {
            throw new IOException("归档文件越出仓库根，已拒绝");
        }
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static String relativePath(String type, String id, String ver) {
        return type + "/" + id + "/" + ver + ".json";
    }

    /** path-traversal 守卫（与 ontology 归档同口径）。 */
    static void requireSafeSegment(String segment) {
        if (segment == null || segment.trim().isEmpty()
                || segment.contains("..") || segment.contains("/") || segment.contains("\\")
                || segment.indexOf(':') >= 0) {
            throw new IllegalArgumentException(segment);
        }
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
