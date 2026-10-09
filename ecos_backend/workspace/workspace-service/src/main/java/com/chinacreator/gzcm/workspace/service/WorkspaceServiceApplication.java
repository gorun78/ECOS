package com.chinacreator.gzcm.workspace.service;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ECOS Workspace Service — 场景应用层独立部署单元 (PMO-49 P2-6)
 *
 * <p>承担方案 v1.4 定位：workspace 是"综合的场景应用层"，调用全部业务 service
 * (sysman/datanet/buszhi/dccheng/aiming)，独立端口 {@code 18090}。
 *
 * <p>扫描 (workspace-impl 库已就绪, 这是 boot 层入口):
 * <ul>
 *   <li>{@code com.chinacreator.gzcm.workspace} — 场景 controller/service/workbook/security</li>
 *   <li>{@code com.chinacreator.gzcm.buszhi} (workspace-impl 依赖 buszhi-impl 的 ObjectRuntimeService
 *       + StateMachineEngine, classpath 需要) </li>
 *   <li>{@code com.chinacreator.gzcm.runtime} (横切底座)</li>
 *   <li>{@code com.chinacreator.gzcm.runtime.llm} (横切底座)</li>
 * </ul>
 *
 * <p>exclude 隔离:
 * <ul>
 *   <li>gateway 侧 WorkspaceApplication (不存在 —— gateway 无专属 workspace controller)</li>
 *   <li>各 service 独立启动器 (自身 @ComponentScan 重复索引), 保持走了 mapper 扫描但隔离主入口</li>
 * </ul>
 *
 * <p>ADR-7 HeaderAuthInterceptor 从 gateway X-ECOS-* 头恢复用户上下文 (Phase 3 落地).
 *
 * @author ecos-factory
 * @since PMO-49 P2-6
 */
@SpringBootApplication(exclude = {
        HibernateJpaAutoConfiguration.class,
        JpaRepositoriesAutoConfiguration.class
})
@EnableAsync
@EnableScheduling
@ComponentScan(basePackages = {
        // workspace 场景层自身 (controller / service / workbook / security / QueryHistory)
        "com.chinacreator.gzcm.workspace",
        // buszhi (workspace-impl 依赖 buszhi.ObjectRuntimeService 等, 同 jar 部署)
        "com.chinacreator.gzcm.buszhi",
        // common-api 契约模块 — 共享 plumbing bean (ObjectRuntimeService/StateMachineEngine 等)
        // AGENTS.md: 全仓 0 业务 import, enforcer 守门, 所有 @Service/@Component 都设计为共享 bean
        "com.chinacreator.gzcm.common",
        // ontology-engine (workspace 依赖计算属性引擎 ActionHookExecutor)
        "com.chinacreator.gzcm.engine.ontology",
        // sysman.config.service — CognitiveConfig/OntologyConfig 等 controller 依赖的
        // SysConfigService（具体类），只扫 config 子包避开 sysman 主体 security 域
        "com.chinacreator.gzcm.sysman.config",
        // engine.data.service — CopilotServiceImpl (ICopilotService impl)
        // 供 OntologyCopilotController 注入; 只扫 service 子包避开 data 域 controller
        "com.chinacreator.gzcm.engine.data.service",
        // engine.data.repository — DataSourceRepository 等 dao (workspace 之外的 data 引擎库
        // 里的 @Repository 注入, 需显式才进 component scan)
        "com.chinacreator.gzcm.engine.data.repository",
        // 横切底座
        "com.chinacreator.gzcm.runtime",
        "com.chinacreator.gzcm.runtime.llm"
}, excludeFilters = {
        @ComponentScan.Filter(type = FilterType.REGEX,
                pattern = "com\\.chinacreator\\.gzcm\\.gateway\\..*"),
        // buszhi 侧 WorkflowController 与 engine.ontology 侧同名 bean 冲突
        // (gateway 已镜像排除 buszhi 版本, 保留 engine.ontology 版本)。
        // 卷07 F07-16：原写法是 FilterType.ASSIGNABLE_TYPE + WorkflowController.class
        // 的编译期引用 —— 那会让本 boot 类**必须**编译期看见 buszhi-impl，
        // 三层纪律（workspace 不得依赖 *-impl）就永远拆不掉。改 REGEX 后
        // 排除效果不变，buszhi-impl 退化为纯运行期装配事实。
        @ComponentScan.Filter(type = FilterType.REGEX,
                pattern = "com\\.chinacreator\\.gzcm\\.buszhi\\.workflow\\.controller\\.WorkflowController")
})
@MapperScan({
        // 卷07 F07-01/X-7：原三条 workspace 通配（**.repository / **.dao / **.mapper）
        // 全部指向**不存在**的包 —— workspace 目前 0 个 MyBatis Mapper 接口
        // （数据访问走 dao.WorkspaceDataDao，是 JdbcTemplate DAO 不是 mapper）。
        // 通配 @MapperScan 扫空包不会报错，但把"没有 mapper"伪装成"已收口"，
        // 故收成一个显式包名：新增 mapper 必须落在这里才被子产品扫描到。
        "com.chinacreator.gzcm.workspace.mapper",
        // buszhi (ObjectRuntime/State Machine 库)
        "com.chinacreator.gzcm.buszhi.**.repository",
        "com.chinacreator.gzcm.buszhi.**.dao",
        "com.chinacreator.gzcm.buszhi.**.mapper",
        // F06-20⑥ (X-8)：llm-gateway 横切底座 @Mapper 接口 (AgentCallLogRepository / ProfileConfigRepository)，
        // 落在 .repository 后缀而非 .mapper/.dao，与 "runtime.**.mapper" / "runtime.**.dao" 通配不重叠，必须显式列出
        "com.chinacreator.gzcm.runtime.llm.repository",
        // runtime 横切底座
        "com.chinacreator.gzcm.runtime.**.mapper",
        "com.chinacreator.gzcm.runtime.**.dao",
        // engine.data.@Mapper 接口 (DataSourceRepository / ProbeRecordRepository 等),
        // CopilotServiceImpl (engine.data.service) 构造器 @Autowired DataSourceRepository
        "com.chinacreator.gzcm.engine.data.**.repository",
        "com.chinacreator.gzcm.engine.data.**.mapper"
})
public class WorkspaceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkspaceServiceApplication.class, args);
    }
}
