package com.chinacreator.gzcm.runtime.access.document;

import com.chinacreator.gzcm.common.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 文档分块公共能力（B6-2 由 kb-engine 上移 runtime-access）。
 *
 * <p>字符级滑动窗口切分：每块长度 {@code chunkSize}，相邻块重叠 {@code chunkOverlap} 字符。
 * 方案 §4「分块策略」规定 chunk 切分是数据工作台解析节点的产物；本类是唯一实现，
 * data-engine（A1 目标态，落 DW 层 {@code doc_chunk}）与 kb-engine（A3 过渡态，落自有表）共用，
 * 禁止两处各写一份（铁律 §2.5-6）。
 *
 * <p>chunkSize 取值范围与前端 {@code CHUNK_SIZE_OPTIONS = [256,512,1024,2048]} 严格一致；
 * 非法值一律抛 {@link ValidationException}（明确原因，不静默兜底）。
 *
 * @author ECOS Runtime Access Team
 */
@Service
public class DocumentChunkSplitter {

    private static final Logger log = LoggerFactory.getLogger(DocumentChunkSplitter.class);

    /** 允许的分块大小（与前端 CHUNK_SIZE_OPTIONS 对齐） */
    public static final Set<Integer> ALLOWED_CHUNK_SIZES = Set.of(256, 512, 1024, 2048);

    /** 默认分块大小 */
    public static final int DEFAULT_CHUNK_SIZE = 512;

    /** 默认分块重叠 */
    public static final int DEFAULT_CHUNK_OVERLAP = 64;

    /**
     * 滑动窗口切分（字符级，重叠 overlap）。
     *
     * @param text        待切分文本（null/空返回空列表）
     * @param chunkSize   分块大小（须 ∈ {256,512,1024,2048}）
     * @param chunkOverlap 分块重叠（须 ≥0 且 &lt; chunkSize）
     * @return 分块列表（空白块跳过，序号连续）
     * @throws ValidationException chunkSize/chunkOverlap 非法
     */
    public List<DocumentChunk> split(String text, int chunkSize, int chunkOverlap) {
        validateChunkSize(chunkSize);
        validateChunkOverlap(chunkOverlap, chunkSize);

        List<DocumentChunk> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }
        int length = text.length();
        int start = 0;
        int index = 0;
        while (start < length) {
            int end = Math.min(start + chunkSize, length);
            String content = text.substring(start, end);
            if (!content.isBlank()) {
                chunks.add(new DocumentChunk(index, content, start, end));
                index++;
            }
            if (end >= length) {
                break;
            }
            start = end - chunkOverlap;
        }
        log.debug("文档切分完成: length={}, chunkSize={}, overlap={}, chunks={}",
                length, chunkSize, chunkOverlap, chunks.size());
        return chunks;
    }

    /**
     * 解析生效分块大小：请求值（可空取默认）→ 合法性校验。
     *
     * @param requested    请求值（可空）
     * @param defaultSize  默认值（可空时取 {@link #DEFAULT_CHUNK_SIZE}）
     * @return 合法分块大小
     * @throws ValidationException 非法值
     */
    public int resolveChunkSize(Integer requested, Integer defaultSize) {
        int fallback = defaultSize == null ? DEFAULT_CHUNK_SIZE : defaultSize;
        int value = requested == null ? fallback : requested;
        validateChunkSize(value);
        return value;
    }

    /**
     * 解析生效分块重叠：请求值（可空取默认）→ 合法性校验。
     *
     * @param requested      请求值（可空）
     * @param chunkSize      已生效分块大小
     * @param defaultOverlap 默认值（可空时取 {@link #DEFAULT_CHUNK_OVERLAP}）
     * @return 合法分块重叠
     * @throws ValidationException 非法值
     */
    public int resolveChunkOverlap(Integer requested, int chunkSize, Integer defaultOverlap) {
        int fallback = defaultOverlap == null ? DEFAULT_CHUNK_OVERLAP : defaultOverlap;
        int value = requested == null ? fallback : requested;
        validateChunkOverlap(value, chunkSize);
        return value;
    }

    /** 校验分块大小 ∈ {256,512,1024,2048}。 */
    public void validateChunkSize(int chunkSize) {
        if (!ALLOWED_CHUNK_SIZES.contains(chunkSize)) {
            throw new ValidationException("chunkSize",
                    "非法分块大小: " + chunkSize + "（合法值: 256/512/1024/2048）");
        }
    }

    /** 校验分块重叠 ≥0 且 &lt; chunkSize。 */
    public void validateChunkOverlap(int chunkOverlap, int chunkSize) {
        if (chunkOverlap < 0 || chunkOverlap >= chunkSize) {
            throw new ValidationException("chunkOverlap",
                    "分块重叠须 ≥0 且小于 chunkSize(" + chunkSize + ")，当前=" + chunkOverlap);
        }
    }
}
