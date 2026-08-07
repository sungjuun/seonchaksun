package com.seonchaksun.entry.service;

import com.seonchaksun.entry.domain.DuplicateEntryException;
import com.seonchaksun.entry.domain.EventEntry;
import com.seonchaksun.entry.dto.EventEntryResponse;
import com.seonchaksun.entry.repository.EventEntryRepository;
import com.seonchaksun.event.domain.Event;
import com.seonchaksun.event.domain.EventNotFoundException;
import com.seonchaksun.event.repository.EventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class EventEntryService {

    private final EventRepository eventRepository;
    private final EventEntryRepository eventEntryRepository;
    private final Clock clock;

    public EventEntryService(
            EventRepository eventRepository,
            EventEntryRepository eventEntryRepository,
            Clock clock
    ) {
        this.eventRepository = eventRepository;
        this.eventEntryRepository =
                eventEntryRepository;
        this.clock = clock;
    }

    @Transactional
    public EventEntryResponse enter(
            Long eventId,
            Long userId
    ) {

        /*
         * 일반적인 중복 요청은 여기서 빠르게 차단한다.
         *
         * 하지만 동시 요청에서는 두 Transaction이
         * 모두 false를 볼 수 있으므로
         * 이것만으로 중복 신청을 완전히 막을 수는 없다.
         */
        validateDuplicateEntry(
                eventId,
                userId
        );

        LocalDateTime now =
                LocalDateTime.now(clock);

        /*
         * 정원 확인 + 증가를 하나의 SQL로 처리한다.
         */
        int updatedCount =
                eventRepository.incrementCurrentCount(
                        eventId,
                        now
                );

        if (updatedCount == 0) {
            throwEntryFailure(
                    eventId,
                    now
            );
        }

        Event event =
                eventRepository.getReferenceById(
                        eventId
                );

        EventEntry entry =
                EventEntry.create(
                        event,
                        userId,
                        now
                );

        EventEntry savedEntry =
                saveEntry(
                        entry,
                        eventId,
                        userId
                );

        return EventEntryResponse.from(
                savedEntry
        );
    }

    private EventEntry saveEntry(
            EventEntry entry,
            Long eventId,
            Long userId
    ) {

        try {

            /*
             * save()가 아니라 saveAndFlush()를 사용한다.
             *
             * UNIQUE(event_id, user_id) 위반을
             * Transaction commit 시점이 아니라
             * 이 메서드 안에서 즉시 감지하기 위함이다.
             */
            return eventEntryRepository
                    .saveAndFlush(entry);

        } catch (
                DataIntegrityViolationException e
        ) {

            /*
             * 같은 Event + User 조합의 동시 신청이
             * DB UNIQUE Constraint에서 막힌 경우
             * 애플리케이션의 의미 있는 비즈니스 예외로 변환한다.
             *
             * 이 예외는 RuntimeException 계열이므로
             * 현재 Transaction 전체가 rollback된다.
             *
             * 따라서 이 요청에서 앞서 수행했던
             * Atomic currentCount 증가도 함께 rollback된다.
             */
            throw new DuplicateEntryException(
                    eventId,
                    userId
            );
        }
    }

    private void validateDuplicateEntry(
            Long eventId,
            Long userId
    ) {

        boolean exists =
                eventEntryRepository
                        .existsByEventIdAndUserId(
                                eventId,
                                userId
                        );

        if (exists) {
            throw new DuplicateEntryException(
                    eventId,
                    userId
            );
        }
    }

    private void throwEntryFailure(
            Long eventId,
            LocalDateTime now
    ) {

        Event event =
                eventRepository
                        .findById(eventId)
                        .orElseThrow(
                                () ->
                                        new EventNotFoundException(
                                                eventId
                                        )
                        );

        /*
         * Atomic Update가 0건이었다면
         *
         * 이벤트 없음
         * 신청 기간 아님
         * 정원 마감
         *
         * 중 하나다.
         *
         * 기존 Event 도메인의 검증 규칙을 재사용한다.
         */
        event.enter(now);

        throw new IllegalStateException(
                "이벤트 신청 실패 원인을 확인할 수 없습니다."
        );
    }
}