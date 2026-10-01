package com.chinacreator.gzcm.engine.data.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * Pipeline Git 版本管理数据访问（仅收编 SystemDB 直连读写字面值操作：
 * git 元数据、定义 JSON、node/edge 归档回写、task upsert/切分支）。
 *
 * <p>架构铁律 §2.5#1 / IR06：Service 不持有 JdbcTemplate，一律经 runtime 数据访问基类落库。</p>
 */
@Repository
public class PipelineGitDao extends JdbcTemplateDaoSupport {

    // ── git 元数据 / 定义读取 ──

    /** 存量 task 行 git 元数据（doCommit 读源）。缺行时抛异常由调用方吞掉。 */
    public Map<String, Object> gitMetadata(String taskId) {
        return jdbcTemplate.queryForMap(
                "SELECT git_url, git_branch FROM ecos_pipeline_task WHERE id = ?", taskId);
    }

    /** doPull 读定义行（取 name）。 */
    public Map<String, Object> definition(String definitionId) {
        return jdbcTemplate.queryForMap(
                "SELECT id, name FROM ecos_pipeline_definition WHERE id = ?", definitionId);
    }

    // ── 定义 JSON + node/edge 归档（F02-03，全量替换） ──

    public void updateDefinition(String canonical, String definitionId) {
        jdbcTemplate.update(
                "UPDATE ecos_pipeline_definition SET definition_json = ?, definition = ?::jsonb, "
                        + "updated_at = NOW() WHERE id = ?",
                canonical, canonical, definitionId);
    }

    public void deleteNodes(String definitionId) {
        jdbcTemplate.update("DELETE FROM ecos_pipeline_node WHERE definition_id = ?", definitionId);
    }

    public void deleteEdges(String definitionId) {
        jdbcTemplate.update("DELETE FROM ecos_pipeline_edge WHERE definition_id = ?", definitionId);
    }

    public void insertNode(String id, String definitionId, String nodeId, String type,
                           String config, String dependsOn) {
        jdbcTemplate.update(
                "INSERT INTO ecos_pipeline_node (id, definition_id, node_id, type, config, depends_on, "
                        + "position_x, position_y, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, 0, 0, NOW(), NOW())",
                id, definitionId, nodeId, type, config, dependsOn);
    }

    public void insertEdge(String id, String definitionId, String from, String to) {
        jdbcTemplate.update(
                "INSERT INTO ecos_pipeline_edge (id, definition_id, from_node_id, to_node_id, created_at) "
                        + "VALUES (?, ?, ?, ?, NOW())",
                id, definitionId, from, to);
    }

    // ── 归档读源（loadDefinitionJson） ──

    public List<Map<String, Object>> nodes(String definitionId) {
        return jdbcTemplate.queryForList(
                "SELECT node_id, type, config, depends_on FROM ecos_pipeline_node "
                        + "WHERE definition_id = ? ORDER BY created_at", definitionId);
    }

    public List<Map<String, Object>> edges(String definitionId) {
        return jdbcTemplate.queryForList(
                "SELECT from_node_id, to_node_id FROM ecos_pipeline_edge "
                        + "WHERE definition_id = ? ORDER BY created_at", definitionId);
    }

    // ── task upsert / 切分支 ──

    public void upsertTaskFromGit(String id, String name, String description, String yamlContent,
                                  Object gitUrl, String gitBranch) {
        jdbcTemplate.update(
                "INSERT INTO ecos_pipeline_task (id, name, description, yaml_content, git_url, git_branch, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'DRAFT') ON CONFLICT (id) DO UPDATE SET yaml_content = EXCLUDED.yaml_content, updated_at = NOW()",
                id, name, description, yamlContent, gitUrl, gitBranch);
    }

    public void switchTaskBranch(String branch, String taskId) {
        jdbcTemplate.update(
                "UPDATE ecos_pipeline_task SET git_branch = ?, updated_at = NOW() WHERE id = ?",
                branch, taskId);
    }
}
