package com.seonchaksun.event.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Column(name = "current_count", nullable = false)
    private int currentCount;

    @Column(name = "open_at", nullable = false)
    private LocalDateTime openAt;

    @Column(name = "close_at", nullable = false)
    private LocalDateTime closeAt;

    protected Event() {
    }

    private Event(
            String name,
            int capacity,
            LocalDateTime openAt,
            LocalDateTime closeAt
    ) {
        validateName(name);
        validateCapacity(capacity);
        validateEventPeriod(openAt, closeAt);

        this.name = name;
        this.capacity = capacity;
        this.currentCount = 0;
        this.openAt = openAt;
        this.closeAt = closeAt;
    }

    public static Event create(
            String name,
            int capacity,
            LocalDateTime openAt,
            LocalDateTime closeAt
    ) {
        return new Event(name, capacity, openAt, closeAt);
    }

    public void enter(LocalDateTime now) {
        Objects.requireNonNull(now, "현재 시간은 null일 수 없습니다.");

        validateEntryPeriod(now);
        validateCapacityAvailable();

        currentCount++;
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new EventException(
                    "이벤트명은 비어 있을 수 없습니다."
            );
        }
    }

    private void validateCapacity(int capacity) {
        if (capacity < 1) {
            throw new EventException(
                    "이벤트 정원은 1명 이상이어야 합니다."
            );
        }
    }

    private void validateEventPeriod(
            LocalDateTime openAt,
            LocalDateTime closeAt
    ) {
        if (openAt == null || closeAt == null) {
            throw new EventException(
                    "이벤트 시작 시간과 종료 시간은 필수입니다."
            );
        }

        if (!openAt.isBefore(closeAt)) {
            throw new EventException(
                    "이벤트 시작 시간은 종료 시간보다 빨라야 합니다."
            );
        }
    }

    private void validateEntryPeriod(LocalDateTime now) {
        boolean isBeforeOpen = now.isBefore(openAt);
        boolean isAtOrAfterClose = !now.isBefore(closeAt);

        if (isBeforeOpen || isAtOrAfterClose) {
            throw new EventException(
                    "이벤트 신청 기간이 아닙니다."
            );
        }
    }

    private void validateCapacityAvailable() {
        if (currentCount >= capacity) {
            throw new EventException(
                    "이벤트 신청이 마감되었습니다."
            );
        }
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getCurrentCount() {
        return currentCount;
    }

    public LocalDateTime getOpenAt() {
        return openAt;
    }

    public LocalDateTime getCloseAt() {
        return closeAt;
    }
}