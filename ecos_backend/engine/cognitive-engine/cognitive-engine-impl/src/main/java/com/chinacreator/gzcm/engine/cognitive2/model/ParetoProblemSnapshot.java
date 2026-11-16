package com.chinacreator.gzcm.engine.cognitive2.model;

/**
 * 一次优化落库快照（D106；{@code /result/{problemId}} 的读取载荷）。
 *
 * @param solutionsJson 前沿解序列化后的原文，由调用方按需反序列化；DAO 不解析它，
 *                      以保证「同一 problemId 两次读取给同一前沿」
 */
public record ParetoProblemSnapshot(
        String problemId,
        String solutionsJson,
        int frontSize,
        long elapsedMs) {
}
