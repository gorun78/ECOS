package com.chinacreator.gzcm.sysman.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;

import com.chinacreator.gzcm.services.sysman.SysmanServiceApplication;

/**
 * B32 sysman MapperScan 装订契约门（P1-15 修复腿 2 的防回潮断言）。
 *
 * <p>实证背景：sysman 域 dao 包下全部是「纯接口 + Impl(ISystemDatabaseAccess/
 * iBATIS sqlMap)」形态（无任何 MyBatis mapper）。旧 @MapperScan 扫了
 * sysman 通配 dao 包，把纯接口误注册成 mapper 代理；注入按参数名优先命中代理
 * （如 {@code organizationDao}）而非 {@code @Repository OrganizationDaoImpl} ⇒
 * 一调用即 {@code Invalid bound statement}（决策链主体域解析 503、
 * {@code GET /system/organizations/all} code=-1 两处 live 实证）。gateway 同口径只扫
 * {@code .mapper}，所以同一代码在 gateway 宿主正常。</p>
 *
 * <p>判据：@MapperScan 值不得含 {@code sysman.**.dao} / {@code sysman.**.mapper}
 * （本域无 mapper；误扫即回到代理注入陷阱），且必须保留 security-engine 与 runtime 条目。</p>
 */
class SysmanMapperScanContractTest {

    private static List<String> mapperScanPackages() throws Exception {
        MapperScan scan = SysmanServiceApplication.class.getAnnotation(MapperScan.class);
        assertNotNull(scan, "SysmanServiceApplication 缺 @MapperScan");
        return Arrays.asList(scan.value());
    }

    @Test
    @DisplayName("@MapperScan 不扫 sysman.**.dao / sysman.**.mapper（纯接口误注册防御）")
    void doesNotScanSysmanDaoOrMapper() throws Exception {
        List<String> pkgs = mapperScanPackages();
        assertFalse(pkgs.contains("com.chinacreator.gzcm.sysman.**.dao"),
                "sysman.**.dao 全是 ISystemDatabaseAccess 形态纯接口，误扫即 mapper 代理注入陷阱");
        assertFalse(pkgs.contains("com.chinacreator.gzcm.sysman.**.mapper"),
                "sysman 域不存在 mapper 包（全量核查），该条目为死配置");
    }

    @Test
    @DisplayName("@MapperScan 保留 security-engine 与 runtime 横切条目（启动依赖）")
    void keepsSecurityAndRuntimeEntries() throws Exception {
        List<String> pkgs = mapperScanPackages();
        assertTrue(pkgs.contains("com.chinacreator.gzcm.engine.security.**.dao"),
                "security-engine dao 是真 mapper，缺失即 RLS/ABAC 注入失败");
        assertTrue(pkgs.contains("com.chinacreator.gzcm.runtime.llm.repository"),
                "runtime.llm.repository 缺失即 AgentMetricsImpl 构造注入失败（启动失败）");
    }

    @Test
    @DisplayName("ComponentScan 必须排除 runtime-core MyBatisConfig（全仓 **.dao 通配误注册防御）")
    void excludesRuntimeMyBatisConfig() {
        ComponentScan scan = SysmanServiceApplication.class.getAnnotation(ComponentScan.class);
        assertNotNull(scan, "SysmanServiceApplication 缺 @ComponentScan");
        boolean excluded = Arrays.stream(scan.excludeFilters())
                .anyMatch(f -> Arrays.asList(f.value()).contains(
                        "com.chinacreator.gzcm.runtime.core.mybatis.config.MyBatisConfig")
                        || Arrays.stream(f.classes())
                                .anyMatch(c -> c.getName().equals(
                                        "com.chinacreator.gzcm.runtime.core.mybatis.config.MyBatisConfig")));
        assertTrue(excluded,
                "MyBatisConfig 的 MapperScannerConfigurer 扫 com.chinacreator.gzcm.**.dao（全仓通配），"
                        + "未排除即把 sysman 纯接口误注册成 mapper 代理（gateway 同口径已排除）");
    }
}
