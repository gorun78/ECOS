package com.chinacreator.gzcm.engine.ontology;

import com.chinacreator.gzcm.engine.ontology.git.OntologyGitArchiveRequest;
import com.chinacreator.gzcm.engine.ontology.git.OntologyGitArchiveResult;
import com.chinacreator.gzcm.engine.ontology.git.OntologyGitReadResult;
import com.chinacreator.gzcm.engine.ontology.git.OntologyGitSyncResult;

public interface OntologyGitService {

    // ── 强类型契约（VG-2 收口：Map 垫片已删，服务契约不再有 Map 出入参）──

    /**
     * 从服务端注册的 remote 拉取到工作树（{@code origin} + 仓库缺省分支）。
     *
     * <p>仓库只由服务端 {@code repositoryId} 定位；缺 id / 未注册 / 禁用 / git 不可用一律
     * {@code not_available} + reason，<b>禁伪成功</b>。</p>
     */
    OntologyGitSyncResult pullVersion(String ontologyId, String repositoryId);

    /**
     * 按服务端 {@code repositoryId} 拉取（{@code /load} 语义，等价 pull）。
     *
     * <p>客户端 {@code url}/远端地址不入参；缺 id 直接 {@code not_available}。</p>
     */
    OntologyGitSyncResult loadVersion(String repositoryId);

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
