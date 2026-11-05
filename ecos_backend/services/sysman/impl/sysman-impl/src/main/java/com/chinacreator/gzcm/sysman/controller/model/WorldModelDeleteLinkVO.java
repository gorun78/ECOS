package com.chinacreator.gzcm.sysman.controller.model;

/** 删除因果链回执：{@code success + id}（与删除目标回执分型，避免多出级联键）。 */
public record WorldModelDeleteLinkVO(boolean success, String id) {
}
