package com.chinacreator.gzcm.engine.data.fact.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 期间关账状态 mapper（H-3 交付① / V278）：{@code ecos_data.ecos_data_period_status}。
 *
 * <p>控制域表（ST07：期间开关是平台控制面状态）；MC06 schema 限定。
 * 查询只按登记键/状态列走索引（IR04 显式列清单、IR05 全 #{}）；
 * close_detail_json 仅描述用，不进任何 WHERE（MC02）。
 */
@Mapper
public interface PeriodStatusMapper {

    /** 按期间取登记行（uniq_edps_period 命中；无行返回 null）。 */
    Map<String, Object> selectByPeriod(@Param("period") String period);

    /** 全部 CLOSED 期间（DatabasePeriodLock 缓存刷新用；idx_edps_status 命中，控制域小表）。 */
    List<String> selectClosedPeriods();

    /** 单行 INSERT（应用侧预填全部列，含 MC01 UUID 与七基线列；时间列 NOW()）。 */
    int insertRow(Map<String, Object> row);

    /**
     * OPEN → CLOSED 单向更新（幂等重放由 Service 层 selectByPeriod 先行判定，
     * 本语句 WHERE status='OPEN' 双保险：并发重复 close 最多一条生效）。
     */
    int updateOpenToClosed(@Param("period") String period,
                           @Param("closedBy") String closedBy,
                           @Param("closeReason") String closeReason,
                           @Param("closeDetailJson") String closeDetailJson,
                           @Param("versionNo") String versionNo,
                           @Param("updateBy") String updateBy);
}
