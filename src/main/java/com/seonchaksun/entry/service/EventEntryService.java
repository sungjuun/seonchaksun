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
         *
         * current_count < capacity 조건까지
         * UPDATE 문 안에서 평가하기 때문에
         * 여러 요청이 동시에 들어와도
         * 정원을 초과해서 증가하지 않는다.
         */
        int updatedCount =
                eventRepository.incrementCurrentCount(
                        eventId,
                        now
                );

        /*
         * UPDATE 0건이면
         *
         * 1. 존재하지 않는 Event
         * 2. 신청 기간이 아닌 Event
         * 3. 이미 정원이 찬 Event
         *
         * 중 하나다.
         */
        if (updatedCount == 0) {
            throwEntryFailure(
                    eventId,
                    now
            );
        }

        /*
         * Atomic Update 성공 후
         * EventEntry FK 설정을 위해 reference만 얻는다.
         *
         * 이미 Event 존재 여부와 신청 가능 여부는
         * Atomic UPDATE 과정에서 검증됐다.
         */
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
             * 이 메서드 안에서 즉시 감지한다.
             */
            return eventEntryRepository
                    .saveAndFlush(entry);

        } catch (
                DataIntegrityViolationException e
        ) {

            /*
             * 같은 Event + User 조합의 동시 신청이
             * DB UNIQUE Constraint에서 막힌 경우
             * 애플리케이션의 의미 있는
             * 비즈니스 예외로 변환한다.
             *
             * RuntimeException이므로
             * 현재 Transaction 전체가 rollback된다.
             *
             * 따라서 앞서 성공했던
             * Atomic currentCount 증가 역시
             * 함께 rollback된다.
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

        /*
         * 여기서 일반 findById()를 사용하면 안 된다.
         *
         * MySQL REPEATABLE_READ에서는
         * 일반 SELECT가 Transaction의 이전 snapshot을
         * 계속 바라볼 수 있다.
         *
         * 반면 바로 앞의 Atomic UPDATE는
         * 최신 row 상태를 기준으로 수행될 수 있다.
         *
         * 그 결과:
         *
         * Atomic UPDATE에서는
         * current_count = capacity를 확인해서
         * 0건이 반환됐지만,
         *
         * 일반 findById()에서는
         * 예전 current_count 값을 읽는 상황이
         * 발생할 수 있다.
         *
         * 따라서 실패 원인 판별에서는
         * Locking Read를 사용하여
         * 최신 DB 상태를 확인한다.
         */
        Event event =
                eventRepository
                        .findByIdWithPessimisticLock(
                                eventId
                        )
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
         * 중 하나여야 한다.
         *
         * 최신 Event 상태를 기준으로
         * 기존 도메인 검증 규칙을 재사용한다.
         *
         * 정상적인 실패라면
         * EventException이 여기에서 발생한다.
         */
        event.enter(now);

        /*
         * 이 지점까지 도달한다면
         * Atomic UPDATE는 실패했는데
         * 최신 Event 상태에서는
         * 신청 가능한 상태였다는 뜻이다.
         *
         * 정상적인 흐름에서는 발생하면 안 되므로
         * 예상하지 못한 상태로 처리한다.
         *
         * RuntimeException으로 Transaction이
         * rollback되므로 event.enter()가
         * 메모리상 currentCount를 변경했더라도
         * DB에는 반영되지 않는다.
         */
        throw new IllegalStateException(
                "이벤트 신청 실패 원인을 확인할 수 없습니다."
        );
    }
}