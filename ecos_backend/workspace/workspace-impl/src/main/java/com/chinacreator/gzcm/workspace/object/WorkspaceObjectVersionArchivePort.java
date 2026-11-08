package com.chinacreator.gzcm.workspace.object;

import com.chinacreator.gzcm.common.service.ObjectVersionArchivePort;
import com.chinacreator.gzcm.runtime.access.git.GitRepositoryService;
import com.chinacreator.gzcm.runtime.access.git.GitService;
import com.chinacreator.gzcm.runtime.access.git.archive.DesignAssetArchiver;
import com.chinacreator.gzcm.runtime.access.git.entity.GitCommitView;
import com.chinacreator.gzcm.runtime.access.git.entity.GitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 对象版本 Git 端口实现（DS-4 对象运行时域；2026-11-08）。
 *
 * <p>对象是本体实例，归档进 {@code ontology-model} 仓的 {@code objects/<objectId>/<ver>.json}；
 * 提交信息 {@code Object snapshot <objectId> v<ver> entity=<entityCode>} 是版本事件。
 * 仓库未注册/读失败显式异常，禁把「没归档」读成「没有版本」。</p>
 */
@Component
public class WorkspaceObjectVersionArchivePort implements ObjectVersionArchivePort {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceObjectVersionArchivePort.class);
    static final String REPO = "ontology-model";
    static final String ASSET_TYPE = "objects";
    static final String PREFIX = "Object snapshot ";
    static final String ID_PREFIX = "git-";

    private final DesignAssetArchiver archiver;
    private final GitService gitService;
    private final GitRepositoryService repositoryService;

    public WorkspaceObjectVersionArchivePort(DesignAssetArchiver archiver, GitService gitService,
                                             GitRepositoryService repositoryService) {
        this.archiver = archiver;
        this.gitService = gitService;
        this.repositoryService = repositoryService;
    }

    @Override
    public Optional<String> archive(String objectId, String entityCode, int versionNo,
                                    String snapshotJson, String changeSummary, String actor) {
        String msg = PREFIX + objectId + " v" + versionNo
                + (entityCode == null || entityCode.isBlank() ? "" : " entity=" + entityCode);
        var outcome = archiver.archive(REPO, ASSET_TYPE, objectId, String.valueOf(versionNo),
                snapshotJson, msg, actor);
        if (outcome != null && outcome.isArchived() && outcome.commitId() != null) {
            return Optional.of(outcome.commitId());
        }
        log.warn("对象版本归档失败 objectId={} v{} status={} reason={}", objectId, versionNo,
                outcome == null ? "null" : outcome.status(),
                outcome == null ? "null" : outcome.reason());
        return Optional.empty();
    }

    @Override
    public List<ObjectVersionRecord> list(String objectId) {
        Map<Integer, ObjectVersionRecord> byVersion = new LinkedHashMap<>();
        for (GitCommitView c : commits()) {
            Parsed p = parse(c, objectId);
            if (p == null || byVersion.containsKey(p.version())) {
                continue;
            }
            byVersion.put(p.version(), toRecord(c, objectId, p, null));
        }
        List<ObjectVersionRecord> out = new ArrayList<>(byVersion.values());
        out.sort((a, b) -> Integer.compare(b.versionNo(), a.versionNo()));
        return out;
    }

    @Override
    public Optional<ObjectVersionRecord> get(String objectId, int versionNo) {
        for (GitCommitView c : commits()) {
            Parsed p = parse(c, objectId);
            if (p == null || p.version() != versionNo) {
                continue;
            }
            Optional<String> content = archiver.readAtRef(REPO, ASSET_TYPE, objectId,
                    String.valueOf(versionNo), c.getCommitId());
            return Optional.of(toRecord(c, objectId, p, content.orElse(null)));
        }
        return Optional.empty();
    }

    @Override
    public int maxVersion(String objectId) {
        return list(objectId).stream().mapToInt(ObjectVersionRecord::versionNo).max().orElse(0);
    }

    private List<GitCommitView> commits() {
        GitRepository repo;
        try {
            repo = repositoryService.getRepositoryById(REPO);
        } catch (GitRepositoryService.GitRepositoryException e) {
            throw new IllegalStateException("对象版本仓库未注册: " + REPO + " - " + e.getMessage(), e);
        }
        if (repo == null || repo.getLocalPath() == null || repo.getLocalPath().isBlank()) {
            throw new IllegalStateException("对象版本仓库未解析 localPath: " + REPO);
        }
        try {
            List<GitCommitView> rows = gitService.listCommits(repo, 0, GitService.MAX_COMMIT_LOG_LIMIT);
            return rows == null ? List.of() : rows;
        } catch (GitService.GitException e) {
            throw new IllegalStateException("对象版本 Git 历史读取失败: " + e.getMessage(), e);
        }
    }

    private record Parsed(int version, String entityCode) {
    }

    private static Parsed parse(GitCommitView c, String objectId) {
        String msg = c.getMessage() == null ? "" : c.getMessage().trim();
        String prefix = PREFIX + objectId + " v";
        if (!msg.startsWith(prefix)) {
            return null;
        }
        String rest = msg.substring(prefix.length()).trim();
        int space = rest.indexOf(' ');
        String ver = space < 0 ? rest : rest.substring(0, space);
        String entity = null;
        if (space > 0) {
            String tail = rest.substring(space + 1).trim();
            if (tail.startsWith("entity=")) {
                entity = tail.substring("entity=".length()).trim();
            }
        }
        try {
            return new Parsed(Integer.parseInt(ver), entity);
        } catch (NumberFormatException e) {
            log.warn("对象版本提交信息不可解析（跳过但留痕）commit={} msg={}", c.getCommitId(), msg);
            return null;
        }
    }

    private static ObjectVersionRecord toRecord(GitCommitView c, String objectId, Parsed p,
                                                String snapshot) {
        return new ObjectVersionRecord(
                ID_PREFIX + c.getCommitId(),
                objectId,
                p.entityCode,
                p.version,
                snapshot,
                c.getMessage(),
                c.getAuthorName(),
                c.getCommittedAt() == null ? null : c.getCommittedAt().getTime());
    }
}
