package com.seonchaksun.event.repository;

import com.seonchaksun.event.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {
}