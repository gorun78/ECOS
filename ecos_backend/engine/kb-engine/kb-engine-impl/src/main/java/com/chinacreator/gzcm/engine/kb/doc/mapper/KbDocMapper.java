package com.chinacreator.gzcm.engine.kb.doc.mapper;

import com.chinacreator.gzcm.engine.kb.doc.model.KbDoc;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 非结构化文档过渡表 Mapper（A3，表 {@code ecos_knowledge.kb_doc}）。
 */
@Mapper
public interface KbDocMapper {

    /**
     * 幂等 upsert 文档行（按 doc_id 主键；重复 ingest 不产生重复行）。
     */
    @Insert("INSERT INTO ecos_knowledge.kb_doc "
            + "(doc_id, source, original_file_name, object_key, content_type, size_bytes, "
            + " parse_status, chunk_size, chunk_overlap, chunk_count, created_at, updated_at) "
            + "VALUES (#{docId}, #{source}, #{originalFileName}, #{objectKey}, #{contentType}, #{sizeBytes}, "
            + " #{parseStatus}, #{chunkSize}, #{chunkOverlap}, #{chunkCount}, NOW(), NOW()) "
            + "ON CONFLICT (doc_id) DO UPDATE SET "
            + " source = EXCLUDED.source, original_file_name = EXCLUDED.original_file_name, "
            + " object_key = EXCLUDED.object_key, content_type = EXCLUDED.content_type, "
            + " size_bytes = EXCLUDED.size_bytes, parse_status = EXCLUDED.parse_status, "
            + " chunk_size = EXCLUDED.chunk_size, chunk_overlap = EXCLUDED.chunk_overlap, "
            + " chunk_count = EXCLUDED.chunk_count, updated_at = NOW()")
    int upsert(KbDoc doc);

    /**
     * 更新状态机字段（含失败原因与解析文本资源定位）。
     */
    @Update("UPDATE ecos_knowledge.kb_doc SET parse_status = #{parseStatus}, "
            + " chunk_count = #{chunkCount}, text_source_path = #{textSourcePath}, "
            + " error_message = #{errorMessage}, updated_at = NOW() "
            + "WHERE doc_id = #{docId}")
    int updateStatus(@Param("docId") String docId,
                     @Param("parseStatus") String parseStatus,
                     @Param("chunkCount") Integer chunkCount,
                     @Param("textSourcePath") String textSourcePath,
                     @Param("errorMessage") String errorMessage);

    /**
     * 按 doc_id 查询文档（含状态）。
     */
    @Select("SELECT doc_id, source, original_file_name, object_key, content_type, size_bytes, "
            + " parse_status, chunk_size, chunk_overlap, chunk_count, text_source_path, "
            + " error_message, created_at, updated_at "
            + "FROM ecos_knowledge.kb_doc WHERE doc_id = #{docId}")
    KbDoc findById(@Param("docId") String docId);
}
