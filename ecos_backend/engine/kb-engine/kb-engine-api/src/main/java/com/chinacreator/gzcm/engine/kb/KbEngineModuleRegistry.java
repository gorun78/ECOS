package com.chinacreator.gzcm.engine.kb;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 知识引擎模块注册表（F10 后端 Controller 分组文档化）。
 * <p>
 * 定义 7 个前端页面模块（21 Controller 按业务域归类），
 * 供 {@code GET /api/v1/knowledge/nav/modules} 端点读取常量表返回 {@code NavModulesVO}。
 * <p>
 * 数据源：纯常量（不走 DB / Neo4j / pgvector），响应 ≤ 5ms。
 *
 * @since PMO-D 2026-09-24
 */
public final class KbEngineModuleRegistry {

    /** 单个模块条目（module 枚举 + controllers 列表） */
    public record ModuleEntry(Module module, List<String> controllers) {
    }

    private KbEngineModuleRegistry() {
    }

    /**
     * 7 个模块枚举，与前端 6 页面 + 1 P3 知识中心对应。
     */
    public enum Module {
        /** 知识总览 */
        OVERVIEW,
        /** 知识资产 */
        ASSETS,
        /** 知识抽取 */
        EXTRACT,
        /** 知识图谱 */
        GRAPH,
        /** 企业知识（Wiki） */
        WIKI,
        /** 知识治理 */
        GOVERN,
        /** 知识中心（P3 未激活，保留路由） */
        CENTER_P3
    }

    /**
     * 模块条目列表（不可变，按枚举声明顺序排列）。
     */
    public static final List<ModuleEntry> MODULES;

    static {
        List<ModuleEntry> list = new ArrayList<>();
        list.add(new ModuleEntry(Module.OVERVIEW, List.of(
                "KbEngineHealthController",
                "KnowledgeApiController",
                "KnowledgeListController"
        )));
        list.add(new ModuleEntry(Module.ASSETS, List.of(
                "NavTaxonController",
                "KnowledgeArticleController",
                "OntologyTreeController"
        )));
        list.add(new ModuleEntry(Module.EXTRACT, List.of(
                "ExtractionController",
                "StructuredExtractController",
                "ScheduledExtractController",
                "KnowledgeIngestController"
        )));
        list.add(new ModuleEntry(Module.GRAPH, List.of(
                "KnowledgeGraphController",
                "GraphSyncController",
                "GraphQueryPostController",
                "EcosKnowledgeGraphController"
        )));
        list.add(new ModuleEntry(Module.WIKI, List.of(
                "EntityLinkController",
                "KnowledgeListController"
        )));
        list.add(new ModuleEntry(Module.GOVERN, List.of(
                "KnowledgeLifecycleController",
                "ExpertRuleController",
                "ComplianceRuleController",
                "KnowledgeEvalController"
        )));
        list.add(new ModuleEntry(Module.CENTER_P3, List.of(
                "RagController"
        )));
        MODULES = List.copyOf(list);
    }

    /**
     * 根据 Controller 短名（无包名）归类到所属 Module。
     *
     * @param shortcut Controller 类的 simple name（如 {@code "RagController"}）
     * @return 所属 Module；未注册时返回 {@code null}
     */
    public static Module classify(String shortcut) {
        if (shortcut == null) {
            return null;
        }
        for (ModuleEntry entry : MODULES) {
            if (entry.controllers().contains(shortcut)) {
                return entry.module();
            }
        }
        return null;
    }
}
