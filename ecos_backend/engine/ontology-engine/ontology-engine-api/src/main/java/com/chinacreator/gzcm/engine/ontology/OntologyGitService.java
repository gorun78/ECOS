package com.chinacreator.gzcm.engine.ontology;

import java.util.Map;

import com.chinacreator.gzcm.engine.ontology.git.OntologyGitArchiveRequest;
import com.chinacreator.gzcm.engine.ontology.git.OntologyGitArchiveResult;
import com.chinacreator.gzcm.engine.ontology.git.OntologyGitReadResult;

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

    /**
     * 按 git ref 读回某版本归档内容（#147E；§2.9.6② 回滚 / §2.9.6③ 对账的取证腿）。
     *
     * <p>只从对象库按引用读取，<b>不</b>读工作树当前文件——否则历史版本被后续发布覆盖时
     * 会把新内容当成旧版本，回滚与对账都变成假证据。不可用（未注册/引用缺失/该 ref 下
     * 无此文件）一律 {@code STATUS_NOT_AVAILABLE} + 原因，禁返回空内容冒充"版本为空"。</p>
     */
    OntologyGitReadResult readVersionAtRef(String repositoryId, String assetType,
                                           String assetId, String versionNo, String ref);
}
