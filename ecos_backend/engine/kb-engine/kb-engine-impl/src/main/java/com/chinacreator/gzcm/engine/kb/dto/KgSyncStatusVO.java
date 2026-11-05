package com.chinacreator.gzcm.engine.kb.dto;

/**
 * 同步状态：{@code objectTypes}（按类型的同步状态行，属主 {@code KgSyncService} 原样结构）+
 * {@code overallStatus}（总体状态）。
 */
public record KgSyncStatusVO(Object objectTypes, String overallStatus) {
}
