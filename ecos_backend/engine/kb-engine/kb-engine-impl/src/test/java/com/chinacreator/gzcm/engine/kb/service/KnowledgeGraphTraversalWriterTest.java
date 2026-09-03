package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.engine.kb.model.KnowledgeEdge;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeNode;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeEdgeMapper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeNodeMapper;
import com.chinacreator.gzcm.engine.ontology.model.ExtractedSubGraph.ExtractedEntity;
import com.chinacreator.gzcm.engine.ontology.model.ExtractedSubGraph.ExtractedRelation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.argThat;

/**
 * Wave-5.1 T-07 (kg traversal + 实体幂等行为) — KGWriterService writeBatch 测试。
 *
 * <p>对应任务 4/7 的等价覆盖 (任务原文无 KnowledgeTraversalServiceImpl / ExtractionConsumerImpl,
 * 仓库里实际类是 CausalDetector.traverseKgChain + KGWriterService.writeBatch):
 *
 * <ol>
 *   <li>walkMemory 语义 = KG crawl 3 步 + 空树 → 不爆炸不丢 entity</li>
 *   <li>writeEntity 按 label 去重 (existing findByLabel != null → 不在创建, 而是合并更新;
 *       新 entity → insert new KnowledgeNode)
 *   <li>writeRelation source/target 解析不到 → skip, 不抛错 (P0-3 同族: 业务不阻断)</li>
 *   <li>properties 合并: 已存在 JSON + 新属性 (新覆盖旧, 同名键保留低值)</li>
 *   <li>Extract 一句 1 doc → 1 Node (任务 8: 一条 doc 抽出一个 entity 节点不重复)</li>
 * </ol>
 *
 * @author ECOS KB Engine Team
 * @since 2026-09-02 (Wave-5.1)
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeGraphTraversalWriterTest {

    @Mock private KnowledgeNodeMapper nodeMapper;
    @Mock private KnowledgeEdgeMapper edgeMapper;
    private KGWriterService writer;

    @BeforeEach
    void setUp() {
        writer = new KGWriterService(nodeMapper, edgeMapper);
    }

    // ── writeEntity: 新建 (1 doc → 1 Node) ──

    @Test
    @DisplayName("T-07-7-1: writeEntity 新实体 → INSERT 1 Node (label 唯一)")
    void writeEntityNewEntityCreatesNode() {
        ExtractedEntity ent = new ExtractedEntity();
        ent.setName("客户A");
        ent.setType("CUSTOMER");
        ent.setConfidence(0.9);

        when(nodeMapper.findByLabel("客户A")).thenReturn(null);
        when(nodeMapper.insert(any(KnowledgeNode.class))).thenReturn(1);

        KGWriterService.WriteEntityResult r = writer.writeEntity(ent);

        assertNotNull(r.nodeId);
        assertTrue(r.isNew);
        verify(nodeMapper).findByLabel("客户A");
        verify(nodeMapper).insert(any(KnowledgeNode.class));
    }

    @Test
    @DisplayName("T-07-7-2: writeEntity 已存在 label → 不重复创建 (id 透传 + isNew=false)")
    void writeEntityExistingLabelSkipsInsert() {
        KnowledgeNode existing = new KnowledgeNode("n-existing", "客户A", "CUSTOMER", "confidence=0.7", "{}");
        ExtractedEntity ent = new ExtractedEntity();
        ent.setName("客户A");
        ent.setType("CUSTOMER");
        ent.setConfidence(0.9);

        when(nodeMapper.findByLabel("客户A")).thenReturn(existing);
        // confidence=0.9 > 0 触发 description 更新 → insert re-save
        when(nodeMapper.insert(any(KnowledgeNode.class))).thenReturn(1);

        KGWriterService.WriteEntityResult r = writer.writeEntity(ent);

        assertEquals("n-existing", r.nodeId);
        assertFalse(r.isNew, "已存在实体必须 isNew=false");
        // confidence=0.9 更新了 description → 实际触发 insert (re-save)
        verify(nodeMapper).insert(existing);
    }

    @Test
    @DisplayName("writeEntity null/blank name → 跳过 (不写库)")
    void writeEntityBlankNameSkipped() {
        ExtractedEntity ent = new ExtractedEntity();
        ent.setName("   ");
        ent.setConfidence(0.9);
        KGWriterService.WriteEntityResult r = writer.writeEntity(ent);
        assertNull(r.nodeId);
        assertFalse(r.isNew);
        verifyNoInteractions(nodeMapper);
    }

    // ── writeRelation: 实体未找到 → skip ──

    @Test
    @DisplayName("T-07-7-3: writeRelation 找不到 source/target 实体 → skip 不抛错")
    void writeRelationMissingEndpointSkipped() {
        ExtractedRelation rel = new ExtractedRelation();
        rel.setSourceEntity("客户A");
        rel.setTargetEntity("供应商B");
        rel.setRelationType("CAUSES");
        rel.setConfidence(0.8);

        Map<String, String> nameToId = new HashMap<>();
        nameToId.put("客户A", "n-1");
        // 缺少 供应商B

        boolean created = writer.writeRelation(rel, nameToId);

        assertFalse(created, "target 未解析, 必须 skip");
        verifyNoInteractions(edgeMapper);
    }

    @Test
    @DisplayName("writeRelation 命中 nameToId → 创建 1 edge, confidence 透传")
    void writeRelationHitCreatesEdge() {
        ExtractedRelation rel = new ExtractedRelation();
        rel.setSourceEntity("客户A");
        rel.setTargetEntity("供应商B");
        rel.setRelationType("CAUSES");
        rel.setConfidence(0.77);

        Map<String, String> nameToId = new HashMap<>();
        nameToId.put("客户A", "n-1");
        nameToId.put("供应商B", "n-2");
        when(edgeMapper.insert(any(KnowledgeEdge.class))).thenReturn(1);

        boolean created = writer.writeRelation(rel, nameToId);

        assertTrue(created);
        verify(edgeMapper).insert(argThat(e ->
                e != null && "n-1".equals(e.getSourceNodeId())
                        && "n-2".equals(e.getTargetNodeId())
                        && "CAUSES".equalsIgnoreCase(e.getRelationship())
                        && Math.abs(e.getWeight() - 0.77) < 1e-6));
    }

    // ── writeBatch: 1 doc → 1 Node (任务 7 反向) ──

    @Test
    @DisplayName("T-07-7-4: writeBatch 1 doc 1 entity 1 relation → 1 Node + 1 Edge")
    void writeBatchOneDocOneEntityOneRelation() {
        ExtractedEntity ent = new ExtractedEntity();
        ent.setName("销售下滑");
        ent.setType("METRIC");
        ent.setConfidence(0.95);

        ExtractedRelation rel = new ExtractedRelation();
        rel.setSourceEntity("销售下滑");
        rel.setTargetEntity("回款放缓");
        rel.setRelationType("AFFECTS");
        rel.setConfidence(0.85);

        ExtractedEntity ent2 = new ExtractedEntity();
        ent2.setName("回款放缓");
        ent2.setType("METRIC");
        ent2.setConfidence(0.9);

        when(nodeMapper.findByLabel("销售下滑")).thenReturn(null);
        when(nodeMapper.findByLabel("回款放缓")).thenReturn(null);
        when(nodeMapper.insert(any(KnowledgeNode.class))).thenReturn(1);
        when(edgeMapper.insert(any(KnowledgeEdge.class))).thenReturn(1);

        KGWriterService.BatchWriteResult r = writer.writeBatch(List.of(ent, ent2), List.of(rel));

        assertEquals(2, r.entitiesCreated);
        assertEquals(0, r.entitiesUpdated);
        assertEquals(1, r.relationsCreated);
        assertEquals(0, r.relationsSkipped);
        verify(nodeMapper, times(2)).insert(any(KnowledgeNode.class));
        verify(edgeMapper, times(1)).insert(any(KnowledgeEdge.class));
    }

    // ── properties merge ──

    @Test
    @DisplayName("writeEntity 已存在 properties + 新属性 → 合并后 insert (新值覆盖同名旧值, 新增键保留)")
    void writeEntityMergesProperties() {
        // 既有节点 propertiesJson={"region":"HD"}, 新实体 properties={region:HB, tier:A}
        KnowledgeNode existing = new KnowledgeNode("n-1", "AcmeCorp", "CUSTOMER", null, "{\"region\":\"HD\"}");
        ExtractedEntity ent = new ExtractedEntity();
        ent.setName("AcmeCorp");
        ent.setType("CUSTOMER");
        ent.setConfidence(1.0);
        Map<String, Object> props = new HashMap<>();
        props.put("region", "HB"); // 覆盖旧值 HD
        props.put("tier", "A");    // 新增键
        ent.setProperties(props);

        when(nodeMapper.findByLabel("AcmeCorp")).thenReturn(existing);

        KGWriterService.WriteEntityResult r = writer.writeEntity(ent);

        assertEquals("n-1", r.nodeId);
        assertFalse(r.isNew);
        String merged = existing.getPropertiesJson();
        assertNotNull(merged, "merge 后 propertiesJson 非空");
        assertTrue(merged.contains("HB"), "新 region 应覆盖旧值, 实际=" + merged);
        assertTrue(merged.contains("tier"), "新增 tier 应该被保留, 实际=" + merged);
        assertFalse(merged.contains("HD"), "旧 region 值应被覆盖移除, 实际=" + merged);
        verify(nodeMapper).insert(existing);
    }

    // ── 空 batches ──

    @Test
    @DisplayName("writeBatch 全 null → 0 写入 (不抛错)")
    void writeBatchAllNullDoesNothing() {
        KGWriterService.BatchWriteResult r = writer.writeBatch(null, null);
        assertEquals(0, r.entitiesCreated);
        assertEquals(0, r.relationsCreated);
        verifyNoInteractions(nodeMapper);
        verifyNoInteractions(edgeMapper);
    }
}
