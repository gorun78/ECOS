package com.chinacreator.gzcm.engine.cognitive2.dao;

import com.chinacreator.gzcm.engine.cognitive2.model.ParetoProblemSnapshot;
import com.chinacreator.gzcm.engine.cognitive2.model.ParetoProblemSummary;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

/**
 * Pareto 多目标优化问题登记 DAO（D106；表 {@code ecos_control.ecos_pareto_problem}，V335）。
 *
 * <p><b>为什么要有这张表</b>：前端 Pareto Tab 的 {@code GET /problems}（历史列表）与
 * {@code GET /result/{problemId}}（回取某次结果）要求结果可回溯；只算不存会让「历史」永远为空，
 * 那正是本批要消灭的「假成功」形态。</p>
 *
 * <p><b>结果快照同表</b>：{@code solutions_json} 存完整前沿（{@code [{variables:{},objectives:{}}]}），
 * {@code /result/{id}} 直取即可，无需二次计算——同一 problemId 的两次读取必须给出同一前沿，
 * 否则用户会看到「同一次优化两次结果不同」的诡异现象。</p>
 *
 * <p>铁律合规：显式列名（0 个 {@code SELECT *}）、schema 显式限定、主键应用侧生成 UUID（MC01/MC02）。</p>
 */
@Repository
public class ParetoProblemDao extends JdbcTemplateDaoSupport {

    /** 落库一次优化（含结果快照）。 */
    public void insert(String id, String problemName, int numObjectives, int numVariables,
                       int populationSize, int generations, int frontSize,
                       String solutionsJson, long elapsedMs) {
        jdbcTemplate.update(
            "INSERT INTO ecos_control.ecos_pareto_problem "
            + "(id, problem_name, num_objectives, num_variables, population_size, generations, "
            + "front_size, solutions_json, elapsed_ms) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            id, problemName, numObjectives, numVariables, populationSize, generations,
            frontSize, solutionsJson, elapsedMs);
    }

    /** 历史列表（按提交时间倒序）。 */
    public List<ParetoProblemSummary> listProblems(int limit) {
        return jdbcTemplate.query(
            "SELECT id, problem_name, front_size, created_at "
            + "FROM ecos_control.ecos_pareto_problem ORDER BY created_at DESC LIMIT ?",
            (rs, rowNum) -> {
                Timestamp ts = rs.getTimestamp("created_at");
                return new ParetoProblemSummary(
                        rs.getString("id"),
                        rs.getString("problem_name"),
                        rs.getInt("front_size"),
                        ts == null ? null : ts.toLocalDateTime().toString());
            },
            limit);
    }

    /** 单次结果（含快照）。不存在返回 null。 */
    public ParetoProblemSnapshot findById(String id) {
        List<ParetoProblemSnapshot> rows = jdbcTemplate.query(
            "SELECT id, solutions_json, front_size, elapsed_ms "
            + "FROM ecos_control.ecos_pareto_problem WHERE id = ?",
            (rs, rowNum) -> new ParetoProblemSnapshot(
                    rs.getString("id"),
                    rs.getString("solutions_json"),
                    rs.getInt("front_size"),
                    rs.getLong("elapsed_ms")),
            id);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
