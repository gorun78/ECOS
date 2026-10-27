package com.chinacreator.gzcm.workspace.arch;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * D-1 场景实体双载体裁决守门（#146f / 映射台账 §D D-1）。
 *
 * <p><b>裁决（2026-10-27 工程自裁，零迁移 / 零写 dev PG / 零新 V 号）</b>：SCENARIO 实体存在
 * 两条物理载体面——</p>
 * <ol>
 *   <li><b>运行权威（活腿）</b>＝{@code dao.WorkspaceDataDao}（JdbcTemplate DAO）读写
 *       {@code ecos_business_scenario} + {@code ecos_scenario_binding}。2026-10-27 dev PG 实查
 *       {@code ecos_ai.ecos_business_scenario} = 4 行，即数据源真值单腿。</li>
 *   <li><b>规划未启用（休眠腿）</b>＝{@code mapper.ScenarioMapper}（V205
 *       {@code ecos_scenario_definition}）+ {@code mapper.ScenarioBindingMapper}（V206
 *       {@code ecos_scenario_asset_binding}）。两表 2026-10-27 实查均 0 行；两接口经
 *       {@code WorkspaceServiceApplication} 的 {@code @MapperScan("com.chinacreator.gzcm.workspace.mapper")}
 *       注册为 Bean，但<b>全仓生产零注入 / 零消费方</b>——所有非 Javadoc 引用为零，Javadoc 仅指向
 *       未建成的「批次 C 收口接缝」。故休眠腿当前<b>不承载任何生产流量</b>。</li>
 * </ol>
 *
 * <p>本守门把「休眠腿当前不承载生产流量」这一事实在构建期钉死：任何生产类（非测试）若开始依赖
 * {@code ScenarioMapper} 或 {@code ScenarioBindingMapper}（mapper 包自身除外），即等于在未完成
 * 双载体收敛裁决的前提下<b>重新接通一条 0 行规划 schema</b>，会把「活腿单腿运行」的假单一打回
 * 「双轨各写各表」的原缺陷面。故两规则立即变红，强制先回映射台账 §D D-1 裁决（收敛到
 * {@code ecos_business_scenario} 单腿，或按 V205/V206 正式迁移启用），再放行接线。</p>
 *
 * <p>与 {@link DataAccessArchTest} 同构：以 {@code haveNameNotMatching(".*Test")} 排除测试类
 * （测试 double 合法持 mapper 接口做 mock；本规则约束的是生产应用层接线纪律），以
 * {@code resideOutsideOfPackage("..workspace.mapper")} 豁免 mapper 接口包自身。棘轮方向＝只减不增：
 * 存活生产注入者数现为 0，任何正数为回潮，故规则恒绿即「休眠腿仍休眠」的持续凭证。</p>
 */
@AnalyzeClasses(packages = "com.chinacreator.gzcm.workspace")
class ScenarioTableMapperLegDormancyGateTest {

    private static final String SCENARIO_MAPPER_FQN =
            "com.chinacreator.gzcm.workspace.mapper.ScenarioMapper";
    private static final String SCENARIO_BINDING_MAPPER_FQN =
            "com.chinacreator.gzcm.workspace.mapper.ScenarioBindingMapper";

    /**
     * 生产代码（非测试、非 mapper 包自身）不得接线休眠腿 {@code ScenarioMapper}
     * （V205 {@code ecos_scenario_definition}，dev 0 行）。
     */
    @ArchTest
    static final ArchRule no_production_wiring_of_dormant_scenario_definition_mapper =
            noClasses().that()
                    .haveNameNotMatching(".*Test")
                    .and().resideOutsideOfPackage("com.chinacreator.gzcm.workspace.mapper")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName(SCENARIO_MAPPER_FQN);

    /**
     * 生产代码（非测试、非 mapper 包自身）不得接线休眠腿 {@code ScenarioBindingMapper}
     * （V206 {@code ecos_scenario_asset_binding}，dev 0 行）。
     */
    @ArchTest
    static final ArchRule no_production_wiring_of_dormant_scenario_asset_binding_mapper =
            noClasses().that()
                    .haveNameNotMatching(".*Test")
                    .and().resideOutsideOfPackage("com.chinacreator.gzcm.workspace.mapper")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName(SCENARIO_BINDING_MAPPER_FQN);
}
