package com.seonchaksun.entry.service;

import com.seonchaksun.TestcontainersConfiguration;
import com.seonchaksun.entry.dto.EventEntryResponse;
import com.seonchaksun.entry.repository.EventEntryRepository;
import com.seonchaksun.event.domain.Event;
import com.seonchaksun.event.repository.EventRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EventEntryServiceIntegrationTest {

    @Autowired
    private EventEntryService eventEntryService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventEntryRepository eventEntryRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private Clock clock;

    @Test
    @DisplayName("실제 DB에서 이벤트 신청 시 신청 내역과 현재 인원이 함께 증가한다")
    void enterWithDatabase() {
        // given
        when(clock.instant())
                .thenReturn(
                        Instant.parse(
                                "2026-08-10T03:00:00Z"
                        )
                );

        when(clock.getZone())
                .thenReturn(
                        ZoneId.of("Asia/Seoul")
                );

        Event event = Event.create(
                "한정판 키보드 사전예약",
                100,
                java.time.LocalDateTime.of(
                        2026, 8, 10, 10, 0
                ),
                java.time.LocalDateTime.of(
                        2026, 8, 10, 18, 0
                )
        );

        Event savedEvent =
                eventRepository.save(event);

        // when
        EventEntryResponse response =
                eventEntryService.enter(
                        savedEvent.getId(),
                        1001L
                );

        entityManager.clear();

        // then
        Event foundEvent =
                eventRepository
                        .findById(savedEvent.getId())
                        .orElseThrow();

        long entryCount =
                eventEntryRepository
                        .countByEventId(
                                savedEvent.getId()
                        );

        assertThat(response.eventId())
                .isEqualTo(savedEvent.getId());

        assertThat(response.userId())
                .isEqualTo(1001L);

        assertThat(foundEvent.getCurrentCount())
                .isEqualTo(1);

        assertThat(entryCount)
                .isEqualTo(1);
    }
}