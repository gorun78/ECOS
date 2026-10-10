package com.chinacreator.gzcm.engine.ai.capability;

import java.util.List;

/**
 * 能力读服务契约（《补充设计 v1.0》§13 {@code GET /api/v1/ai/capabilities}，只读）。
 *
 * <p>读侧单源纪律（E.5/§十）：能力目录以 {@link AiCapabilityId} 代码冻结为唯一词表来源；
 * 工具集分组与契约版本从既有 {@code sys_config} 的 {@code wagent.toolset.*} 族 key 读，
 * 一律经 sysman {@code SysConfigService} 门面，<b>禁 @Value 散落</b>、禁各自私读表。
 * 缺配置回落代码冻结默认（目录三要件已冻结在枚举上，配置只补充工具集归属，不改变目录）。</p>
 */
public interface CapabilityReadService {

    /**
     * 能力 + 契约版本清单（§13 三行 GET 面）。
     *
     * @return 11 项能力目录；每项含 capabilityId/contractVersion/evidenceRequired/
     *         automationLevel/costUnit/family 与所属 toolset（工具集族 key 命中，可空）。
     */
    List<CapabilityReadVO> listCapabilities();

    /** 单个能力契约版本快照（缺 → 404 由 Controller 映射，禁造空）。 */
    CapabilityReadVO getCapability(String capabilityId);
}
