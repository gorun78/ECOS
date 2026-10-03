package com.chinacreator.gzcm.engine.kb.assumption;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;

/**
 * 情景假设·数值事实 Mapper（表 {@code ecos_dw.ecos_kb_assumption_value}，V177）。
 * <p>
 * R-8 ②：数值 {@code value NUMERIC(18,4)} 落业务域，写方 = data-engine 写通道（ADR-14）；
 * kb 侧为可读方；本类 insert 供 dev/单测态代写（生产上 data-engine 代管）。
 *
 * @author ECOS KB Team
 */
@Mapper
public interface KbAssumptionValueMapper {

    @Insert("INSERT INTO ecos_dw.ecos_kb_assumption_value ("
            + "id, assumption_id, assumption_key, value_type, value, value_semantic, trace_id, domain, "
            + "version_no, is_deleted, create_by, update_by, create_time, update_time) "
            + "VALUES (#{id}, #{assumptionId}, #{assumptionKey}, #{valueType}, #{value}, #{valueSemantic}, "
            + "#{traceId}, #{domain}, '1', 0, #{createBy}, #{updateBy}, #{createTime}, #{updateTime})")
    int insert(@Param("id") String id,
               @Param("assumptionId") String assumptionId,
               @Param("assumptionKey") String assumptionKey,
               @Param("valueType") String valueType,
               @Param("value") BigDecimal value,
               @Param("valueSemantic") String valueSemantic,
               @Param("traceId") String traceId,
               @Param("domain") String domain,
               @Param("createBy") String createBy,
               @Param("updateBy") String updateBy,
               @Param("createTime") Date createTime,
               @Param("updateTime") Date updateTime);

    /** 按 value_ref（= 本表 id）取数值。 */
    @Select("SELECT id, assumption_id AS assumptionId, assumption_key AS assumptionKey, "
            + "value_type AS valueType, value, value_semantic AS valueSemantic, trace_id AS traceId "
            + "FROM ecos_dw.ecos_kb_assumption_value WHERE id = #{valueRef} AND is_deleted = 0")
    Map<String, Object> findById(@Param("valueRef") String valueRef);
}
