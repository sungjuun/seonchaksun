package com.seonchaksun.event.repository;

import com.seonchaksun.TestcontainersConfiguration;
import com.seonchaksun.event.domain.Event;
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
class EventRepositoryTest {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("이벤트를 저장하고 ID로 다시 조회한다")
    void saveAndFindEvent() {
        LocalDateTime openAt =
                LocalDateTime.of(2026, 8, 10, 10, 0);

        LocalDateTime closeAt =
                LocalDateTime.of(2026, 8, 10, 18, 0);

        Event event = Event.create(
                "한정판 키보드 사전예약",
                100,
                openAt,
                closeAt
        );

        Event savedEvent = eventRepository.save(event);

        entityManager.flush();
        entityManager.clear();

        Event foundEvent = eventRepository.findById(savedEvent.getId())
                .orElseThrow();

        assertThat(foundEvent.getId())
                .isEqualTo(savedEvent.getId());

        assertThat(foundEvent.getName())
                .isEqualTo("한정판 키보드 사전예약");

        assertThat(foundEvent.getCapacity())
                .isEqualTo(100);

        assertThat(foundEvent.getCurrentCount())
                .isZero();

        assertThat(foundEvent.getOpenAt())
                .isEqualTo(openAt);

        assertThat(foundEvent.getCloseAt())
                .isEqualTo(closeAt);
    }
}