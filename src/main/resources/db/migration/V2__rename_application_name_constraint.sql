ALTER TABLE applications
    RENAME CONSTRAINT applications_name_key
        TO uk_applications_name;