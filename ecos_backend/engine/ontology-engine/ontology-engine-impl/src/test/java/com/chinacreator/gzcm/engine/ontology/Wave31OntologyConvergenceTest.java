package com.chinacreator.gzcm.engine.ontology;

import com.chinacreator.gzcm.common.context.TenantContextHolder;
import com.chinacreator.gzcm.engine.ontology.model.OntologyDomain;
import com.chinacreator.gzcm.engine.ontology.model.OntologyEntity;
import com.chinacreator.gzcm.engine.ontology.model.OntologyVersion;
import com.chinacreator.gzcm.engine.ontology.repository.OntologyDomainRepository;
import com.chinacreator.gzcm.engine.ontology.repository.OntologyRepository;
import com.chinacreator.gzcm.engine.ontology.repository.OntologyVersionRepository;
import com.chinacreator.gzcm.engine.ontology.service.OntologyDomainService;
import com.chinacreator.gzcm.engine.ontology.service.OntologyProposalService;
import com.chinacreator.gzcm.engine.ontology.service.OntologyService;
import com.chinacreator.gzcm.engine.ontology.service.OntologyVersionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PMO-28~31 Wave3.1 收口单测 (5 case)
 *
 * <ol>
 *   <li>C1 — Entity/Relation CRUD: OntologyService 走通 createEntity + createRelationship</li>
 *   <li>C2 — Domain RLS: tenant-b 上下文查 tenant-a 的 domain 返回 null</li>
 *   <li>C3 — Domain search: 租户上下文 + 关键词模糊匹配</li>
 *   <li>C4 — 乐观锁版本号失配 → IllegalStateException 带 ONT-409</li>
 *   <li>C5 — publishFromProposal 成功路径 + 冲突路径</li>
 * </ol>
 *
 * <p>全 mock, 不依赖真实 PG/Neo4j。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class Wave31OntologyConvergenceTest {

    @Mock private OntologyVersionRepository versionRepo;
    @Mock private OntologyRepository ontRepo;
    @Mock private OntologyDomainRepository domainRepo;
    // P0: OntologyMappingStore 的 `public final Map store = new ConcurrentHashMap<>()` 用 mock 会 NPE
    // (Mockito 默认 field 初始化 skipped), 改用真实 instance (重 feder memcfg OFF, Pi can) 模拟 in-process 内存存储。
    private final com.chinacreator.gzcm.engine.ontology.repository.OntologyMappingStore mappingStore =
            new com.chinacreator.gzcm.engine.ontology.repository.OntologyMappingStore();

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    // ═══════════════ C1: Entity/Relation CRUD ═════════════

    @Test
    @DisplayName("C1: Entity/Relation CRUD — createEntity + createRelationship 走通")
    void c1_entityRelationCrud() {
        OntologyService svc = new OntologyService(ontRepo, mappingStore);

        // ── 创建 entity ──
        Map<String, Object> body = new HashMap<>();
        body.put("code", "ENT_FIN_INVOICE");
        body.put("name", "财务发票");
        body.put("entityType", "MASTER");
        body.put("description", "财务域发票实体");
        Map<String, Object> created = svc.createEntity("ont001", body);
        assertNotNull(created.get("id"), "新建实体应返回 id");
        assertEquals("ENT_FIN_INVOICE", created.get("code"));
        verify(ontRepo, times(1)).insertEntity(any());

        // ── 创建 relation ──
        Map<String, Object> relBody = new HashMap<>();
        relBody.put("targetEntityId", "ent992");
        relBody.put("code", "BELONGS_TO");
        relBody.put("name", "归属");
        Map<String, Object> rel = svc.createRelationship("ent991", relBody);
        assertNotNull(rel.get("id"), "新建关系应返回 id");
        assertEquals("BELONGS_TO", rel.get("code"));
        verify(ontRepo, times(1)).insertRelationship(any());

        // ── 删除 entity 级联 (findEntityById 存在 → 走 deleteProperties/Relationships/Actions/Entity) ──
        OntologyEntity stub = new OntologyEntity();
        stub.setId("ent991");
        stub.setOntologyId("ont001");
        stub.setCode("STUB");
        stub.setName("Stub");
        stub.setEntityType("MASTER");
        when(ontRepo.findEntityById("ent991")).thenReturn(Optional.of(stub));
        boolean deleted = svc.deleteEntity("ent991");
        assertTrue(deleted, "deleteEntity 在实体存在时应返回 true");
        verify(ontRepo).deletePropertiesByEntity("ent991");
        verify(ontRepo).deleteRelationshipsByEntity("ent991");
        verify(ontRepo).deleteActionsByEntity("ent991");
        verify(ontRepo).deleteEntity("ent991");
    }

    // ═══════════════ C2: Domain 多租户 RLS ═════════════

    @Test
    @DisplayName("C2: Domain RLS — tenant-b 上下文查 tenant-a 的 domain 应返回 null")
    void c2_domainTenantIsolationByCode() {
        TenantContextHolder.setTenantId("tenant-b");
        try {
            OntologyDomainService svc = new OntologyDomainService(domainRepo, ontRepo);

            // tenant-b 查 tenant-a 的 domain → empty → null
            when(domainRepo.findByCode("finance-a")).thenReturn(Optional.empty());
            assertNull(svc.getDomain("finance-a"),
                "其他租户的 domain 在 tenant-b 上下文中应不可见");

            // tenant-b 查本租户 domain → 命中
            OntologyDomain bDomain = new OntologyDomain();
            bDomain.setId("dom-b1");
            bDomain.setCode("finance-b");
            bDomain.setName("财务B");
            bDomain.setStatus("Active");
            bDomain.setSortOrder(1);
            when(domainRepo.findByCode("finance-b")).thenReturn(Optional.of(bDomain));
            assertNotNull(svc.getDomain("finance-b"));
        } finally {
            TenantContextHolder.clear();
        }
    }

    // ═══════════════ C3: Domain search RLS + keyword ═════════════

    @Test
    @DisplayName("C3: Domain search — tenant 隔离 + 关键字模糊匹配")
    void c3_domainSearchRlsAndKeyword() {
        TenantContextHolder.setTenantId("tenant-b");
        try {
            OntologyDomainService svc = new OntologyDomainService(domainRepo, ontRepo);

            OntologyDomain hitA = new OntologyDomain();
            hitA.setId("dom-b1");
            hitA.setCode("F1");
            hitA.setName("财务报表");
            hitA.setStatus("Active");
            hitA.setSortOrder(1);
            OntologyDomain hitB = new OntologyDomain();
            hitB.setId("dom-b2");
            hitB.setCode("F2");
            hitB.setName("财务预算");
            hitB.setStatus("Active");
            hitB.setSortOrder(2);

            when(domainRepo.searchDomains("财务", 20)).thenReturn(List.of(hitA, hitB));

            List<Map<String, Object>> hits = svc.searchDomains("财务", 20);
            assertEquals(2, hits.size());
            assertEquals("F1", hits.get(0).get("code"));
            assertEquals("财务报表", hits.get(0).get("name"));
        } finally {
            TenantContextHolder.clear();
        }
    }

    // ═══════════════ C4: 版本乐观锁版本号失配 ═════════════

    @Test
    @DisplayName("C4: 乐观锁 — 版本号失配抛 IllegalStateException 带 ONT-409")
    void c4_optimisticLockVersionMismatch() {
        OntologyProposalService proposal = mock(OntologyProposalService.class);
        OntologyVersionService svc = new OntologyVersionService(versionRepo, ontRepo, proposal);

        // 提案 999 当前版本号=4, 客户端发 5 → mismatch
        when(proposal.optimisticTransition(eq("999"), eq("APPROVED"),
            eq("user-x"), any(), eq(5))).thenReturn(0);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> svc.publishFromProposal("ont001", "999", 5, "user-x"));
        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().contains("ONT-409"), "应带 ONT-409 错误码");
        assertTrue(ex.getMessage().contains("OPTIMISTIC_LOCK_CONFLICT"), "应带冲突标识");
        // 验证: 没走到 createVersion (事务性回滚)
        verify(versionRepo, never()).insert(any());
        verify(versionRepo, never()).updateStatus(anyString(), anyString());
    }

    // ═══════════════ C5: publishFromProposal 双路径 ═════════════

    @Test
    @DisplayName("C5: publishFromProposal — 成功路径 + 冲突路径")
    void c5_publishFromProposalHappypathAndConflict() {
        OntologyProposalService proposal = mock(OntologyProposalService.class);
        OntologyVersionService svc = new OntologyVersionService(versionRepo, ontRepo, proposal);

        // ── 成功路径: 版本号匹配 → 走 publish 流程 ──
        OntologyVersion existing = new OntologyVersion();
        existing.setId("ver9001");
        existing.setOntologyId("ont001");
        existing.setVersionNo("1.0.0");
        existing.setStatus("Published");
        existing.setSnapshot("{\"entities\":[],\"properties\":[],\"relationships\":[],\"actions\":[]}");
        existing.setChangeLog("initial");
        existing.setPublisher("seed");
        existing.setCreatedAt(java.time.LocalDateTime.of(2026, 7, 1, 10, 0, 0));

        when(versionRepo.findLatestPublished("ont001")).thenReturn(Optional.of(existing));
        when(ontRepo.findEntitiesByOntology("ont001")).thenReturn(List.of());
        // versionService.createVersion 内部会调 versionRepository.insert → mock 默认返回 1
        when(versionRepo.findById(anyString())).thenAnswer(inv -> {
            OntologyVersion v = new OntologyVersion();
            v.setId(inv.getArgument(0));
            v.setOntologyId("ont001");
            v.setVersionNo("1.0.1");
            v.setStatus("Draft");
            v.setSnapshot("[]");
            return Optional.of(v);
        });
        when(versionRepo.findLatestPublished("ont001")).thenReturn(Optional.of(existing));
        // publishVersion 调 versionRepository.findById 返回 ver 实例后调 updateStatus
        // 让 updateStatus 返回 1
        when(versionRepo.updateStatus(anyString(), eq("Published"))).thenReturn(1);
        // 让 proposal.transition 返回 1
        when(proposal.optimisticTransition(eq("555"), eq("APPROVED"), eq("sysadmin"), any(), eq(7)))
            .thenReturn(1);

        // 成功路径
        var ok = svc.publishFromProposal("ont001", "555", 7, "sysadmin");
        assertNotNull(ok, "成功路径应返回 publish 结果");
        verify(proposal, times(1)).optimisticTransition(
            eq("555"), eq("APPROVED"), eq("sysadmin"), any(), eq(7));

        // ── 冲突路径: 版本号失配 → 409 ──
        when(proposal.optimisticTransition(eq("555"), eq("APPROVED"), eq("sysadmin"), any(), eq(3)))
            .thenReturn(0); // conflict
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> svc.publishFromProposal("ont001", "555", 3, "sysadmin"));
        assertTrue(ex.getMessage().startsWith("ONT-409"));
    }
}
