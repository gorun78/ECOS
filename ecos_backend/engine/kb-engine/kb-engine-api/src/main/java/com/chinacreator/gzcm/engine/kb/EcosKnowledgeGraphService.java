package com.chinacreator.gzcm.engine.kb;

import java.util.Map;

public interface EcosKnowledgeGraphService {

    Map<String, Object> getGraphSnapshot();

    Map<String, Object> syncToNeo4j();

    /**
     * 真实投影 PG 权威图谱到 Neo4j 并做 PG↔Neo4j 计数对账（附录 A 第 15 条 triggerGraphSync
     * 的投影腿；详设 §7.2.3「投影/对账/诚实」三行）。
     *
     * <p>语义边界：</p>
     * <ul>
     *   <li>Neo4j 投影不可用（standard 档）→ 200 {@code graphForm="PG_ONLY"}，{@code attempted=0}、
     *       {@code written=0}、{@code notAttempted=PG 节点数}+原因；<b>不以 PG 为失败</b>；</li>
     *   <li>可用 → 按 {@code MERGE} 写节点/边（固定标签 {@code KGNode}／关系 {@code KG_REL}，
     *       原始边 type 存属性，禁动态关系类型拼接），随后按 {@code node_type} 与总数对账；</li>
     *   <li>对账差异非零或投影计数不可读 → 抛 500 {@code ECOS-KB-080}（谜底在 message 的
     *       {@code graphForm=MISMATCH} 与逐类型明细，禁以 200 掩盖漂移）。</li>
     * </ul>
     *
     * @param fullResync {@code true} = 先清空 {@code :KGNode} 子图再全量重写（脏投影的重放手段）
     * @return {@code {graphForm,attempted,written,notAttempted,reason,reconcileDiff}}
     */
    Map<String, Object> projectGraph(boolean fullResync);

    /**
     * 图谱双形态只读状态（附录 A 第 15 条 {@code GET /api/v1/knowledge/graph/sync/status}）。
     *
     * <p>返回 {@code {graphForm,lastSyncTime,diff}}；{@code graphForm} 三态：
     * {@code PG_ONLY}（无投影或投影计数不可读）、{@code PROJECTED}（对账一致）、
     * {@code MISMATCH}（对账漂移，body 另带 {@code mismatchCode=ECOS-KB-080}）。
     * <b>读面不因漂移变错误</b>——漂移本身就是被读的对象；台账/PG 读故障仍上浮 500
     * {@code ECOS-KB-050}（禁空态伪装）。</p>
     */
    Map<String, Object> graphFormStatus();
}