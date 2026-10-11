ALTER TABLE applications
    ADD COLUMN image_artifact_id UUID;

ALTER TABLE applications
    ADD CONSTRAINT fk_applications_image_artifact
        FOREIGN KEY (image_artifact_id)
        REFERENCES image_artifacts(id);