package com.chinacreator.gzcm.engine.ontology.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import com.chinacreator.gzcm.engine.ontology.dto.OntologyEntitySaveDTO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyEntityVO;
import com.chinacreator.gzcm.engine.ontology.model.OntologyDomain;
import com.chinacreator.gzcm.engine.ontology.model.OntologyEntity;
import com.chinacreator.gzcm.engine.ontology.repository.OntologyDomainRepository;
import com.chinacreator.gzcm.engine.ontology.repository.OntologyMappingStore;
import com.chinacreator.gzcm.engine.ontology.repository.OntologyRepository;

/**
 * 域实体端点按 {@code domain_id} 解析门禁（D136）。
 *
 * <p><b>病灶</b>：{@code /api/v1/ecos/ontologies/domains/{domainCode}/entities} 曾把 domainCode
 * 直接当 ontologyId 用——GET 走 {@code WHERE ontology_id = '<域码>'}（正常实体查不到），
 * POST 把域码写进 {@code ontology_id} 并让 {@code domain_id} 留空（写进去的行按域查询也查不到）。
 *
 * <p><b>本门钉三件事</b>：①按域查询走 {@code findEntitiesByDomain(域码)} 而不是按 ontologyId；
 * ②创建实体落 {@code domain_id} = 域主键；③未知域返回 {@code Optional.empty()}（调用方 404），
 * 不静默返回空数组——空数组与"域不存在"是两种语义，混同会把拼错的域码说成"该域没有实体"。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OntologyDomainEntityResolveTest {

    @Mock private OntologyRepository repository;
    @Mock private OntologyDomainRepository domainRepository;
    @Mock private ObjectProvider<OntologyDomainRepository> domainProvider;

    private final OntologyMappingStore mappingStore = new OntologyMappingStore();

    private OntologyService service;

    @BeforeEach
    void setUp() {
        service = new OntologyService(repository, mappingStore);
        try {
            var field = OntologyService.class.getDeclaredField("domainRepositoryProvider");
            field.setAccessible(true);
            field.set(service, domainProvider);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        when(domainProvider.getIfAvailable()).thenReturn(domainRepository);
        when(repository.nextId(anyString(), anyString())).thenReturn("ent9001");
    }

    private OntologyDomain finance() {
        OntologyDomain d = new OntologyDomain();
        d.setId("dom_fin");
        d.setCode("Finance");
        return d;
    }

    private OntologyEntity entityInDomain() {
        OntologyEntity e = new OntologyEntity();
        e.setId("ent501");
        e.setOntologyId("Finance");
        e.setDomainId("dom_fin");
        e.setCode("ENT_FIN_INVOICE");
        e.setName("财务发票");
        e.setEntityType("MASTER");
        e.setSortOrder(1);
        return e;
    }

    @Test
    @DisplayName("D133：ontologyId 为空 ⇒ 返回全量实体，不退化成 WHERE ontology_id = ''")
    void blankOntologyIdYieldsAllEntities() {
        when(repository.findAllEntities()).thenReturn(List.of(entityInDomain()));

        for (String blank : new String[]{null, "", "   "}) {
            List<OntologyEntityVO> all = service.listEntitiesVO(blank);
            assertEquals(1, all.size(), "空 ontologyId 必须返回全量（入参: " + blank + "）");
        }
        verify(repository, never()).findEntitiesByOntology(anyString());

        service.listEntitiesVO("ont001");
        verify(repository).findEntitiesByOntology("ont001");
    }

    @Test
    @DisplayName("D136：按域查实体走 domain_id 关联，不把域码当 ontologyId")
    void listEntitiesByDomainUsesDomainId() {
        when(domainRepository.findByCode("Finance")).thenReturn(Optional.of(finance()));
        when(repository.findEntitiesByDomain("Finance")).thenReturn(List.of(entityInDomain()));

        Optional<List<OntologyEntityVO>> result = service.listEntitiesByDomainVO("Finance");

        assertTrue(result.isPresent());
        assertEquals(1, result.get().size());
        assertEquals("dom_fin", result.get().get(0).getDomainId());
        verify(repository).findEntitiesByDomain("Finance");
        verify(repository, never()).findEntitiesByOntology(anyString());
    }

    @Test
    @DisplayName("D136：未知域返回 empty（调用方 404），不返回空数组冒充")
    void unknownDomainYieldsEmpty() {
        when(domainRepository.findByCode("NoSuchDomain")).thenReturn(Optional.empty());
        when(domainRepository.findById("NoSuchDomain")).thenReturn(Optional.empty());

        assertTrue(service.listEntitiesByDomainVO("NoSuchDomain").isEmpty());
        verify(repository, never()).findEntitiesByDomain(anyString());
        verify(repository, never()).findAllEntities();
    }

    @Test
    @DisplayName("D136：域内创建实体落 domain_id = 域主键")
    void createEntityInDomainPersistsDomainId() {
        when(domainRepository.findByCode("Finance")).thenReturn(Optional.of(finance()));

        OntologyEntitySaveDTO dto = new OntologyEntitySaveDTO();
        dto.setCode("ENT_FIN_INVOICE");
        dto.setName("财务发票");
        dto.setEntityType("MASTER");

        Optional<OntologyEntityVO> created = service.createEntityInDomain("Finance", dto);

        assertTrue(created.isPresent());
        assertEquals("dom_fin", created.get().getDomainId());
        var captor = org.mockito.ArgumentCaptor.forClass(OntologyEntity.class);
        verify(repository).insertEntity(captor.capture());
        assertEquals("dom_fin", captor.getValue().getDomainId(),
                "insertEntity 必须带上解析出的域主键，否则按域查询查不到");
        assertFalse(captor.getValue().getDomainId().isBlank());
    }
}
