package com.chinacreator.gzcm.sysman.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication(exclude = {
    org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class,
    org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration.class
})
@ComponentScan(basePackages = {
    "com.chinacreator.gzcm.sysman",
    "com.chinacreator.gzcm.runtime",
    "com.chinacreator.gzcm.common",
    "com.chinacreator.gzcm.buszhi",
    "com.chinacreator.gzcm.market",
    "com.chinacreator.gzcm.worldmodel",
    "com.chinacreator.gzcm.workspace",
    "com.chinacreator.gzcm.portal",
}, excludeFilters = {
    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {com.chinacreator.gzcm.runtime.core.mybatis.config.MyBatisConfig.class})
})
@MapperScan({
    "com.chinacreator.gzcm.sysman.**.mapper",
    "com.chinacreator.gzcm.runtime.**.mapper",
    // F06-20⑥ (X-8)：llm-gateway 横切底座 @Mapper 接口 (AgentCallLogRepository / ProfileConfigRepository)，
    // 落在 .repository 后缀而非 .mapper/.dao，与 "runtime.**.mapper" 通配不重叠，必须显式列出
    "com.chinacreator.gzcm.runtime.llm.repository",
    "com.chinacreator.gzcm.engine.ai.agent.mesh.repository",
    "com.chinacreator.gzcm.engine.ai.agent.mesh.knowledge.repository"
})
public class SysManApplication {
    public static void main(String[] args) {
        SpringApplication.run(SysManApplication.class, args);
    }
}
