package com.chinacreator.gzcm.services.buszhi;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ECOS Buszhi 微服务 — 本体服务（金·I）
 *
 * <p>P2-3 目标态：封装 ontology-engine（本体建模/对象/关系/版本）+ 原顶层 buszhi（工作流引擎 + 对象管理）。
 *
 * <p>Phase 2 现状：**代码物理搬迁待做**
 * <ul>
 *   <li>原 buszhi/ 顶层目录 → services/buszhi/src (保留 package 名 com.chinacreator.gzcm.buszhi)**
 *   <li>gateway 的 @ComponentScan 移 basePackages 中 com.chinacreator.gzcm.buszhi + 相应 excludeFilters
 *   <li>VersionPrefixRewriteFilter 需为精确前缀路由 (buszhi /api/v1/ecos/object/* vs workspace /api/v1/ecos/objects/*)
 * </ul>
 *
 * @author ecos-factory
 * @since PMO-49 P2-3 (骨架交付, 代码干 Phase 3 执行)
 */
@SpringBootApplication(exclude = {
        HibernateJpaAutoConfiguration.class,
        JpaRepositoriesAutoConfiguration.class
})
@EnableAsync
@EnableScheduling
@ComponentScan(basePackages = {
        // Buszhi 服务自身 (HeaderAuthInterceptor / 服务级 config / ADR-7)
        "com.chinacreator.gzcm.services.buszhi",
        // 主封装引擎 — ontology-engine (金·I): 本体建模/对象/关系/版本
        "com.chinacreator.gzcm.engine.ontology",
        // 原 buszhi 包 — 物理搬迁后 registry 到本 service 下
        "com.chinacreator.gzcm.buszhi",
        // 横切底座
        "com.chinacreator.gzcm.runtime"
})
@MapperScan({
        "com.chinacreator.gzcm.engine.ontology.**.dao",
        "com.chinacreator.gzcm.engine.ontology.**.mapper",
        "com.chinacreator.gzcm.engine.ontology.**.repository",
        "com.chinacreator.gzcm.buszhi.**.dao",
        "com.chinacreator.gzcm.buszhi.**.mapper",
        "com.chinacreator.gzcm.buszhi.**.repository",
        "com.chinacreator.gzcm.runtime.**.mapper",
        "com.chinacreator.gzcm.runtime.**.dao"
})
public class BuszhiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BuszhiServiceApplication.class, args);
    }
}
