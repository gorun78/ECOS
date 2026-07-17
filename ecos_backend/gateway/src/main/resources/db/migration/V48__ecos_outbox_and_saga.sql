-- ECOS Outbox Pattern: Add outbox_event table to each domain schema for CDC-based event publishing
-- Sprint 1.4: Outbox Pattern for reliable event delivery via Kafka

-- Each microservice will write events to its own outbox table within its schema.
-- A CDC poller in each service reads from outbox and publishes to Kafka.

CREATE TABLE IF NOT EXISTS ecos_identity.outbox_event (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(128) NOT NULL,
    aggregate_type VARCHAR(128) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    published BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMP WITH TIME ZONE,
    kafka_topic VARCHAR(128),
    kafka_key VARCHAR(128)
);
CREATE INDEX IF NOT EXISTS idx_identity_outbox_unpublished ON ecos_identity.outbox_event (created_at) WHERE published = FALSE;

CREATE TABLE IF NOT EXISTS ecos_catalog.outbox_event (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(128) NOT NULL,
    aggregate_type VARCHAR(128) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    published BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMP WITH TIME ZONE,
    kafka_topic VARCHAR(128),
    kafka_key VARCHAR(128)
);
CREATE INDEX IF NOT EXISTS idx_catalog_outbox_unpublished ON ecos_catalog.outbox_event (created_at) WHERE published = FALSE;

CREATE TABLE IF NOT EXISTS ecos_ontology.outbox_event (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(128) NOT NULL,
    aggregate_type VARCHAR(128) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    published BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMP WITH TIME ZONE,
    kafka_topic VARCHAR(128),
    kafka_key VARCHAR(128)
);
CREATE INDEX IF NOT EXISTS idx_ontology_outbox_unpublished ON ecos_ontology.outbox_event (created_at) WHERE published = FALSE;

CREATE TABLE IF NOT EXISTS ecos_object.outbox_event (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(128) NOT NULL,
    aggregate_type VARCHAR(128) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    published BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMP WITH TIME ZONE,
    kafka_topic VARCHAR(128),
    kafka_key VARCHAR(128)
);
CREATE INDEX IF NOT EXISTS idx_object_outbox_unpublished ON ecos_object.outbox_event (created_at) WHERE published = FALSE;

CREATE TABLE IF NOT EXISTS ecos_workflow.outbox_event (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(128) NOT NULL,
    aggregate_type VARCHAR(128) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    published BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMP WITH TIME ZONE,
    kafka_topic VARCHAR(128),
    kafka_key VARCHAR(128)
);
CREATE INDEX IF NOT EXISTS idx_workflow_outbox_unpublished ON ecos_workflow.outbox_event (created_at) WHERE published = FALSE;

CREATE TABLE IF NOT EXISTS ecos_rule.outbox_event (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(128) NOT NULL,
    aggregate_type VARCHAR(128) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    published BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMP WITH TIME ZONE,
    kafka_topic VARCHAR(128),
    kafka_key VARCHAR(128)
);
CREATE INDEX IF NOT EXISTS idx_rule_outbox_unpublished ON ecos_rule.outbox_event (created_at) WHERE published = FALSE;

CREATE TABLE IF NOT EXISTS ecos_agent.outbox_event (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(128) NOT NULL,
    aggregate_type VARCHAR(128) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    published BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMP WITH TIME ZONE,
    kafka_topic VARCHAR(128),
    kafka_key VARCHAR(128)
);
CREATE INDEX IF NOT EXISTS idx_agent_outbox_unpublished ON ecos_agent.outbox_event (created_at) WHERE published = FALSE;

CREATE TABLE IF NOT EXISTS ecos_knowledge.outbox_event (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(128) NOT NULL,
    aggregate_type VARCHAR(128) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    published BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMP WITH TIME ZONE,
    kafka_topic VARCHAR(128),
    kafka_key VARCHAR(128)
);
CREATE INDEX IF NOT EXISTS idx_knowledge_outbox_unpublished ON ecos_knowledge.outbox_event (created_at) WHERE published = FALSE;

CREATE TABLE IF NOT EXISTS ecos_mission.outbox_event (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(128) NOT NULL,
    aggregate_type VARCHAR(128) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    published BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMP WITH TIME ZONE,
    kafka_topic VARCHAR(128),
    kafka_key VARCHAR(128)
);
CREATE INDEX IF NOT EXISTS idx_mission_outbox_unpublished ON ecos_mission.outbox_event (created_at) WHERE published = FALSE;

CREATE TABLE IF NOT EXISTS ecos_audit.outbox_event (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(128) NOT NULL,
    aggregate_type VARCHAR(128) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    published BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMP WITH TIME ZONE,
    kafka_topic VARCHAR(128),
    kafka_key VARCHAR(128)
);
CREATE INDEX IF NOT EXISTS idx_audit_outbox_unpublished ON ecos_audit.outbox_event (created_at) WHERE published = FALSE;

CREATE TABLE IF NOT EXISTS ecos_pipeline.outbox_event (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(128) NOT NULL,
    aggregate_type VARCHAR(128) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    published BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMP WITH TIME ZONE,
    kafka_topic VARCHAR(128),
    kafka_key VARCHAR(128)
);
CREATE INDEX IF NOT EXISTS idx_pipeline_outbox_unpublished ON ecos_pipeline.outbox_event (created_at) WHERE published = FALSE;

-- Saga state table (in ecos_config schema for centralized saga orchestration)
CREATE TABLE IF NOT EXISTS ecos_config.saga_instance (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    saga_type VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'STARTED',
    current_step INT NOT NULL DEFAULT 0,
    total_steps INT NOT NULL,
    input_data JSONB NOT NULL DEFAULT '{}',
    compensation_data JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT
);
CREATE INDEX IF NOT EXISTS idx_saga_status ON ecos_config.saga_instance (status, created_at);
