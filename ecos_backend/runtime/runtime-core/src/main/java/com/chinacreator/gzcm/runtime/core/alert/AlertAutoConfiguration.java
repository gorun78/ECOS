package com.chinacreator.gzcm.runtime.core.alert;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.chinacreator.gzcm.runtime.core.alert.mapper.AlertRecordMapper;
import com.chinacreator.gzcm.runtime.core.alert.mapper.AlertRuleMapper;
import com.chinacreator.gzcm.runtime.core.alert.service.impl.AlertServiceImpl;

/**
 * 告警持久化装配（F00-09 / W07）。
 * <p>
 * <ul>
 *   <li>{@link AlertServiceImpl}：当 mapper 缺席（如 runtime-core 独立宿主、无 MyBatis 上下文）
 *       时以 null mapper 装配为纯内存模式，等价既有 {@code new AlertServiceImpl()} 行为；</li>
 *   <li>mapper 走 host 的 {@code @MapperScan("/.mapper")} 扫描（gateway 与 aiming/datanet/dccheng
 *       均已注册 {@code com.chinacreator.gzcm.runtime.**.mapper}，无需在本处重复声明）；
 *       本处仅追加显式 {@code @MapperScan} 兜底对 sysman-boot 宿主（其 MapperScan 不含 runtime
 *       包）生效 —— <b>重复声明同包幂等，MyBatis 对重复注册不报错</b>。</li>
 * </ul>
 *
 * <p>注意：runtime-core 既有 {@code MyBatisConfig} 的 MapperScannerConfigurer 扫
 * {@code com.chinacreator.gzcm.**.dao}（不含 .mapper），故此处兜底必要。
 */
@Configuration
@MapperScan(basePackages = "com.chinacreator.gzcm.runtime.core.alert.mapper")
public class AlertAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(IAlertService.class)
    public IAlertService alertService(
            org.springframework.beans.factory.ObjectProvider<AlertRuleMapper> ruleMapperProvider,
            org.springframework.beans.factory.ObjectProvider<AlertRecordMapper> recordMapperProvider) {
        AlertRuleMapper ruleMapper = ruleMapperProvider.getIfAvailable();
        AlertRecordMapper recordMapper = recordMapperProvider.getIfAvailable();
        if (ruleMapper == null && recordMapper == null) {
            // 纯内存模式（dev 态 / 无 MyBatis 上下文）：双写降级，方法语义不变
            return new AlertServiceImpl();
        }
        return new AlertServiceImpl(ruleMapper, recordMapper);
    }
}
