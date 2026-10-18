CREATE TABLE deployments (
    id UUID PRIMARY KEY,

    application_id UUID NOT NULL
        CONSTRAINT fk_deployments_application
            REFERENCES applications(id),
    server_id UUID NOT NULL
        CONSTRAINT fk_deployments_server
            REFERENCES servers(id),
    image_artifact_id UUID
        CONSTRAINT fk_deployments_image_artifact
            REFERENCES image_artifacts(id),

    image_reference TEXT NOT NULL,
    container_port INTEGER NOT NULL
        CHECK (container_port BETWEEN 1 AND 65535),

    status VARCHAR(32) NOT NULL DEFAULT 'PENDING'
        CONSTRAINT ck_deployments_status
            CHECK (status IN (
                'PENDING',
                'TRANSFERRING',
                'STARTING',
                'VERIFYING',
                'SUCCESS',
                'FAILED',
                'INTERRUPTED'
            )),

    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    container_id VARCHAR(64),
    error_code VARCHAR(64),
    error_message TEXT,

    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_deployments_application_requested
    ON deployments(application_id, requested_at DESC);

CREATE UNIQUE INDEX uk_deployments_active_application_server
    ON deployments(application_id, server_id)
    WHERE status IN (
        'PENDING',
        'TRANSFERRING',
        'STARTING',
        'VERIFYING',
        'INTERRUPTED'
    );