CREATE TABLE event_entries
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    event_id   BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT fk_event_entries_event
        FOREIGN KEY (event_id)
            REFERENCES events (id),

    CONSTRAINT uk_event_entries_event_user
        UNIQUE (event_id, user_id),

    CONSTRAINT chk_event_entries_user_id_positive
        CHECK (user_id > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;