package com.seonchaksun.event.domain;

import com.seonchaksun.entry.service.EntryStrategy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

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

    /*
     * 이 이벤트에서 사용할 동시성 처리 전략.
     *
     * 하나의 이벤트는 하나의 전략만 사용하도록 고정해서
     * Redis Counter와 MySQL current_count가 섞이는 문제를 막는다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "strategy", nullable = false, length = 20)
    private EntryStrategy strategy;

    @Column(name = "open_at", nullable = false)
    private LocalDateTime openAt;

    @Column(name = "close_at", nullable = false)
    private LocalDateTime closeAt;

    /*
     * Optimistic Lock을 위한 버전 값.
     *
     * 같은 Event를 여러 트랜잭션이 동시에 수정하면
     * Hibernate가 version을 비교해서 충돌을 감지한다.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Event() {
    }

    private Event(
            String name,
            int capacity,
            EntryStrategy strategy,
            LocalDateTime openAt,
            LocalDateTime closeAt
    ) {
        validateName(name);
        validateCapacity(capacity);
        validateStrategy(strategy);
        validateEventPeriod(
                openAt,
                closeAt
        );

        this.name = name;
        this.capacity = capacity;
        this.currentCount = 0;
        this.strategy = strategy;
        this.openAt = openAt;
        this.closeAt = closeAt;
    }

    public static Event create(
            String name,
            int capacity,
            EntryStrategy strategy,
            LocalDateTime openAt,
            LocalDateTime closeAt
    ) {
        return new Event(
                name,
                capacity,
                strategy,
                openAt,
                closeAt
        );
    }

    /*
     * 기존 테스트/내부 코드 호환용 생성 메서드.
     * 전략을 지정하지 않은 기존 코드는 ATOMIC을 기본값으로 사용한다.
     * 실제 이벤트 생성 API에서는 strategy 입력을 필수로 받는다.
     */
    public static Event create(
            String name,
            int capacity,
            LocalDateTime openAt,
            LocalDateTime closeAt
    ) {
        return create(
                name,
                capacity,
                EntryStrategy.ATOMIC,
                openAt,
                closeAt
        );
    }

    public void enter(
            LocalDateTime now
    ) {
        validateEntryPeriod(now);
        validateCapacityAvailable();

        currentCount++;
    }

    private void validateName(
            String name
    ) {
        if (name == null || name.isBlank()) {
            throw new EventException(
                    "이벤트명은 비어 있을 수 없습니다."
            );
        }
    }

    private void validateCapacity(
            int capacity
    ) {
        if (capacity < 1) {
            throw new EventException(
                    "이벤트 정원은 1명 이상이어야 합니다."
            );
        }
    }

    private void validateStrategy(
            EntryStrategy strategy
    ) {
        if (strategy == null) {
            throw new EventException(
                    "이벤트 처리 전략은 필수입니다."
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

    public void validateEntryPeriod(
            LocalDateTime now
    ) {
        Objects.requireNonNull(
                now,
                "현재 시간은 null일 수 없습니다."
        );

        boolean isBeforeOpen =
                now.isBefore(openAt);

        boolean isAtOrAfterClose =
                !now.isBefore(closeAt);

        if (
                isBeforeOpen
                        || isAtOrAfterClose
        ) {
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

    public EntryStrategy getStrategy() {
        return strategy;
    }

    public LocalDateTime getOpenAt() {
        return openAt;
    }

    public LocalDateTime getCloseAt() {
        return closeAt;
    }

    public Long getVersion() {
        return version;
    }
}
