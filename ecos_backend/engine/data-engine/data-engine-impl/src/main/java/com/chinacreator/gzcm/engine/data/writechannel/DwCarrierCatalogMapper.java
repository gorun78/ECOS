package com.chinacreator.gzcm.engine.data.writechannel;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * F02-05 / MC02 · 载体列元数据查询 Mapper。
 * <p>唯一写通道对 WHERE 列做「类型判」时需知列类型；本 Mapper 经
 * {@code information_schema} 取 PG 载体列类型（只读元数据，无业务写）。
 * 表/schema 不存在或列不存在 → 返回 {@code null}，守卫退化为保守命名判。
 */
@Mapper
public interface DwCarrierCatalogMapper {

    /**
     * @param schema  目标 schema（MC06 解析后）
     * @param table   目标表名（不含 schema）
     * @param column  列名
     * @return 列类型（大写，如 VARCHAR / TIMESTAMP / JSON）；元数据不可得返回 null
     */
    String columnTypeOf(@Param("schema") String schema,
                        @Param("table") String table,
                        @Param("column") String column);
}
