package com.chinacreator.gzcm.engine.cognitive2.model;

import java.util.List;

/** 历史列表容器（D106；{@code {data,total}} 两键）。 */
public record ParetoProblemPage(
        List<ParetoProblemSummary> data,
        int total) {
}
