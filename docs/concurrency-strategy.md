# 동시성 전략 설계 문서

## 1. 문서 목적

이 문서는 선착순 프로젝트에서 동시성 문제를 어떻게 재현했고, 어떤 전략을 적용했으며, 각 전략이 어떤 장단점을 보였는지를 기록하기 위한 설계 문서입니다.

프로젝트의 목적은 특정 기술을 정답으로 선택하는 것이 아니라 다음 과정을 실제 코드와 테스트로 검증하는 것입니다.

```text
단순 구현
→ 문제 재현
→ 원인 분석
→ 해결 전략 적용
→ 동일 조건 측정
→ 장애/정합성 한계 분석
```

---

## 2. 기본 요구사항

이벤트의 정원이 100명일 때 200개의 요청이 동시에 들어와도 다음 조건을 만족해야 합니다.

```text
성공 요청 = 100
실패 요청 = 100
실제 저장된 EventEntry = 100
```

또한 동일 사용자가 동시에 여러 번 신청하더라도 한 번만 성공해야 합니다.

```text
동일 사용자 20회 동시 요청
→ 성공 1
→ 중복 실패 19
```

핵심 불변식은 다음과 같습니다.

### DB 기반 전략

```text
Event.currentCount == EventEntry count <= capacity
```

### Redis 전략

```text
Redis count == EventEntry count <= capacity
```

---

## 3. Naive 구현과 문제 재현

최초 구현은 JPA Entity를 조회한 뒤 `currentCount`를 증가시키는 일반적인 Read-Modify-Write 방식이었습니다.

```text
SELECT Event
→ currentCount 확인
→ currentCount + 1
→ Transaction Commit
```

동일 Event에 대해 200개의 요청을 최대 32개 Thread로 실행한 결과:

```text
서비스 성공 수 = 40
서비스 실패 수 = 160
Event.currentCount = 20
실제 EventEntry 수 = 40
```

신청 데이터는 40건 저장되었지만 Event의 카운트는 20이었습니다.

이는 여러 Transaction이 같은 `currentCount` 값을 읽고 각각 증가시킨 뒤 덮어쓰면서 발생한 Lost Update입니다.

테스트 과정에서는 MySQL Error 1213 / SQLState 40001 deadlock도 함께 관찰되었습니다. 다만 정확한 deadlock 원인은 InnoDB deadlock graph를 수집하지 않았기 때문에 특정 쿼리 간 관계로 단정하지 않습니다.

---

## 4. Atomic Update

### 설계

정원 확인과 증가를 하나의 UPDATE로 수행합니다.

```text
UPDATE Event
SET currentCount = currentCount + 1
WHERE id = :eventId
  AND currentCount < capacity
  AND openAt <= now
  AND closeAt > now
```

### 의도

애플리케이션에서 다음 과정을 분리하지 않습니다.

```text
조회
→ 조건 판단
→ 증가
```

대신 DB의 단일 Statement가 조건 확인과 증가를 원자적으로 수행합니다.

### 장점

- 구조가 비교적 단순함
- 별도의 외부 인프라가 필요 없음
- Lost Update 방지 가능
- Retry 로직이 필요하지 않음

### 단점

- 동일 Event row가 계속 갱신되는 hot-row 구조
- 트래픽이 매우 높아질수록 DB row contention 가능
- Bulk Update 사용 시 JPA Persistence Context / @Version 처리에 주의 필요

현재 구현에서는 `@Version` 필드가 존재하므로 JPQL Bulk Update에서 version도 함께 증가시킵니다.

---

## 5. Pessimistic Lock

### 설계

Event를 조회할 때 `PESSIMISTIC_WRITE` Lock을 획득합니다.

```text
SELECT Event FOR UPDATE
→ Lock 획득
→ 신청 처리
→ COMMIT
→ 다음 Transaction 진행
```

### 장점

- 충돌을 사전에 막음
- 비즈니스 로직이 직관적임
- 충돌이 매우 높은 경우 Optimistic Retry보다 효율적일 수 있음

### 단점

- Lock 대기 발생
- Transaction 범위가 길어지면 성능 저하 가능
- DB Connection 점유 시간이 길어질 수 있음

이번 테스트에서는 Critical Section이 매우 짧아 Atomic Update와 유사한 처리 시간을 기록했습니다.

---

## 6. Optimistic Lock

### 설계

Event Entity에 `@Version`을 두고 수정 시 version 충돌을 감지합니다.

충돌한 요청은 별도 Facade에서 재시도합니다.

```text
조회
→ 변경
→ version 충돌
→ rollback
→ random backoff
→ retry
```

### Retry 정책

현재 구현은 다음 예외를 재시도 대상으로 처리합니다.

- `ObjectOptimisticLockingFailureException`
- `CannotAcquireLockException`

Random Backoff를 적용해 재충돌을 완화합니다.

### 장점

- 충돌이 적은 환경에서는 Lock 대기 없이 처리 가능
- Read 비중이 높은 환경에서 유리할 수 있음

### 단점

- hot-row 환경에서는 충돌률 증가
- Retry마다 SELECT / UPDATE / rollback 비용이 다시 발생
- 충돌이 많으면 처리 시간이 크게 늘어남

이번 테스트에서는 네 전략 중 가장 긴 평균 처리 시간을 기록했습니다.

---

## 7. 중복 신청 Race Condition

애플리케이션에서 다음 검증을 하더라도 동시 요청에서는 안전하지 않습니다.

```text
existsByEventIdAndUserId(eventId, userId)
→ false
→ INSERT
```

두 Transaction이 동시에 `false`를 볼 수 있기 때문입니다.

따라서 최종 불변식은 MySQL UNIQUE Constraint가 보장합니다.

```text
UNIQUE(event_id, user_id)
```

현재 서비스는 `save()` 대신 `saveAndFlush()`를 사용해 Constraint 위반을 Transaction commit 이전 서비스 내부에서 감지하고 `DuplicateEntryException`으로 변환합니다.

Atomic Update 전략에서는 중복 INSERT가 실패하면 같은 DB Transaction에 포함된 `currentCount` 증가도 함께 rollback됩니다.

실제 동시성 테스트 결과:

```text
동일 사용자 요청 수 = 20
성공 수 = 1
중복 신청 실패 수 = 19
예상하지 못한 실패 수 = 0
Event.currentCount = 1
실제 EventEntry 수 = 1
```

---

## 8. Redis Lua Script 전략

### 왜 Distributed Lock 대신 Lua Script인가

이 프로젝트의 핵심 요구사항은 하나의 Event에 대해 "남은 정원이 있는지 확인하고 있으면 한 자리를 차지한다"입니다.

이 연산은 다음처럼 표현할 수 있습니다.

```text
GET current
→ current < capacity 확인
→ INCR
```

따라서 Lock을 획득하고 해제하는 구조보다 이 세 작업을 하나의 Redis Lua Script로 묶는 방식이 요구사항에 직접 대응한다고 판단했습니다.

### reserve script

```text
current 조회
→ capacity 이상이면 0 반환
→ 아니면 INCR
→ 1 반환
```

Redis는 Lua Script 실행을 원자적으로 처리하므로 Script 내부의 조회/비교/증가 사이에 다른 요청이 끼어들지 않습니다.

### 단독 Redis 동시성 테스트

```text
요청 수 = 200
정원 = 100
Thread 수 = 32
성공 수 = 100
실패 수 = 100
Redis count = 100
처리 시간 = 398 ms
```

이 수치는 Redis 정원 예약만 측정한 값이므로 DB 전략과 직접 비교하지 않습니다.

---

## 9. Redis + MySQL 통합 전략

Redis 전략에서는 역할을 다음처럼 분리합니다.

```text
Redis
→ 정원 Admission Control

MySQL event_entries
→ 실제 신청 내역 영속화
```

현재 실험에서는 Redis를 정원 카운트의 기준으로 사용하며 `events.current_count`는 Redis 전략에서 증가시키지 않습니다.

정합성 검증 기준은 다음과 같습니다.

```text
Redis count == EventEntry count
```

### 정상 처리

```text
Event 조회
→ 중복 신청 사전 확인
→ 신청 기간 확인
→ Redis reserve
→ EventEntry INSERT
→ 성공
```

---

## 10. Redis + MySQL Dual Write 문제

Redis와 MySQL은 동일한 MySQL `@Transactional` 범위에서 함께 rollback되지 않습니다.

문제 상황:

```text
Redis reserve 성공
0 → 1

MySQL INSERT 실패

Redis는 그대로 1
```

이 상태를 방치하면 실제 신청자 수보다 Redis 카운트가 커지고 사용 가능한 자리가 사라집니다.

### 현재 보상 처리

DB 저장에 실패하면 Redis Counter를 다시 감소시킵니다.

```text
Redis reserve
→ DB saveAndFlush

성공
→ 종료

DB 실패
→ Redis release
→ 예외 반환
```

`release` 역시 Lua Script로 처리합니다.

```text
현재 count 조회
→ 0 이하면 실패
→ DECR
```

### 동일 사용자 중복 Race 결과

```text
동일 사용자 요청 수 = 20
성공 수 = 1
중복 신청 실패 수 = 19
예상하지 못한 실패 수 = 0
Redis count = 1
실제 EventEntry 수 = 1
```

DB UNIQUE 위반으로 실패한 19개 요청의 Redis 예약이 보상되어 최종적으로 Redis와 MySQL이 모두 1을 유지했습니다.

---

## 11. Redis + MySQL 정원 동시성 검증

서로 다른 사용자 200명이 정원 100명 Event에 신청했습니다.

```text
요청 수 = 200
정원 = 100
최대 동시 Thread 수 = 32
성공 수 = 100
정원 초과 실패 수 = 100
예상하지 못한 실패 수 = 0
Redis count = 100
실제 EventEntry 수 = 100
처리 시간 = 877 ms
```

정확성 측면에서 다음 불변식을 만족했습니다.

```text
성공 수 == Redis count == EventEntry count == capacity
```

단일 877ms 결과는 warm-up과 실행 편차가 있기 때문에 최종 성능 결론에는 사용하지 않고, 동일 Harness에서 5회 반복 측정한 결과를 사용합니다.

---

## 12. 동일 조건 성능 비교

### 측정 조건

```text
요청 Task = 200
정원 = 100
최대 동시 Worker Thread = 32
전략별 측정 횟수 = 5
```

`200 threads`가 아니라 **200개의 요청 task를 최대 32개의 worker thread로 처리**한 테스트입니다.

각 회차마다 새로운 Event를 생성하며 모든 전략에서 `startLatch.countDown()` 직전부터 측정합니다.

성능 측정과 동시에 각 전략의 정합성도 검증합니다.

### 결과

| Strategy | Average | Min | Max |
|---|---:|---:|---:|
| Redis + MySQL | **183.40 ms** | 153 ms | 231 ms |
| Pessimistic Lock | 670.60 ms | 634 ms | 759 ms |
| Atomic Update | 683.60 ms | 568 ms | 920 ms |
| Optimistic Lock | 1096.40 ms | 992 ms | 1196 ms |

### 해석

#### Atomic vs Pessimistic

두 전략은 이번 조건에서 거의 유사한 평균 시간을 기록했습니다.

따라서 "Pessimistic Lock은 항상 느리다"라고 판단하지 않습니다.

이번 Critical Section이 짧기 때문에 명시적인 Lock이 있어도 대기 시간이 크게 늘어나지 않은 것으로 볼 수 있습니다.

#### Optimistic

높은 충돌 환경에서 rollback / retry / backoff 비용이 누적되면서 가장 긴 처리 시간을 기록했습니다.

테스트 중 MySQL 1213 deadlock도 반복 관찰되었으며 현재 구현은 해당 DB Lock 충돌도 Retry 대상으로 처리합니다.

#### Redis + MySQL

이번 로컬 통합 테스트에서는 가장 짧은 평균 처리 시간을 기록했습니다.

DB 기반 전략은 모든 성공 요청이 동일한 `events.current_count` row를 갱신합니다.

Redis 전략에서는 정원 경쟁을 Redis Lua Script로 옮기고 MySQL에는 서로 다른 `event_entries` row를 INSERT합니다.

즉, hot-row write contention을 줄인 것이 이번 결과에 영향을 준 것으로 해석합니다.

---

## 13. 성능 결과를 해석할 때의 제한사항

이번 테스트는 JMH나 실제 운영 성능 테스트가 아닙니다.

정확한 표현은 **integration-level concurrency comparison**입니다.

아직 포함되지 않은 요소:

- HTTP Server 처리 비용
- JSON Serialization / Deserialization
- 실제 Client → Server Network RTT
- 다중 Spring Boot Instance
- 운영 수준 Redis/MySQL Network 분리
- Connection Pool tuning
- Redis 장애 / failover
- DB replication
- CPU / Memory resource limit
- TPS, p95, p99 응답시간

따라서 결과를 일반화해 "Redis가 항상 N배 빠르다"고 표현하지 않습니다.

현재 환경과 현재 요구사항에서 관찰된 결과로 한정합니다.

---

## 14. Redis 보상 처리의 남은 한계

현재 서비스 내부의 `saveAndFlush()` 실패는 catch해서 Redis 보상을 수행할 수 있습니다.

그러나 다음 시나리오는 더 어렵습니다.

```text
Redis reserve 성공
→ DB INSERT / flush 성공
→ Service return
→ Spring Transaction Commit 시점 장애
```

실제 DB commit은 `@Transactional` Proxy가 서비스 메서드 반환 이후 수행할 수 있기 때문에 서비스 메서드 내부 catch만으로 commit 자체의 실패를 모두 처리한다고 보장할 수 없습니다.

또한:

```text
DB 실패
→ Redis release 시도
→ Redis 장애
```

처럼 보상 자체가 실패할 수도 있습니다.

따라서 현재 구현은 실험을 위한 보상 처리이며 완전한 Distributed Transaction 해결책은 아닙니다.

운영 수준으로 확장한다면 다음 방법을 추가 검토할 수 있습니다.

- TransactionSynchronization을 통한 commit/rollback callback
- 재처리 가능한 Compensation Queue
- Outbox 기반 상태 변경 기록
- 주기적인 Redis/MySQL Reconciliation
- Redis reservation에 TTL / 상태 모델 도입
- 장애 발생 시 운영 복구 절차

각 방법은 추가 복잡도를 만들기 때문에 실제 요구사항과 장애 허용 수준에 따라 선택해야 합니다.

---

## 15. 현재 기술적 결론

### Atomic Update

단순하고 외부 인프라가 필요 없으며 현재 규모에서 충분히 좋은 선택입니다.

### Pessimistic Lock

높은 경합 환경에서도 예측 가능한 방식이며 Transaction이 짧다면 반드시 느린 전략이라고 볼 수 없습니다.

### Optimistic Lock

충돌이 적은 경우 좋은 선택일 수 있지만, 하나의 hot-row에 요청이 집중되는 선착순 이벤트에서는 Retry 비용이 크게 나타났습니다.

### Redis Lua Script

이번 실험에서는 가장 짧은 처리 시간을 기록했으며 DB hot-row contention을 줄일 수 있었습니다.

하지만 다음 비용을 새롭게 만듭니다.

```text
Redis 운영
Dual Write
Compensation
장애 복구
Source of Truth 결정
```

따라서 최종 전략은 단순히 가장 빠른 수치를 기준으로 선택하지 않습니다.

핵심 판단 기준은 다음과 같습니다.

```text
정확성
+ 처리량
+ 구현 복잡도
+ 장애 복구 난이도
+ 운영 비용
```

---

## 16. 다음 검증 계획

1. Redis 예약 성공 후 DB 실패 보상 테스트를 명시적으로 유지
2. 보상 실패 / commit 시점 실패 시나리오 설계
3. HTTP API에서 전략 선택 가능하도록 테스트 Endpoint 또는 구조 정리
4. k6 부하 테스트
5. TPS / avg / p95 / p99 측정
6. 로컬 통합 테스트 결과와 HTTP 부하 테스트 결과 비교
7. 최종 전략 선택 및 README 결론 갱신

---

## 17. 인터뷰 설명 요약

프로젝트를 설명할 때 핵심 흐름은 다음과 같습니다.

> 최초에는 일반적인 JPA Read-Modify-Write 방식으로 구현했습니다. 200개의 요청 task를 최대 32개의 thread로 실행했을 때 실제 신청 성공은 40건인데 Event currentCount는 20으로 남아 Lost Update를 재현했습니다. 이후 Atomic Update, Pessimistic Lock, Optimistic Lock + Retry를 구현하고 동일 Harness에서 비교했습니다. 중복 신청 Race도 별도로 재현해 DB UNIQUE Constraint를 최종 방어선으로 두었습니다. 이후 Redis Lua Script로 정원 Admission Control을 분리했고, DB 저장 실패 시 Redis 예약을 되돌리는 보상 로직을 구현했습니다. 동일 조건 5회 측정에서는 Redis + MySQL이 평균 183.4ms로 가장 짧았지만 Redis/MySQL Dual Write와 commit 시점 장애 같은 새로운 정합성 문제가 생긴다는 점까지 확인했습니다. 따라서 성능 수치만이 아니라 장애 복구와 운영 복잡도를 포함해 최종 전략을 판단하고 있습니다.
