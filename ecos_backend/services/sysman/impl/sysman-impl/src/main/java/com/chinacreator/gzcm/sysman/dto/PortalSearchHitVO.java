package com.chinacreator.gzcm.sysman.dto;

/**
 * Portal 全局搜索命中项（前端 {@code SearchHit} 契约：{@code type/id/name/url}）。
 *
 * <p>{@code id}/{@code name} 保持 {@code Object}：四类实体的主键/名称列在不同表里
 * 可能是 String/数值，旧实现原样透出，强转字符串会改 wire。</p>
 */
public record PortalSearchHitVO(String type, Object id, Object name, String url) {
}
