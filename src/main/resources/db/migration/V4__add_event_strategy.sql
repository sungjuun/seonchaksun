ALTER TABLE events
    ADD COLUMN strategy VARCHAR(20) NOT NULL DEFAULT 'ATOMIC' AFTER current_count;

ALTER TABLE events
    ADD CONSTRAINT chk_events_strategy
        CHECK (strategy IN ('ATOMIC', 'PESSIMISTIC', 'OPTIMISTIC', 'REDIS'));
