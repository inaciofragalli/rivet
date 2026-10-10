CREATE TABLE applications (
    id UUID PRIMARY KEY,
    name VARCHAR(30) NOT NULL UNIQUE,
    image TEXT NOT NULL,
    container_port INTEGER NOT NULL
        CHECK ( container_port BETWEEN 1 AND 65535 )
);