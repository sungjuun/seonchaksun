package com.seonchaksun.event.repository;

import com.seonchaksun.event.domain.Event;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EventRepository
        extends JpaRepository<Event, Long> {

    /*
     * Atomic Update 방식
     */
    @Modifying(
            flushAutomatically = true,
            clearAutomatically = true
    )
    @Query("""
            UPDATE Event e
               SET e.currentCount = e.currentCount + 1
             WHERE e.id = :eventId
               AND e.currentCount < e.capacity
               AND e.openAt <= :now
               AND e.closeAt > :now
            """)
    int incrementCurrentCount(
            @Param("eventId") Long eventId,
            @Param("now") LocalDateTime now
    );

    /*
     * Pessimistic Lock 방식
     *
     * 조회하는 순간 해당 Event row에
     * 배타적 락(PESSIMISTIC_WRITE)을 획득한다.
     *
     * 트랜잭션이 끝날 때까지 다른 트랜잭션은
     * 같은 Event row를 수정하기 위해 기다려야 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT e
              FROM Event e
             WHERE e.id = :eventId
            """)
    Optional<Event> findByIdWithPessimisticLock(
            @Param("eventId") Long eventId
    );
}