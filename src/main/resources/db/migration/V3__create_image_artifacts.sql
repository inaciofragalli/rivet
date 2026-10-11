CREATE TABLE image_artifacts (
    id UUID PRIMARY KEY,
    image_name VARCHAR(512) NOT NULL,
    image_tag VARCHAR(128),
    architecture VARCHAR(64) NOT NULL,
    os VARCHAR(64) NOT NULL,
    exposed_ports JSONB NOT NULL DEFAULT '[]'::jsonb,
    archive_size_bytes BIGINT NOT NULL
        CHECK (archive_size_bytes > 0),
    sha256 VARCHAR(64) NOT NULL
        CHECK (char_length(sha256) = 64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_image_artifact_ports_array
        CHECK (jsonb_typeof(exposed_ports) = 'array')
);