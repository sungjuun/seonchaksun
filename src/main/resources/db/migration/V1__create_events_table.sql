CREATE TABLE events
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    name          VARCHAR(100) NOT NULL,
    capacity      INT          NOT NULL,
    current_count INT          NOT NULL DEFAULT 0,
    open_at       DATETIME(6)  NOT NULL,
    close_at      DATETIME(6)  NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT chk_events_capacity_positive
        CHECK (capacity > 0),

    CONSTRAINT chk_events_current_count_non_negative
        CHECK (current_count >= 0),

    CONSTRAINT chk_events_current_count_capacity
        CHECK (current_count <= capacity),

    CONSTRAINT chk_events_period
        CHECK (open_at < close_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;