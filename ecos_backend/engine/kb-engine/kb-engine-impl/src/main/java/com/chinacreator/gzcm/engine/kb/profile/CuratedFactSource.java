package com.chinacreator.gzcm.engine.kb.profile;

import java.math.BigDecimal;
import java.util.List;

/**
 * CURATED 事实取数抽象（F04-06 / PRD-04 §2.3-2，B-1）。
 * <p>
 * 取数只读 CURATED 层 {@code dq_status=PUBLISHED} 的 ACTUAL 事实；<b>禁读 RAW</b>（B-1）。
 * 通过"数据源可注入 + 默认实现查 ecos_dw curated 事实表 + 查询标识可复现"留缝：
 * <ul>
 *   <li>生产/联调：{@link JdbcCuratedFactSource}（经 runtime-access 只读 JdbcTemplate 查 ecos_dw）；</li>
 *   <li>切换 datanet REST 供数（C.3 S-1）：再实现本接口即可，Service 层无感；</li>
 *   <li>单测：直接 mock 本接口，纯算法路径可验（无 @SpringBootTest）。</li>
 * </ul>
 *
 * @author ECOS KB Team
 */
public interface CuratedFactSource {

    /**
     * 拉取某指标在窗口内、可复现标识下的 CURATED 事实值流。
     *
     * @param metricCode     指标编码
     * @param windowFrom     YYYY-MM
     * @param windowTo       YYYY-MM
     * @param sourceQueryRef 查询标识（可复现：含数据源/过滤条件摘要），落画像 source_query_ref
     * @return 事实值列表（value 可为 null 表示缺失样本，计入 missingRate）
     */
    List<CuratedFactRow> fetch(String metricCode, String windowFrom, String windowTo, String sourceQueryRef);

    /** 单条 CURATED 事实样本（四轴维度 + 指标值）。 */
    final class CuratedFactRow {
        /** 指标值（nullable → missing）。 */
        private BigDecimal value;
        /** 项目（最细粒度，degrade 链最内层，可空）。 */
        private String project;
        /** 项目类型（可空）。 */
        private String projectType;
        /** 部门（可空）。 */
        private String department;
        /** 环节（degrade 链最外层，保留）。 */
        private String stage;

        public CuratedFactRow() {
        }

        public CuratedFactRow(BigDecimal value, String project, String projectType,
                              String department, String stage) {
            this.value = value;
            this.project = project;
            this.projectType = projectType;
            this.department = department;
            this.stage = stage;
        }

        public BigDecimal getValue() {
            return value;
        }

        public void setValue(BigDecimal value) {
            this.value = value;
        }

        public String getProject() {
            return project;
        }

        public void setProject(String project) {
            this.project = project;
        }

        public String getProjectType() {
            return projectType;
        }

        public void setProjectType(String projectType) {
            this.projectType = projectType;
        }

        public String getDepartment() {
            return department;
        }

        public void setDepartment(String department) {
            this.department = department;
        }

        public String getStage() {
            return stage;
        }

        public void setStage(String stage) {
            this.stage = stage;
        }
    }
}
