package com.chinacreator.gzcm.engine.cognitive2.mind;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link MindCapabilityMask} ↔ DB 列 {@code capability_mask_json}（TEXT，DR04，V187）的唯一序列化器。
 *
 * <p><b>契约</b>（F05-11 验收 {@code #roundTripThroughTextJsonArray}）：DB 值恒为紧凑 JSON 字符串
 * 数组形态：空 = {@code []}，五项全集 =
 * {@code ["DETECT","FORECAST","SIMULATE","PLAN","REVIEW"]}（按枚举声明序，防漂移）；序列化
 * <b>只经本类</b>，Mapper/Service 不得手写 JSON 字符串（离线可单测 round-trip，禁散点拼串）。
 *
 * <p><b>读容错</b>：空串/空白 → 空列表；非法 JSON 或未知成员 → 抛 {@link CodecException}
 * （读侧 Service 决定记 {@code issues[]} 还是回空 + issue，本类不做静默吞）。
 */
public final class MindCapabilityMaskCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .disable(SerializationFeature.INDENT_OUTPUT);

    private MindCapabilityMaskCodec() {
    }

    /** 枚举序序列化为紧凑 JSON 字符串数组（{@code []} / {@code ["DETECT",…]}）。 */
    public static String toJsonArray(List<MindCapabilityMask> masks) {
        List<MindCapabilityMask> in = (masks == null) ? List.of() : masks;
        List<String> wires = in.stream().map(MindCapabilityMask::wire).collect(Collectors.toList());
        try {
            return MAPPER.writeValueAsString(wires);
        } catch (JsonProcessingException e) {
            throw new CodecException("capabilityMask 序列化失败: " + e.getMessage(), e);
        }
    }

    /** 解析 DB 值；空/空白 → 空列表；非法 JSON/未知成员 → {@link CodecException}。 */
    public static List<MindCapabilityMask> fromJsonArray(String wireValue) {
        if (wireValue == null) return List.of();
        String v = wireValue.trim();
        if (v.isEmpty()) return List.of();
        List<String> wires;
        try {
            @SuppressWarnings("unchecked")
            List<String> parsed = MAPPER.readValue(v, List.class);
            wires = parsed;
        } catch (JsonProcessingException e) {
            throw new CodecException("capabilityMask 不是 JSON 数组: " + v, e);
        }
        return wires.stream().map(w ->
            MindCapabilityMask.fromWire(w).orElseThrow(
                () -> new CodecException("未知 capabilityMask 成员: " + w)))
            .collect(Collectors.toList());
    }

    /** 序列化契约违例（DB 值形态与 api 枚举漂移）。 */
    public static final class CodecException extends RuntimeException {
        @java.io.Serial
        private static final long serialVersionUID = 1L;

        public CodecException(String message) {
            super(message);
        }

        public CodecException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
