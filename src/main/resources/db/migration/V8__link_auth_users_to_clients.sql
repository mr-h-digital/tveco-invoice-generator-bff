ALTER TABLE app_users
    ADD COLUMN client_id UUID;

ALTER TABLE app_users
    ADD CONSTRAINT fk_app_users_client
        FOREIGN KEY (client_id)
        REFERENCES clients (id)
        ON DELETE SET NULL;

CREATE INDEX idx_app_users_client_id ON app_users (client_id);
