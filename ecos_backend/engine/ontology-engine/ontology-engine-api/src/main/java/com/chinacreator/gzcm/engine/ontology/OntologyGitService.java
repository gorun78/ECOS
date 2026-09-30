package com.chinacreator.gzcm.engine.ontology;

import java.util.Map;

import com.chinacreator.gzcm.engine.ontology.git.OntologyGitArchiveRequest;
import com.chinacreator.gzcm.engine.ontology.git.OntologyGitArchiveResult;

public interface OntologyGitService {

    // ── 旧 Map 契约（API 只增不改，Wave31 mock 兼容；内部委托至下方强类型 archive）──

    Map<String, Object> commit(String ontologyId, Map<String, Object> body);

    Map<String, Object> pull(String ontologyId, Map<String, Object> body);

    Map<String, Object> load(Map<String, Object> body);

    // ── 强类型契约（VG-7，H4-T4 真实 Git 归档落地入口）──

    /**
     * 将版本资产内容写入仓库工作树 → {@code commitAllChanges} → 按
     * {@code {assetType}/{assetId}/{versionNo}} 打 tag → 回真实 {@code commitId}
     * （来自 {@code getCurrentCommitId}，禁止伪造）。
     *
     * <p>仓库仅由服务端 {@code repositoryId}（runtime 注册表）定位；缺失仓库、
     * runtime 不可用、git 不可用时返回 {@code not_available} + reason，禁止伪成功。
     */
    OntologyGitArchiveResult archiveVersion(OntologyGitArchiveRequest request);
}
