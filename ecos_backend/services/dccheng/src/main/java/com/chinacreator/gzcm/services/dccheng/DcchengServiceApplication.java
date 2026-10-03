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
 * ECOS DCCheng 微服务 — 知识服务（水·K 引擎；木·C 为 P2-4/P5 目标态，尚未接入本扫描）
 *
 * <p><b>当前实装</b>：本 service 实际封装 kb-engine（KG/RAG/规则CRUD/知识抽取），承担
 * zhi (I→K) 转化。下方 {@code @ComponentScan} 仅扫描
 * {@code com.chinacreator.gzcm.engine.kb}，<b>不含 cognitive-engine</b>。
 *
 * <p><b>P2-4 目标态（未落地）</b>：目标是同时封装 kb-engine 与 cognitive-engine（因果推理/情景推演），
 * 承担 zhi (I→K) + cheng (K→C) 双转化。接入 cognitive 时须：
 * <ul>
 *   <li>在本类 {@code @ComponentScan(basePackages)} 增补 {@code com.chinacreator.gzcm.engine.cognitive}</li>
 *   <li>在 {@code @MapperScan} 增补 cognitive mapper 包</li>
 *   <li>完成 cognitive ScenarioController 的 @ComponentScan.Filter 排除 buszhi 副本验证</li>
 * </ul>
 * 未接入前，cognitive-domain 请求应走 workspace 侧编排或认知引擎独立 boot（cognitive-engine-boot:18089），
 * 不得经本 service 的 kb 端口 (18086) 承接 —— 详见 F.1 台账 C72/K-3（dccheng 双引擎矛盾）与分册 07。
 *
 * <p>与 kb-engine-boot / cognitive-engine-boot 端口互斥：
 * 本 service 沿用 kb 引擎主端口 18086, 与二者本机互斥运行 (铁律 §0.3)。
 *
 * @author ecos-factory
 * @since PMO-49 P2-4 【2026-09-30 校订：原注"双引擎单 JVM"与 @ComponentScan 不含 cognitive 矛盾，已按现状订正（P-7）】
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
