package com.chinacreator.gzcm.sysman.controller.model;

import java.util.List;

/** 目标列表 / 因果链列表信封：{@code data + total}。 */
public record WorldModelListVO<T>(List<T> data, int total) {
}
