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
     * Atomic Update
     */
    @Modifying(
            flushAutomatically = true,
            clearAutomatically = true
    )
    @Query("""
            UPDATE Event e
               SET e.currentCount = e.currentCount + 1,
                   e.version = e.version + 1
             WHERE e.id = :eventId
               AND e.currentCount < e.capacity
               AND e.openAt <= :now
               AND e.closeAt > :now
            """)
    int incrementCurrentCount(
            @Param("eventId")
            Long eventId,

            @Param("now")
            LocalDateTime now
    );

    /*
     * Pessimistic Lock
     */
    @Lock(
            LockModeType.PESSIMISTIC_WRITE
    )
    @Query("""
            SELECT e
              FROM Event e
             WHERE e.id = :eventId
            """)
    Optional<Event>
    findByIdWithPessimisticLock(
            @Param("eventId")
            Long eventId
    );
}