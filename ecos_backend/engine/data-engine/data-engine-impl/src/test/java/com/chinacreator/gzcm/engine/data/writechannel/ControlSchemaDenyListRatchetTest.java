package com.chinacreator.gzcm.engine.data.writechannel;

import com.chinacreator.gzcm.common.data.model.DataResource;
import com.chinacreator.gzcm.engine.data.dto.WriteChannelRequest;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MC04 控制域 deny-list 棘轮门 —— <b>已 DROP 的 legacy schema 必须留在 {@code CONTROL_SCHEMAS} 里</b>。
 *
 * <p><b>为什么需要本门（反证一条计划项）</b>：数据库脚本整理计划曾要求把
 * {@code DwSchemaQualifier.CONTROL_SCHEMAS} 中的 {@code ecos_sysman}/{@code ecos_security}
 * 「移除将 DROP 的 schema」，并把该集合称作「白名单」。实查证明该前提<b>倒置</b>：
 * <ul>
 *   <li>{@code classifyDomain()} 对<b>不在</b>集合内的 schema <b>默认返回 BUSINESS</b>，
 *       即该集合是 fail-closed 的<b>控制域 deny-list</b>，不是白名单；</li>
 *   <li>{@code OlapEngineResolver.guard()} 只拒 {@code CONTROL → OLAP}。
 *       故「移除」= 把这两个名字改判 BUSINESS = <b>放开</b> MC04 守卫：
 *       ultimate 档配了 Doris 时 {@code carrierRef="ecos_sysman.x"} + DORIS + APPLICATION
 *       会被放行并登记成 {@code OLAP_TABLE} 载体，而 OLAP 是独立存储，schema 不存在于 PG
 *       并不能拦住它。</li>
 * </ul>
 * 两个 schema 已于 V349 实跑 DROP（dev 库 {@code pg_namespace} 实查不存在），但<b>名字仍须被认作控制域</b>：
 * knownLegacy 口径是「只停写不迁移」，且 {@code GatewayNoDomainTableArchTest} 的禁域正则至今仍列
 * {@code ecos_sysman}。deny-list 里留着已消失的名字零成本、纯保护；删掉才是净损失。
 *
 * <p><b>本门钉的是行为而非仅常量</b>：第一条用例走完整写通道，移除集合成员即红。
 * 此前<b>无任何测试</b>钉该集合成员（{@code WriteChannelTestFixtures} 只是 new 出来），
 * 故移除会静默全绿 —— 这正是本门要堵的假绿。
 */
@DisplayName("MC04：已 DROP / knownLegacy 的控制域 schema 名不得移出 deny-list")
class ControlSchemaDenyListRatchetTest {

    /** V349 已实跑 DROP 的两个 legacy schema —— 名字仍须判 CONTROL。 */
    private static final String[] DROPPED_LEGACY = {"ecos_sysman", "ecos_security"};

    /** knownLegacy「只停写不迁」四个 + 仍在库中的 ecos_dq，均须判 CONTROL。 */
    private static final String[] KNOWN_LEGACY = {
            "ecos_dq", "ecos_buszhi", "ecos_dccheng", "ecos_aiming"};

    @Test
    @DisplayName("已 DROP 的 ecos_sysman/ecos_security 写 OLAP → 仍 MC04 拒，且不登记载体")
    void droppedLegacySchemasStillRejectedFromOlap() {
        for (String schema : DROPPED_LEGACY) {
            WriteChannelTestFixtures.Ctx ctx = olapEnabled();
            when(ctx.dataResourceRepository.countRegisteredCarriersByLayer(any())).thenReturn(1);
            ctx.buildService();

            WriteChannelRequest req = WriteChannelTestFixtures.baseRequest();
            req.setCarrierRef(schema + ".ecos_any_table");
            req.setStorageKind("DORIS");
            req.setLayer("APPLICATION");

            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> ctx.service.write(req),
                    schema + " 已不在 CONTROL_SCHEMAS ⇒ 被改判 BUSINESS 而放行进 OLAP（MC04 守卫被削弱）");
            assertTrue(e.getMessage().contains("ECOS-DATA-062"),
                    schema + " 应命中 MC04 错误码: " + e.getMessage());
            verify(ctx.dataResourceRepository, never()).insert(any(DataResource.class));
            verify(ctx.idempotencyMapper, never()).insert(any(DwWriteChannelIdempotency.class));
        }
    }

    @Test
    @DisplayName("knownLegacy 四 schema + ecos_dq 仍判 CONTROL（只停写不迁，非死配置）")
    void knownLegacySchemasRemainControlDomain() {
        DwSchemaQualifier qualifier = new DwSchemaQualifier("ecos_dw");
        for (String schema : KNOWN_LEGACY) {
            assertEquals(DwSchemaQualifier.Domain.CONTROL, qualifier.classifyDomain(schema),
                    schema + " 属 knownLegacy 控制域，移出即放开 MC04");
        }
        for (String schema : DROPPED_LEGACY) {
            assertEquals(DwSchemaQualifier.Domain.CONTROL, qualifier.classifyDomain(schema),
                    schema + " 虽已 DROP，名字仍须认作控制域（deny-list 留名零成本、纯保护）");
        }
    }

    /**
     * 语义自证：集合是<b>默认开放</b>的 deny-list —— 未列名者一律 BUSINESS。
     * 这条把「移除成员 = 放开守卫」的因果钉成可执行事实，而非口头推断。
     */
    @Test
    @DisplayName("未列名的 schema 默认判 BUSINESS ⇒ 移除成员等于放开 MC04（语义自证）")
    void unlistedSchemaDefaultsToBusiness() {
        DwSchemaQualifier qualifier = new DwSchemaQualifier("ecos_dw");
        assertEquals(DwSchemaQualifier.Domain.BUSINESS,
                qualifier.classifyDomain("ecos_never_registered_schema"),
                "默认分支不是 CONTROL ⇒ CONTROL_SCHEMAS 是 deny-list，删成员即放开守卫");
        assertEquals(DwSchemaQualifier.Domain.BUSINESS, qualifier.classifyDomain(null),
                "null 亦落默认 BUSINESS 分支");
    }

    private static WriteChannelTestFixtures.Ctx olapEnabled() {
        WriteChannelTestFixtures.Ctx ctx = WriteChannelTestFixtures.wire(mock(EventBusService.class));
        ctx.olapEngineResolver = new OlapEngineResolver("doris");
        return ctx;
    }
}