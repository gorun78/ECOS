package com.chinacreator.gzcm.engine.kb.repo;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * pgvector 扩展可用性探测（B4 向量链路修复）。
 *
 * <p>检索（{@code KnowledgeRetrievalServiceImpl}）与写入（{@code KnowledgeVectorWriteService}）
 * 共用同一判定，避免各自重复实现扩展探测逻辑（架构铁律 §2.5 公共能力不重复造）。
 *
 * <p>不可用时调用方必须降级并 {@code log.warn}（禁止静默降级，D1 教训）。
 *
 * @since B4 (2026-09-19)
 */
@Component
public class PgVectorSupport {

    private static final Logger log = LoggerFactory.getLogger(PgVectorSupport.class);

    private final JdbcTemplate jdbcTemplate;

    /** pgvector 是否可用（volatile：启动探测一次，运行期只读） */
    private volatile boolean available = false;

    /** 探测到的 pgvector 版本，未知为 unknown */
    private volatile String version = "unknown";

    public PgVectorSupport(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 启动时探测 pgvector 扩展是否已安装（失败不阻断应用启动）。
     */
    @PostConstruct
    public void refresh() {
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT extname, extversion FROM pg_extension WHERE extname = 'vector'");
            version = String.valueOf(row.getOrDefault("extversion", "unknown"));
            available = true;
            log.info("✅ pgvector 扩展已就绪 — version {}", version);
        } catch (Exception e) {
            available = false;
            version = "unknown";
            log.warn("⚠️ pgvector 扩展不可用 — 向量检索/写入降级。镜像需内置 pgvector（postgres:16-alpine 未内置）。原因: {}",
                    e.getMessage());
        }
    }

    /**
     * @return true 表示 pgvector 扩展已安装，可使用 vector 类型与 HNSW 索引
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * @return pgvector 扩展版本；未安装时为 unknown
     */
    public String getVersion() {
        return version;
    }
}
