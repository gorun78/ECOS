package com.chinacreator.gzcm.services.datanet;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ECOS Datanet 微服务 — 数据服务（土·D）
 *
 * <p>P2-2 目标态：封装 data-engine（数据源/管道/血缘/DQ）+ ge 转化（D→I），
 * 替代原 gateway fat-JAR 中对 {@code com.chinacreator.gzcm.engine.data.*} 的聚合扫描。
 *
 * <p>拆分期双跑策略：
 * <ul>
 *   <li>gateway 保留对 data-engine 的组件扫描（老流量继续走 :8080）</li>
 *   <li>本 service 独立 :18082 承接 data-engine 全量路由</li>
 *   <li>切流由 gateway 路由开关 ECOS_ROUTE_datanet 控制，最终 gateway 清理 basePackages 完成</li>
 * </ul>
 *
 * <p>与 {@link com.chinacreator.gzcm.engine.data.boot.DataEngineApplication} 端口冲突处理：
 * 二者均默认 :18082，本机互斥运行（铁律 §0.3 引擎 boot 非生产入口）。
 *
 * @author ecos-factory
 * @since PMO-49 P2-2
 */
@SpringBootApplication(exclude = {
        HibernateJpaAutoConfiguration.class,
        JpaRepositoriesAutoConfiguration.class
})
@EnableAsync
@EnableScheduling
@ComponentScan(basePackages = {
        // Datanet 服务自身 (HeaderAuthInterceptor / 服务级 config / ADR-7 认证头还原)
        "com.chinacreator.gzcm.services.datanet",
        // 主封装引擎 — data-engine (土·D)
        "com.chinacreator.gzcm.engine.data",
        // 横切底座 (runtime-core 通用 mybatis/mapper/Jackson / runtime-access Driver 封装 / runtime-task 全局调度)
        "com.chinacreator.gzcm.runtime",
        // peer 系统配置 (DataEngineConfigController 直接注入 SysConfigService concrete 类；
        // 拆分期允许与 sysman service 双进程共存该 bean — 各自独立 Jvm 写不同 schema)
        "com.chinacreator.gzcm.sysman"
})
@MapperScan({
        // data-engine 主 mapper (repository / quality / datasource / pipeline)
        "com.chinacreator.gzcm.engine.data.repository",
        // PMO-48-A T5: DqRuleMapper 与质量包下 mapper
        "com.chinacreator.gzcm.engine.data.quality.mapper",
        "com.chinacreator.gzcm.engine.data.quality.**.mapper",
        // data-engine 内部 dao 兼容写法
        "com.chinacreator.gzcm.engine.data.**.dao",
        "com.chinacreator.gzcm.engine.data.**.mapper",
        // 横切底座
        "com.chinacreator.gzcm.runtime.**.mapper",
        "com.chinacreator.gzcm.runtime.**.dao"
})
public class DatanetServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DatanetServiceApplication.class, args);
    }
}
