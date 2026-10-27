package com.chinacreator.gzcm.services.agent.dao;

import org.springframework.stereotype.Repository;

/**
 * agent 工具面 {@code JdbcTemplate} 数据访问面 —— <b>只读面已全量下线</b>
 * （#146j/B4-3e-91 收口 <b>D-B4-49 余项</b> 只读直连子项，本类正文只读方法清零）。
 *
 * <p><b>为什么留着空壳而不是删文件</b>：{@code AgentToolOwnerWriteChannelSingleSourceTest}
 * 的 {@code toolDaoSource()} 断言本文件存在——该门对「agent 工具面进程内写腿」做的是
 * 供扇判决：file 缺失即意味着判据错位（无法证明「本 DAO 里零 DML」），须保
 * 证该锚点在场。所有只读方法已由各工具腿改指属主 REST 端点（#146j/B4-3e-91 实跑取证），
 * 写原语早于 #142/B4-3e-22 已全撤。</p>
 *
 * <p><b>留下的硬约束</b>：本类仍须保持空体——任何在本类内新增的
 * {@code jdbcTemplate.update(}/{@code INSERT INTO}/{@code UPDATE ... SET}/
 * {@code DELETE FROM} 都被该门判红；工具侧的新读需求须经属主端点开同批种子
 * 登记，不重立本类内联查数面（「agent 进程不持属主 SQL」是 D-B4-49 余项集体
 * 收口的终局形态）。</p>
 */
@Repository
public class AgentToolDao {

    // 只读面 8 支（ExecuteExpertRule / FindGraphPath / GetDataLineage / GetTableSchema /
    // ListDataSources / RAGQuery / SearchCatalog / SearchKnowledgeGraph）：
    // #146j/B4-3e-91 全改指属主端点，本批撤出。写原语（insertPipelineRun/insertQualityCheck）
    // 早由 #142/B4-3e-22 撤除。本类不再有可移除内容，保留类壳与 @Repository 注解
    // 就是「agent 工具面进程内 SQL 面已关账」的可读证据。
}
