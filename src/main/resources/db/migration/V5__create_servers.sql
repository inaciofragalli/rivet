CREATE TABLE servers (
    id UUID PRIMARY KEY,
    name VARCHAR(30) NOT NULL
        CONSTRAINT uk_servers_name UNIQUE,
    host VARCHAR(255) NOT NULL,
    ssh_port INTEGER NOT NULL
        CHECK ( ssh_port BETWEEN 1 AND 65535 ),
    ssh_username VARCHAR(30) NOT NULL
);