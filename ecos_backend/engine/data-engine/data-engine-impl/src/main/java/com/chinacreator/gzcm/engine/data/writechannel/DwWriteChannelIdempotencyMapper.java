package com.chinacreator.gzcm.engine.data.writechannel;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * F02-05 / C.3.6 唯一写通道 · 幂等台账 Mapper。
 * <p>MyBatis 数据访问（三层 DAO 层，铁律 §五）。落表 {@code td_dw_write_channel_idempotency}（V260）。
 */
@Mapper
public interface DwWriteChannelIdempotencyMapper {

    /** 按幂等键查首次登记（同 key 重放命中即返回既有行）。 */
    DwWriteChannelIdempotency findByKey(@Param("idempotencyKey") String idempotencyKey);

    /** 首次登记（唯一索引兜底并发竞态）。 */
    int insert(DwWriteChannelIdempotency record);
}
