package com.seonchaksun.event.repository;

import com.seonchaksun.event.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface EventRepository
        extends JpaRepository<Event, Long> {

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
}