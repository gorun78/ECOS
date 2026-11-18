-- PMO-37 元数据采集任务执行审计日志
CREATE TABLE IF NOT EXISTS td_metadata_collect_log (
    id              BIGSERIAL PRIMARY KEY,
    datasource_id   VARCHAR(64) NOT NULL,
    count_method    VARCHAR(20)  NOT NULL DEFAULT 'ESTIMATE',
    tables_total    INTEGER DEFAULT 0,
    tables_ok       INTEGER DEFAULT 0,
    tables_failed   INTEGER DEFAULT 0,
    failed_tables   TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'SUCCEEDED',
    detail          TEXT,
    task_id         VARCHAR(64),
    elapsed_ms      BIGINT,
    create_time     TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_mcl_datasource ON td_metadata_collect_log(datasource_id, create_time DESC);
COMMENT ON TABLE td_metadata_collect_log IS 'PMO-37 元数据采集任务执行审计日志';
