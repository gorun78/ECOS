package com.chinacreator.gzcm.engine.ai.template;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.chinacreator.gzcm.engine.ai.dao.AipPipelineDao;
import com.chinacreator.gzcm.engine.ai.dataobject.AipPipelineDO;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentMissionDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentMissionDO;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.PlaybookContentSource;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.PlaybookRenderer;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentPlan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模板服务（批 2/3）：目录去重、内容源缺席如实 422、实例化落库与报告。
 */
class AipPipelineTemplateServiceTest {

    private static WAgentMissionDO mission(String id, String ref, String version) {
        WAgentMissionDO m = new WAgentMissionDO();
        m.setId(id);
        m.setName("mission-" + id);
        m.setPlaybookGitRef(ref);
        m.setPlaybookVersion(version);
        m.setDomain("default");
        return m;
    }

    private static String playbookJson() {
        return WAgentPlan.PlanCodec.toJson(new WAgentPlan.Plan(
                com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.PlanSource.PLAYBOOK,
                "repo/pb1", "v1",
                List.of(
                        new WAgentPlan.Step("s1", "tool", "ont.query", null, 1, List.of(), Map.of(), false, false, null),
                        new WAgentPlan.Step("s2", "human_gate", null, null, 1, List.of("s1"), Map.of(), true, false, "perm.approve"),
                        new WAgentPlan.Step("s3", "sub_run", null, null, 1, List.of("s2"), Map.of(), false, false, null))));
    }

    private static <T> ObjectProvider<T> providerOf(T value) {
        return new ObjectProvider<>() {
            @Override public T getObject(Object... args) { return value; }
            @Override public T getIfAvailable() { return value; }
            @Override public T getIfUnique() { return value; }
            @Override public T getObject() { return value; }
        };
    }

    private static class CapturingPipelineDao extends AipPipelineDao {
        AipPipelineDO inserted;
        @Override public int insert(AipPipelineDO pipeline) {
            inserted = pipeline;
            return 1;
        }
        @Override public List<Map<String, Object>> queryCandidateTemplates(String tenantId) {
            return List.of(Map.of(
                    "id", "cand-1",
                    "name", "候选模板A",
                    "payload_json", "{\"playbookGitRef\":\"repo/cand-pb\",\"playbookVersion\":\"v9\"}"));
        }
    }

    private static class FixedMissionDao extends WAgentMissionDao {
        private final List<WAgentMissionDO> rows;
        FixedMissionDao(List<WAgentMissionDO> rows) { this.rows = rows; }
        @Override public List<WAgentMissionDO> findWithPlaybookRef(String tenantId) { return rows; }
    }

    @Test
    @DisplayName("目录：mission 源按 ref@version 去重，叠加 candidate 来源（payload 声明引用才算）")
    void listsPlaybookAndCandidateSources() {
        CapturingPipelineDao dao = new CapturingPipelineDao();
        AipPipelineTemplateService service = new AipPipelineTemplateService(
                new FixedMissionDao(List.of(
                        mission("m1", "repo/pb1", "v1"),
                        mission("m2", "repo/pb1", "v1"),   // 重复去重
                        mission("m3", "repo/pb2", "v2"))),
                dao, new PlaybookRenderer(), new PlaybookToGraphConverter(), providerOf(null));

        List<AipPipelineTemplateService.TemplateItem> items = service.listTemplates("t1");

        assertEquals(List.of("repo/pb1@v1", "repo/pb2@v2", "repo/cand-pb@v9"),
                items.stream().map(i -> i.ref() + "@" + i.version()).toList());
        assertEquals("PLAYBOOK", items.get(0).source());
        assertEquals("CANDIDATE", items.get(2).source());
    }

    @Test
    @DisplayName("实例化：内容源缺席 ⇒ 422 且不落库（禁伪造正文）")
    void instantiateWithoutContentSourceFailsLoud() {
        CapturingPipelineDao dao = new CapturingPipelineDao();
        AipPipelineTemplateService service = new AipPipelineTemplateService(
                new FixedMissionDao(List.of(mission("m1", "repo/pb1", "v1"))),
                dao, new PlaybookRenderer(), new PlaybookToGraphConverter(), providerOf(null));

        AipPipelineTemplateService.TemplateValidationException e = assertThrows(
                AipPipelineTemplateService.TemplateValidationException.class,
                () -> service.instantiate("t1", "u1", "repo/pb1", "新编排", Map.of()));
        assertTrue(e.getMessage().contains("内容源未装配"));
        assertEquals(null, dao.inserted, "不得落库");
    }

    @Test
    @DisplayName("实例化：正常路径落库 + 转换报告如实上报 sub_run 未映射")
    void instantiatePersistsAndReportsUnmapped() {
        CapturingPipelineDao dao = new CapturingPipelineDao();
        PlaybookContentSource source = (tenantId, ref, version) -> Optional.of(playbookJson());
        AipPipelineTemplateService service = new AipPipelineTemplateService(
                new FixedMissionDao(List.of(mission("m1", "repo/pb1", "v1"))),
                dao, new PlaybookRenderer(), new PlaybookToGraphConverter(), providerOf(source));

        AipPipelineTemplateService.InstantiateResult result =
                service.instantiate("t1", "u1", "repo/pb1", "   新编排  ", Map.of());

        assertNotNull(dao.inserted, "必须落库");
        assertEquals("新编排", dao.inserted.getName(), "名称应 trim");
        assertEquals("repo/pb1", dao.inserted.getTemplateRef());
        assertEquals("draft", dao.inserted.getStatus());
        assertTrue(dao.inserted.getDefinitionJson().contains("\"graph\""), "定义需含 graph 快照");
        assertTrue(dao.inserted.getDefinitionJson().contains("human_gate")
                        || dao.inserted.getDefinitionJson().contains("approval"),
                "human_gate 应映射为 approval 节点");
        assertEquals(2, result.mapped());
        assertEquals(List.of("s3:sub_run"), result.unmapped());
    }

    @Test
    @DisplayName("实例化：模板 ref 不存在 ⇒ 404")
    void instantiateUnknownRefFails404() {
        AipPipelineTemplateService service = new AipPipelineTemplateService(
                new FixedMissionDao(List.of(mission("m1", "repo/pb1", "v1"))),
                new CapturingPipelineDao(), new PlaybookRenderer(), new PlaybookToGraphConverter(), providerOf(null));

        AipPipelineTemplateService.TemplateNotFoundException e = assertThrows(
                AipPipelineTemplateService.TemplateNotFoundException.class,
                () -> service.instantiate("t1", "u1", "repo/missing", "x", Map.of()));
        assertTrue(e.getMessage().contains("模板不存在"));
    }
}
