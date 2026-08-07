package com.seonchaksun.entry.domain;

import com.seonchaksun.event.domain.Event;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "event_entries")
public class EventEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected EventEntry() {
    }

    private EventEntry(
            Event event,
            Long userId,
            LocalDateTime createdAt
    ) {
        validateEvent(event);
        validateUserId(userId);
        validateCreatedAt(createdAt);

        this.event = event;
        this.userId = userId;
        this.createdAt = createdAt;
    }

    public static EventEntry create(
            Event event,
            Long userId,
            LocalDateTime createdAt
    ) {
        return new EventEntry(
                event,
                userId,
                createdAt
        );
    }

    private void validateEvent(Event event) {
        Objects.requireNonNull(
                event,
                "이벤트는 필수입니다."
        );
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId < 1) {
            throw new IllegalArgumentException(
                    "사용자 ID는 1 이상이어야 합니다."
            );
        }
    }

    private void validateCreatedAt(
            LocalDateTime createdAt
    ) {
        Objects.requireNonNull(
                createdAt,
                "신청 시간은 필수입니다."
        );
    }

    public Long getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public Long getUserId() {
        return userId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}