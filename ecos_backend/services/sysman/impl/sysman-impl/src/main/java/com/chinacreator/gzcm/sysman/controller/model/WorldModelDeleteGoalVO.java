package com.chinacreator.gzcm.sysman.controller.model;

/**
 * 删除目标回执：{@code success + id + cascadedLinksRemoved}（级联删除的因果链条数）。
 * 删除因果链回执同为 {@code success + id}，用 {@code cascadedLinksRemoved=0} 会多出键，
 * 故两腿分开由控制器 {#deleteLink} 使用 {@code WorldModelDeleteLinkVO}。
 */
public record WorldModelDeleteGoalVO(boolean success, String id, int cascadedLinksRemoved) {
}
