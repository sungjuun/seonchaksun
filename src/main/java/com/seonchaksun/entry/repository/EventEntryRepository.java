package com.seonchaksun.entry.repository;

import com.seonchaksun.entry.domain.EventEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventEntryRepository
        extends JpaRepository<EventEntry, Long> {

    boolean existsByEventIdAndUserId(
            Long eventId,
            Long userId
    );

    long countByEventId(Long eventId);
}