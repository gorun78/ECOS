package com.chinacreator.gzcm.engine.kb.doc.mapper;

import com.chinacreator.gzcm.engine.kb.doc.model.KbDocChunk;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 非结构化文档分块表 Mapper（A3，表 {@code ecos_knowledge.kb_doc_chunk}）。
 */
@Mapper
public interface KbDocChunkMapper {

    /**
     * 批量 upsert 分块（单条 SQL 多 VALUES，禁循环单条写库）。
     * <p>按唯一键 {@code (doc_id, chunk_index)} 幂等，重复解析不产生重复行。
     */
    @Insert("<script>"
            + "INSERT INTO ecos_knowledge.kb_doc_chunk "
            + "(id, doc_id, source, chunk_index, content, char_start, char_end, metadata, status, embedding_id, created_at, updated_at) "
            + "VALUES "
            + "<foreach collection='items' item='it' separator=','>"
            + "(#{it.id}, #{it.docId}, #{it.source}, #{it.chunkIndex}, #{it.content}, "
            + " #{it.charStart}, #{it.charEnd}, CAST(#{it.metadata} AS jsonb), #{it.status}, #{it.embeddingId}, NOW(), NOW())"
            + "</foreach> "
            + "ON CONFLICT (doc_id, chunk_index) DO UPDATE SET "
            + " id = EXCLUDED.id, content = EXCLUDED.content, char_start = EXCLUDED.char_start, "
            + " char_end = EXCLUDED.char_end, metadata = EXCLUDED.metadata, "
            + " status = EXCLUDED.status, embedding_id = EXCLUDED.embedding_id, updated_at = NOW()"
            + "</script>")
    int batchUpsert(@Param("items") List<KbDocChunk> items);

    /**
     * 统计某文档的分块数（验收用）。
     */
    @Select("SELECT COUNT(*) FROM ecos_knowledge.kb_doc_chunk WHERE doc_id = #{docId}")
    long countByDocId(@Param("docId") String docId);
}
