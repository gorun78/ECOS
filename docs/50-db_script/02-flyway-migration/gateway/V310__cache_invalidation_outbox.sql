-- V310 (§2.9.8④ Outbox/事务消息：跨节点缓存失效的可靠投递面；#147AF/D)
--
-- 为什么需要：本册 §2.9.8④ 的「主动失效与事件驱动」要求写路径在提交权威数据后广播失效；
-- 直接发消息在事务回滚时会「幽灵失效」、在进程崩溃时会「丢失失效」。Outbox 把失效意图
-- 与业务事务同库落行，再由后台中继投递到 Redis 频道——至少一次、可重试、可观测。
--
-- 基线列（DR06/DR07/DR08，V200+ 新表必带）：create_time / update_time / create_by /
--   update_by / is_deleted / version_no(VARCHAR(20) NOT NULL) / domain(VARCHAR(50))。
--   注意 `created_at/updated_at` 属 V150 存量别名，**新表不豁免**（本脚本首稿曾用 created_at
--   并被 DrBaselineColumnsTest 判红，现改为规范列名）。
-- 语义：
--   * `status`：PENDING → PUBLISHED（中继成功）/ FAILED（超次，供运维重放）；
--   * `payload_json`：{env, domain, reason, objectType?, objectId?}，消费方按域/对象失效；
--   * 只加表，不改既有表；逻辑删走 is_deleted。
--
-- 纪律: IR02 手动 psql -f（Flyway 永久禁用）/ IR03 只加不删 / MC01 主键 VARCHAR(36) 应用侧生成 /
--       DR01 小写下划线 + schema 限定名。

CREATE TABLE IF NOT EXISTS ecos_control.cache_invalidation_outbox (
    id            VARCHAR(36)  NOT NULL,
    event_type    VARCHAR(64)  NOT NULL,
    payload_json  TEXT         NOT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    attempts      INTEGER      NOT NULL DEFAULT 0,
    domain        VARCHAR(50)  NOT NULL DEFAULT 'sysman',
    version_no    VARCHAR(20)  NOT NULL DEFAULT 'v1',
    published_at  TIMESTAMP,
    last_error    VARCHAR(512),
    create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by     VARCHAR(64)  NOT NULL DEFAULT 'system',
    update_by     VARCHAR(64)  NOT NULL DEFAULT 'system',
    is_deleted    SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT pk_cache_invalidation_outbox PRIMARY KEY (id),
    CONSTRAINT ck_cache_invalidation_outbox_status
        CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

-- 已被本批首稿误建为 created_at 形态的 dev 库幂等补列（新库此段为空操作）。
ALTER TABLE ecos_control.cache_invalidation_outbox
    ADD COLUMN IF NOT EXISTS domain      VARCHAR(50) NOT NULL DEFAULT 'sysman',
    ADD COLUMN IF NOT EXISTS version_no  VARCHAR(20) NOT NULL DEFAULT 'v1',
    ADD COLUMN IF NOT EXISTS create_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS update_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS create_by   VARCHAR(64) NOT NULL DEFAULT 'system',
    ADD COLUMN IF NOT EXISTS update_by   VARCHAR(64) NOT NULL DEFAULT 'system';

COMMENT ON TABLE ecos_control.cache_invalidation_outbox IS
    '§2.9.8④ 缓存失效 Outbox：写路径事务内落行，后台中继投递 Redis 频道（至少一次、可重试）';
COMMENT ON COLUMN ecos_control.cache_invalidation_outbox.payload_json IS
    '{env, domain, reason, objectType?, objectId?}；消费方按域/对象失效，禁携带业务内容';

CREATE INDEX IF NOT EXISTS idx_cache_invalidation_outbox_pending
    ON ecos_control.cache_invalidation_outbox (status, create_time);
