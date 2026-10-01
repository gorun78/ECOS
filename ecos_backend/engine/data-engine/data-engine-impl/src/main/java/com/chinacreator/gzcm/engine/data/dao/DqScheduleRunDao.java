package com.chinacreator.gzcm.engine.data.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

/**
 * B4a 数据访问收口（R2-B4a）：{@code DqScheduledTask}（..scheduler..）
 * 内联 raw-SQL（ecos_quality_rule 计数 + sys_config 任务时间维护）的 DAO 承载点。
 *
 * <p>架构铁律 §2.5#1 / IR06：Task 层禁私持 {@code JdbcTemplate}，
 * SQL 一律下沉 {@code ..dao..}；本 DAO 只搬不改，SQL 文本与
 * {@code DqScheduledTask} 原实现逐字节一致。</p>
 */
@Repository
public class DqScheduleRunDao extends JdbcTemplateDaoSupport {

    /** 数据质量定时任务的时间印章配置键（sys_config，与任务实现常量一致） */
    private static final String DQ_LAST_RUN_TIME_KEY = "dq_last_run_time";

    /** 当前启用的质量规则条数（每日扫描 / 旧链路巡检的门槛计数）。 */
    public Integer countEnabledQualityRules() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ecos_quality_rule WHERE enabled = true", Integer.class);
    }

    /**
     * 维护 Dq 定时任务"上次运行时间"印章：先 UPDATE 已有配置行，
     * 0 命中再 INSERT 新行。整段由调用方 try/catch 兜底，本方法不吞异常。
     *
     * @param now 展示时间戳（由调用方格式化后传入）
     * @return UPDATE 阶段命中行数（1=更新到既有行；0=走了 INSERT 分支）
     */
    public int updateDqLastRunTime(String now) {
        int rows = jdbcTemplate.update(
                "UPDATE sys_config SET config_value = ?, updated_at = NOW() WHERE config_key = ?",
                now, DQ_LAST_RUN_TIME_KEY);
        if (rows == 0) {
            jdbcTemplate.update(
                    "INSERT INTO sys_config (config_key, config_value, created_at, updated_at) VALUES (?, ?, NOW(), NOW())",
                    DQ_LAST_RUN_TIME_KEY, now);
        }
        return rows;
    }
}
