package com.seonchaksun.entry.repository;

import com.seonchaksun.TestcontainersConfiguration;
import com.seonchaksun.entry.domain.EventEntry;
import com.seonchaksun.event.domain.Event;
import com.seonchaksun.event.repository.EventRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class EventEntryRepositoryTest {

    @Autowired
    private EventEntryRepository eventEntryRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("이벤트 신청 내역을 저장하고 조회한다")
    void saveEventEntry() {
        LocalDateTime openAt =
                LocalDateTime.of(
                        2026, 8, 10, 10, 0
                );

        LocalDateTime closeAt =
                LocalDateTime.of(
                        2026, 8, 10, 18, 0
                );

        LocalDateTime entryAt =
                LocalDateTime.of(
                        2026, 8, 10, 12, 0
                );

        Event event = Event.create(
                "한정판 키보드 사전예약",
                100,
                openAt,
                closeAt
        );

        Event savedEvent =
                eventRepository.save(event);

        EventEntry entry =
                EventEntry.create(
                        savedEvent,
                        1001L,
                        entryAt
                );

        EventEntry savedEntry =
                eventEntryRepository.save(entry);

        entityManager.flush();
        entityManager.clear();

        EventEntry foundEntry =
                eventEntryRepository
                        .findById(savedEntry.getId())
                        .orElseThrow();

        assertThat(foundEntry.getUserId())
                .isEqualTo(1001L);

        assertThat(foundEntry.getCreatedAt())
                .isEqualTo(entryAt);

        assertThat(
                foundEntry.getEvent().getId()
        ).isEqualTo(savedEvent.getId());
    }

    @Test
    @DisplayName("동일 이벤트에 특정 사용자가 신청했는지 확인한다")
    void existsEventEntry() {
        LocalDateTime openAt =
                LocalDateTime.of(
                        2026, 8, 10, 10, 0
                );

        LocalDateTime closeAt =
                LocalDateTime.of(
                        2026, 8, 10, 18, 0
                );

        LocalDateTime entryAt =
                LocalDateTime.of(
                        2026, 8, 10, 12, 0
                );

        Event event = eventRepository.save(
                Event.create(
                        "한정판 키보드 사전예약",
                        100,
                        openAt,
                        closeAt
                )
        );

        eventEntryRepository.save(
                EventEntry.create(
                        event,
                        1001L,
                        entryAt
                )
        );

        entityManager.flush();
        entityManager.clear();

        boolean exists =
                eventEntryRepository
                        .existsByEventIdAndUserId(
                                event.getId(),
                                1001L
                        );

        assertThat(exists).isTrue();
    }
}