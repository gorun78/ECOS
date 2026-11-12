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
                dao, new PlaybookRenderer(), new PlaybookToGraphConverter(), providerOf(null),
                new BuiltinPipelineTemplateCatalog());

        List<AipPipelineTemplateService.TemplateItem> items = service.listTemplates("t1");
        List<AipPipelineTemplateService.TemplateItem> nonBuiltin = items.stream()
                .filter(i -> !AipPipelineTemplateService.SOURCE_BUILTIN.equals(i.source())).toList();

        assertEquals(List.of("repo/pb1@v1", "repo/pb2@v2", "repo/cand-pb@v9"),
                nonBuiltin.stream().map(i -> i.ref() + "@" + i.version()).toList());
        assertEquals(AipPipelineTemplateService.SOURCE_PLAYBOOK, nonBuiltin.get(0).source());
        assertEquals(AipPipelineTemplateService.SOURCE_CANDIDATE, nonBuiltin.get(2).source());
    }

    private static class EmptyPipelineDao extends CapturingPipelineDao {
        @Override public List<Map<String, Object>> queryCandidateTemplates(String tenantId) {
            return List.of();
        }
    }

    @Test
    @DisplayName("目录：内置八意图模板恒在最前（不依赖内容源与 DB 引用）")
    void listsBuiltinTemplatesFirstEvenWithEmptyDb() {
        AipPipelineTemplateService service = new AipPipelineTemplateService(
                new FixedMissionDao(List.of()), new EmptyPipelineDao(),
                new PlaybookRenderer(), new PlaybookToGraphConverter(), providerOf(null),
                new BuiltinPipelineTemplateCatalog());

        List<AipPipelineTemplateService.TemplateItem> items = service.listTemplates("t1");

        assertEquals(8, items.size(), "八个 GoalIntent 各一枚");
        assertEquals(items.size(), items.stream()
                .filter(i -> AipPipelineTemplateService.SOURCE_BUILTIN.equals(i.source())).count());
        assertTrue(items.get(0).ref().startsWith(BuiltinPipelineTemplateCatalog.REF_PREFIX));
        assertTrue(items.get(0).ref().endsWith("FORECAST"), "首枚应为 FORECAST（GoalIntent 声明序）");
    }

    @Test
    @DisplayName("实例化：内容源缺席 ⇒ 422 且不落库（禁伪造正文）")
    void instantiateWithoutContentSourceFailsLoud() {
        CapturingPipelineDao dao = new CapturingPipelineDao();
        AipPipelineTemplateService service = new AipPipelineTemplateService(
                new FixedMissionDao(List.of(mission("m1", "repo/pb1", "v1"))),
                dao, new PlaybookRenderer(), new PlaybookToGraphConverter(), providerOf(null),
                new BuiltinPipelineTemplateCatalog());

        AipPipelineTemplateService.TemplateValidationException e = assertThrows(
                AipPipelineTemplateService.TemplateValidationException.class,
                () -> service.instantiate("t1", "u1", "repo/pb1", "新编排", Map.of()));
        assertTrue(e.getMessage().contains("内容源未装配"));
        assertEquals(null, dao.inserted, "不得落库");
    }

    @Test
    @DisplayName("实例化内置模板：内容源缺席也能生成骨架（human_gate→tool→aggregate）并落库")
    void instantiateBuiltinWorksWithoutContentSource() {
        CapturingPipelineDao dao = new CapturingPipelineDao();
        AipPipelineTemplateService service = new AipPipelineTemplateService(
                new FixedMissionDao(List.of()),
                dao, new PlaybookRenderer(), new PlaybookToGraphConverter(), providerOf(null),
                new BuiltinPipelineTemplateCatalog());

        AipPipelineTemplateService.InstantiateResult result = service.instantiate(
                "t1", "u1",
                BuiltinPipelineTemplateCatalog.REF_PREFIX + "FORECAST", "  我的预测流程  ", Map.of());

        assertNotNull(dao.inserted, "内置模板必须落库（不依赖 Playbook 内容源）");
        assertEquals("我的预测流程", dao.inserted.getName(), "名称应 trim");
        assertEquals(BuiltinPipelineTemplateCatalog.REF_PREFIX + "FORECAST", dao.inserted.getTemplateRef());
        assertEquals("draft", dao.inserted.getStatus());
        assertTrue(dao.inserted.getDefinitionJson().contains("\"graph\""), "定义需含 graph 快照");
        // 端点出参经 DefinitionJsonCodec.stepsOf 只读外层 steps 键：少它则画布空白（实机踩过）
        assertTrue(dao.inserted.getDefinitionJson().contains("\"steps\""),
                "definition_json 必须有外层 steps 键，否则端点回读不到画布");
        assertTrue(DefinitionJsonCodec.read(dao.inserted.getDefinitionJson()).containsKey("steps"));
        // FORECAST = freeze_data_snapshot + run_profit_forecast ⇒ 闸门+2工具+聚合 = 4 节点
        assertEquals(4, result.mapped());
        assertEquals(List.of(), result.unmapped(), "内置骨架全部可映射");
        assertTrue(dao.inserted.getDefinitionJson().contains("freeze_data_snapshot"));
        assertTrue(dao.inserted.getDefinitionJson().contains("run_profit_forecast"));
    }

    @Test
    @DisplayName("实例化内置模板：空名称回落到意图展示名")
    void instantiateBuiltinFallsBackToDisplayName() {
        CapturingPipelineDao dao = new CapturingPipelineDao();
        AipPipelineTemplateService service = new AipPipelineTemplateService(
                new FixedMissionDao(List.of()),
                dao, new PlaybookRenderer(), new PlaybookToGraphConverter(), providerOf(null),
                new BuiltinPipelineTemplateCatalog());

        service.instantiate("t1", "u1", BuiltinPipelineTemplateCatalog.REF_PREFIX + "DIAGNOSE", "   ", Map.of());

        assertEquals("利润诊断模板", dao.inserted.getName());
    }

    @Test
    @DisplayName("实例化内置模板：ref 的意图段越界 ⇒ 404（不猜别的意图）")
    void instantiateUnknownBuiltinIntentFails404() {
        AipPipelineTemplateService service = new AipPipelineTemplateService(
                new FixedMissionDao(List.of()), new CapturingPipelineDao(),
                new PlaybookRenderer(), new PlaybookToGraphConverter(), providerOf(null),
                new BuiltinPipelineTemplateCatalog());

        assertThrows(AipPipelineTemplateService.TemplateNotFoundException.class,
                () -> service.instantiate("t1", "u1",
                        BuiltinPipelineTemplateCatalog.REF_PREFIX + "NOT_AN_INTENT", "x", Map.of()));
    }

    @Test
    @DisplayName("实例化：正常路径落库 + 转换报告如实上报 sub_run 未映射")
    void instantiatePersistsAndReportsUnmapped() {
        CapturingPipelineDao dao = new CapturingPipelineDao();
        PlaybookContentSource source = (tenantId, ref, version) -> Optional.of(playbookJson());
        AipPipelineTemplateService service = new AipPipelineTemplateService(
                new FixedMissionDao(List.of(mission("m1", "repo/pb1", "v1"))),
                dao, new PlaybookRenderer(), new PlaybookToGraphConverter(), providerOf(source),
                new BuiltinPipelineTemplateCatalog());

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
                new CapturingPipelineDao(), new PlaybookRenderer(), new PlaybookToGraphConverter(), providerOf(null),
                new BuiltinPipelineTemplateCatalog());

        AipPipelineTemplateService.TemplateNotFoundException e = assertThrows(
                AipPipelineTemplateService.TemplateNotFoundException.class,
                () -> service.instantiate("t1", "u1", "repo/missing", "x", Map.of()));
        assertTrue(e.getMessage().contains("模板不存在"));
    }
}
