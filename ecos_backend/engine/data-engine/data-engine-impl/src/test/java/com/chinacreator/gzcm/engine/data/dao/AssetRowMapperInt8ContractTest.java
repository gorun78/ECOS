package com.chinacreator.gzcm.engine.data.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;

import com.chinacreator.gzcm.engine.data.dto.DataAssetVO;

/**
 * B39/P1-17 契约门：资产行映射的 int8→Integer 转换。
 *
 * <p>实证：{@code confirmed_field_count} 子查询是 {@code COUNT(*)}（JDBC 返回 int8），
 * 原实现 {@code rs.getObject(..., Integer.class)} 抛
 * 「conversion to class java.lang.Integer from int8 not supported」⇒
 * 资产详情恒 404（404 分支吞掉异常）、资产列表恒 409。修法按 Long 取再降位。</p>
 *
 * <p>判据：桩 ResultSet 仅在以 {@code Long.class} 读取时返回 7；若实现回退到
 * {@code Integer.class}，取值恒 null ⇒ 断言转红。</p>
 */
class AssetRowMapperInt8ContractTest {

    @Test
    @DisplayName("confirmed_field_count 按 Long 读取并降位为 Integer")
    void readsCountAsLongThenNarrows() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString(anyString())).thenReturn(null);
        when(rs.getObject("field_count", Integer.class)).thenReturn(null);
        when(rs.getObject("record_count", Long.class)).thenReturn(null);
        when(rs.getObject("confirmed_field_count", Long.class)).thenReturn(7L);

        RowMapper<DataAssetVO> mapper = AssetDao.assetRowMapper();
        DataAssetVO vo = mapper.mapRow(rs, 0);

        assertEquals(7, vo.getConfirmedFieldCount(),
                "COUNT(*) 是 int8：必须以 Long 读取再降位，禁 Integer.class 直读");
    }

    @Test
    @DisplayName("COUNT 为 0 时降位为 0（不返回 null）")
    void zeroCountNarrowsToZero() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString(anyString())).thenReturn(null);
        when(rs.getObject("confirmed_field_count", Long.class)).thenReturn(0L);

        DataAssetVO vo = AssetDao.assetRowMapper().mapRow(rs, 0);
        assertEquals(0, vo.getConfirmedFieldCount());
    }
}
