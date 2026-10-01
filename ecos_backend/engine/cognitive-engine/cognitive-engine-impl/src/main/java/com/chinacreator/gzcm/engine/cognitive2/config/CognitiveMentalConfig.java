package com.chinacreator.gzcm.engine.cognitive2.config;

import com.chinacreator.gzcm.runtime.core.monitor.interfaces.IWarnLogService;
import com.chinacreator.gzcm.runtime.core.monitor.service.impl.WarnLogServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * PMO-59 P2b 心智层装配配置 — 仅补"公共底座缺口"，不新建引擎自有基建（铁律 §2.5 补强而非自建）。
 *
 * <p>注册项：{@link IWarnLogService} — runtime-monitor 既有实现 {@code WarnLogServiceImpl} 原本无
 * Spring 装配（runtime 缺口），按 PMO-59 指令 §禁止清单 7 授权例外补 Bean，使
 * {@code CognitiveHypothesisService} 失效告警可达 runtime-monitor（铁律 §2.5 监控收敛）。
 * 内存态实现（warn 正文含 faultContext 结构化字段，周一故障复盘可经 findByPage 查询），
 * 落库/通知通道 Phase 3+ 按 runtime-monitor 演进补齐。</p>
 *
 * <p>Kafka producer 相关 Bean（{@code DefaultKafkaProducerFactory<String,String>} +
 * {@code KafkaTemplate<String,String>}）已由 PMO-74 H2-T4 迁至 runtime-event 底座
 * （{@code KafkaBusProducerConfig}），本类不再持有 spring-kafka 依赖（铁律 §2.5：事件通道
 * 单一收敛于 runtime-event）。</p>
 */
@Configuration
public class CognitiveMentalConfig {

    private static final Logger log = LoggerFactory.getLogger(CognitiveMentalConfig.class);

    /**
     * 告警日志服务（runtime-monitor 公共底座；补强既有 impl 的 Spring 装配缺口）。
     *
     * <p>PMO-59 P4a：装配时一并注入 {@link JdbcTemplate}，使告警落库 {@code ecos_warn_log}（V131），
     * 关闭 P2b「告警内存态，进程重启即失」残留风险；JdbcTemplate 缺失时退化为纯内存态（仅 WARN，不阻断启动）。</p>
     *
     * <p><b>架构铁律 §2.5 / IR06 收口豁免（R2 残余复扫定性）</b>：本类是 {@code @Configuration}
     * Bean 工厂，注入的 JdbcTemplate 仅作唯一回传路径交给公共底座实现
     * {@code WarnLogServiceImpl}（其自有语句在 runtime-monitor 底座层执行）；
     * 本类自身<b>不执行任何业务 SQL</b>，属"框架装配模板"而非业务数据访问，
     * 故不为其强制建 DAO（过度收口反模式），也不扩大 DataAccessArchTest
     * 的守门范围（守门类名不含 {@code Config}，包范围不含 {@code ..config..}）。</p>
     */
    @Bean
    @ConditionalOnMissingBean(IWarnLogService.class)
    public IWarnLogService cognitiveWarnLogService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            log.warn("[CognitiveMental] IWarnLogService 已装配，但 JdbcTemplate 缺失 → 告警仅内存态（不落库）");
        } else {
            log.info("[CognitiveMental] IWarnLogService 已装配 + 告警落库通道启用 (ecos_warn_log, PMO-59 P4a)");
        }
        return new WarnLogServiceImpl(jdbcTemplate);
    }
}
