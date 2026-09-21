package com.chinacreator.gzcm.services.dccheng;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ECOS DCCheng 微服务 — 知识服务（水·K + 木·C 双引擎）
 *
 * <p>P2-4 目标态：同时封装 kb-engine（KG/RAG/规则CRUD/知识抽取）与
 * cognitive-engine（因果推理/情景推演），承担 zhi (I→K) + cheng (K→C) 双转化。
 *
 * <p>双引擎单 JVM 依据：
 * <ul>
 *   <li>kb-impl 与 cognitive-impl 已直依赖 (架构债务, 非 REST), 物理上必须同 JVM 才能注入</li>
 *   <li>P1-6 PoC 已验证单 JVM 双扫描无 Bean 冲突 (cognitive ScenarioController
 *       已显式 @ComponentScan.Filter 排除 buszhi 副本)</li>
 *   <li>emulate cognitive-smart 因 cognitive-impl 依赖 kb-impl, 两者必须共进程</li>
 * </ul>
 *
 * <p>与 kb-engine-boot / cognitive-engine-boot 端口互斥：
 * 本 service 沿用 kb 引擎主端口 18086, 与二者本机互斥运行 (铁律 §0.3)。
 *
 * @author ecos-factory
 * @since PMO-49 P2-4
 */
@SpringBootApplication(exclude = {
        HibernateJpaAutoConfiguration.class,
        JpaRepositoriesAutoConfiguration.class
})
@EnableAsync
@EnableScheduling
@ComponentScan(basePackages = {
        // DCCheng 服务自身 (HeaderAuthInterceptor / 服务级 config / ADR-7 认证头还原)
        "com.chinacreator.gzcm.services.dccheng",
        // 主封装引擎 — kb (水·K): KG 存储/检索/RAG/规则CRUD/知识抽取
        "com.chinacreator.gzcm.engine.kb",
        // 横切底座
        "com.chinacreator.gzcm.runtime",
        // peer 系统配置 (kb/cognitive 某 controller 有具体类 type IModel 引用)
        "com.chinacreator.gzcm.sysman"
})
@MapperScan({
        // kb-engine 主 mapper (KG / 规则 / 抽取) — 包名是 `.dao`, 不是 `.repository`
        "com.chinacreator.gzcm.engine.kb.**.dao",
        "com.chinacreator.gzcm.engine.kb.**.repository",
        "com.chinacreator.gzcm.engine.kb.**.mapper",
        // 横切底座
        "com.chinacreator.gzcm.runtime.**.mapper",
        "com.chinacreator.gzcm.runtime.**.dao",
        // runtime.llm.repository 横切底座
        "com.chinacreator.gzcm.runtime.llm.repository"
})
public class DcchengServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DcchengServiceApplication.class, args);
    }
}
