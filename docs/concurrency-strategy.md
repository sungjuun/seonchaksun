# 동시성 전략 설계 문서

## 1. 문서 목적

이 문서는 `선착순` 프로젝트에서 동시성 문제를 어떻게 재현했고,
어떤 전략을 적용했으며,
각 전략이 어떤 특성과 한계를 보였는지를 기록하기 위한 기술 문서입니다.

프로젝트의 목적은 특정 기술을 정답으로 선택하는 것이 아닙니다.

다음 과정을 실제 코드와 테스트를 통해 반복했습니다.

```text
단순 구현
→ 문제 재현
→ 원인 분석
→ 해결 전략 적용
→ 정합성 검증
→ 성능 측정
→ 장애 발견
→ 개선
→ 재측정
```

---

# 2. 핵심 요구사항

정원이 100명인 이벤트에 200개의 요청이 몰리더라도
정확하게 100명만 신청에 성공해야 합니다.

```text
요청 수 = 200
정원 = 100

성공 = 100
정원 초과 실패 = 100
실제 저장된 EventEntry = 100
```

또한 동일 사용자가 동시에 여러 번 신청하더라도
최종적으로 한 번만 성공해야 합니다.

```text
동일 사용자 20회 동시 요청

→ 성공 = 1
→ 중복 실패 = 19
→ EventEntry = 1
```

---

## 2.1 핵심 불변식

DB 기반 전략의 정합성 기준:

```text
Success
==
Event.currentCount
==
EventEntry count
<=
capacity
```

Redis 전략의 정합성 기준:

```text
Success
==
Redis count
==
EventEntry count
<=
capacity
```

Redis 전략에서는 `events.current_count`를 정원 기준으로 사용하지 않습니다.

---

# 3. Naive 구현과 문제 재현

최초 구현은 JPA Entity를 조회한 뒤
애플리케이션에서 `currentCount`를 증가시키는 일반적인
Read-Modify-Write 방식이었습니다.

```text
SELECT Event
→ currentCount 확인
→ currentCount + 1
→ EventEntry 저장
→ Transaction Commit
```

테스트 조건:

```text
요청 Task = 200
정원 = 100
최대 Worker Thread = 32
```

결과:

```text
서비스 성공 수 = 40
서비스 실패 수 = 160

Event.currentCount = 20
EventEntry count = 40
```

실제 신청 데이터는 40건 저장되었지만
이벤트 카운트는 20만 증가했습니다.

---

## 3.1 Lost Update

여러 Transaction이 동시에 동일한 값을 읽었습니다.

예를 들어:

```text
Transaction A
currentCount = 10 조회

Transaction B
currentCount = 10 조회
```

두 Transaction 모두 각각:

```text
10 + 1 = 11
```

을 저장하면 최종 결과는:

```text
11
```

입니다.

실제로는 두 요청이 성공했으므로:

```text
12
```

가 되어야 합니다.

즉 다른 Transaction의 변경 결과를 덮어쓰는
**Lost Update**가 발생했습니다.

---

## 3.2 Deadlock 관찰

동시성 테스트 과정에서는 다음 MySQL 오류도 관찰했습니다.

```text
MySQL Error 1213
SQLState 40001
Deadlock found when trying to get lock
```

다만 InnoDB Deadlock Graph를 별도로 수집하지 않았기 때문에
정확히 어떤 Lock 순서와 쿼리 조합 때문에 발생했는지는 단정하지 않습니다.

따라서 이 프로젝트에서는:

```text
Deadlock이 관찰되었다
```

까지만 기록합니다.

---

# 4. Atomic Update

## 4.1 설계

정원 확인과 신청 인원 증가를 하나의 UPDATE Statement에서 처리합니다.

현재 구현의 핵심 JPQL은 다음과 같습니다.

```java
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
        Long eventId,
        LocalDateTime now
);
```

기존:

```text
SELECT
→ 조건 판단
→ UPDATE
```

Atomic Update:

```text
조건 판단 + UPDATE
→ 단일 Statement
```

DB가 직접 정원 조건을 확인하면서 값을 변경하도록 했습니다.

---

## 4.2 왜 version도 직접 증가시키는가

Entity에 `@Version`이 존재하지만 JPQL Bulk Update는
일반적인 JPA Dirty Checking을 거치지 않습니다.

따라서:

```text
@Version
```

값 역시 자동으로 증가하지 않습니다.

현재 구현에서는 Bulk Update 쿼리 안에서:

```text
version = version + 1
```

을 명시적으로 처리합니다.

---

## 4.3 장점

- 구조가 비교적 단순
- 별도의 외부 인프라가 필요 없음
- Lost Update 방지
- Application Retry 불필요
- DB가 원자적으로 정원 조건을 판별

---

## 4.4 단점

모든 성공 요청이 같은 Event row를 갱신합니다.

```text
events.id = 15
```

하나에 수많은 쓰기가 집중되면:

```text
Hot Row Contention
```

이 발생할 수 있습니다.

트래픽이 증가할수록 DB row 경합이 병목이 될 가능성이 있습니다.

---

# 5. Pessimistic Lock

## 5.1 설계

Event 조회 시 `PESSIMISTIC_WRITE` Lock을 획득합니다.

```text
SELECT Event FOR UPDATE

→ Lock 획득
→ 정원 확인
→ Event 변경
→ EventEntry 저장
→ COMMIT
→ Lock 해제
```

Repository에서는 JPA의:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
```

를 사용합니다.

---

## 5.2 특징

먼저 Lock을 획득한 Transaction이 작업을 완료할 때까지
다른 Transaction은 동일 row를 수정하지 못합니다.

즉 충돌이 발생한 뒤 처리하는 것이 아니라
충돌 자체를 사전에 직렬화합니다.

---

## 5.3 장점

- 동작이 직관적
- 충돌을 사전에 차단
- Retry 로직이 필요하지 않음
- 높은 충돌 환경에서 안정적일 수 있음

---

## 5.4 단점

- Lock 대기 발생
- Transaction 시간이 길어지면 대기 증가
- DB Connection 점유 시간 증가 가능
- Lock 범위와 Transaction 범위를 신중하게 관리해야 함

이번 프로젝트에서는 Critical Section을 짧게 유지했습니다.

---

# 6. Optimistic Lock

## 6.1 설계

Event Entity에:

```java
@Version
```

필드를 추가했습니다.

처리 과정:

```text
Event 조회

↓

비즈니스 로직 수행

↓

UPDATE
WHERE id = ?
AND version = ?

↓

version 불일치

↓

Optimistic Lock Exception

↓

Rollback

↓

Backoff

↓

Retry
```

---

## 6.2 Retry 구조

한 Transaction 안에서 실패 후 계속 재시도하지 않습니다.

별도 Transaction을 수행하는 Worker와
Retry를 담당하는 Facade를 분리했습니다.

```text
Facade
   ↓
Worker
   ↓
새 Transaction
```

Retry 대상:

```text
ObjectOptimisticLockingFailureException
CannotAcquireLockException
```

최대 Retry:

```text
100회
```

Random Backoff:

```text
1 ~ 5 ms
```

를 적용했습니다.

---

## 6.3 장점

- 충돌이 적으면 Lock 대기 없이 처리 가능
- Read 비중이 높은 환경에서 유리할 수 있음
- DB row를 장시간 Lock하지 않음

---

## 6.4 단점

하나의 Event에 요청이 집중되면:

```text
조회
→ 수정
→ 충돌
→ Rollback
→ Retry
```

가 반복됩니다.

따라서 높은 Hot Row Contention에서는
Retry 비용이 크게 증가할 수 있습니다.

이번 실험에서도 네 전략 중 가장 높은 Tail Latency를 기록했습니다.

---

# 7. 중복 신청 Race Condition

동시성 제어를 정원에만 적용한다고 문제가 끝나지 않습니다.

다음 사전 검증이 존재한다고 가정합니다.

```text
existsByEventIdAndUserId(eventId, userId)
```

일반적으로:

```text
exists
→ false
→ INSERT
```

를 수행할 수 있습니다.

하지만 두 Transaction이 동시에 조회하면:

```text
Transaction A → false
Transaction B → false
```

가 가능합니다.

따라서 애플리케이션 레벨의 사전 조회만으로
동시 중복 신청을 완전히 방지할 수 없습니다.

---

## 7.1 DB UNIQUE Constraint

최종 불변식은 DB가 보장하도록 했습니다.

```text
UNIQUE(event_id, user_id)
```

즉:

```text
Application validation
+
Database UNIQUE Constraint
```

구조입니다.

---

## 7.2 saveAndFlush

현재 구현에서는 단순:

```java
save()
```

가 아니라:

```java
saveAndFlush()
```

를 사용합니다.

이유는 UNIQUE Constraint 위반을
Service method가 종료된 이후가 아니라
Service 내부에서 빠르게 확인하기 위해서입니다.

이를 통해:

```text
DataIntegrityViolationException
```

을 서비스에서 감지하고:

```text
DuplicateEntryException
```

으로 변환합니다.

---

## 7.3 동일 사용자 테스트 결과

동일 사용자로 20개의 동시 요청을 보냈습니다.

```text
요청 = 20

성공 = 1
중복 실패 = 19
예상하지 못한 실패 = 0
```

DB 기반 전략:

```text
Event.currentCount = 1
EventEntry count = 1
```

Redis 전략:

```text
Redis count = 1
EventEntry count = 1
```

을 확인했습니다.

---

# 8. Redis Lua Script 전략

## 8.1 왜 Distributed Lock이 아닌가

이 프로젝트의 정원 처리 요구사항은 단순합니다.

```text
현재 신청자 수를 조회한다
→ 정원이 남았는지 확인한다
→ 남았다면 한 자리를 차지한다
```

이를 Redis로 표현하면:

```text
GET
→ 비교
→ INCR
```

입니다.

이 연산을 위해 반드시:

```text
Lock 획득
→ 처리
→ Lock 해제
```

구조를 사용할 필요는 없다고 판단했습니다.

Redis Lua Script를 사용하면
조회 / 비교 / 증가를 하나의 원자적 연산으로 실행할 수 있습니다.

---

## 8.2 Reserve Lua Script

```lua
local current = tonumber(redis.call('GET', KEYS[1]) or '0')
local capacity = tonumber(ARGV[1])

if current >= capacity then
    return 0
end

redis.call('INCR', KEYS[1])

return 1
```

동작:

```text
현재 count 조회

↓

capacity 이상?

YES
→ 0 반환

NO
→ INCR
→ 1 반환
```

Redis는 Lua Script를 원자적으로 실행하므로
Script 실행 중 다른 요청이 중간에 끼어들지 않습니다.

---

# 9. Redis 단독 정원 테스트

Redis Counter 예약만 단독으로 테스트했습니다.

```text
요청 수 = 200
정원 = 100
Worker Thread = 32

성공 = 100
실패 = 100

Redis count = 100

처리 시간 = 398 ms
```

이 결과는 Redis 예약만 측정한 것이므로
DB 기반 전략과 직접적인 성능 비교에는 사용하지 않습니다.

---

# 10. Redis + MySQL 통합 전략

Redis 전략에서는 역할을 분리했습니다.

```text
Redis
→ 정원 Admission Control

MySQL
→ 실제 EventEntry 저장
```

신청 과정:

```text
Event 조회

↓

중복 사용자 사전 확인

↓

현재 시간 확인

↓

신청 기간 검증

↓

Redis reserve

↓

자리가 없으면 실패

↓

EventEntry saveAndFlush

↓

성공
```

---

## 10.1 Count Source

Atomic / Pessimistic / Optimistic:

```text
events.current_count
```

Redis:

```text
Redis Counter
```

즉 Redis 전략에서는:

```text
events.current_count
```

를 증가시키지 않습니다.

따라서 Redis 전략 정합성은:

```text
Redis count == EventEntry count
```

으로 확인합니다.

React Demo UI에서도 선택한 전략에 따라:

```text
MYSQL
REDIS
```

Count Source를 별도로 표시합니다.

---

# 11. Redis + MySQL Dual Write

Redis와 MySQL은 동일한 MySQL Transaction으로 묶이지 않습니다.

예:

```text
Redis reserve 성공

0 → 1

↓

MySQL EventEntry INSERT 실패

↓

Redis Counter = 1
EventEntry = 0
```

이 상태가 유지되면:

```text
실제 사용자보다 Redis count가 큼
```

이 되고 사용 가능한 정원이 잘못 감소합니다.

---

# 12. Redis 보상 처리

DB 저장에 실패하면
Redis에서 확보했던 자리를 반환합니다.

```text
Redis reserve

↓

DB saveAndFlush

├─ 성공
│   └─ 정상 종료
│
└─ 실패
    ↓
Redis release
    ↓
예외 반환
```

release 역시 Lua Script를 이용합니다.

개념적으로:

```text
현재 count 조회
→ 0보다 크면 DECR
```

합니다.

---

## 12.1 DB 저장 실패 테스트

Mock을 사용해 DB 저장 실패를 발생시켰습니다.

```text
Redis reserve 성공

↓

DB 저장 실패

↓

Redis release
```

최종 결과:

```text
Redis count = 0
```

을 확인했습니다.

---

## 12.2 중복 요청과 Redis 보상

동일 사용자가 동시에 20번 요청:

```text
성공 = 1
중복 실패 = 19
```

DB UNIQUE Constraint에서 실패한 요청들은
이미 확보한 Redis 정원을 다시 반환합니다.

최종:

```text
Redis count = 1
EventEntry count = 1
```

을 유지했습니다.

---

# 13. Redis 보상 처리의 한계

현재 구조에도 남아 있는 문제가 있습니다.

Service 내부에서는:

```java
saveAndFlush()
```

까지 성공했지만
실제 Transaction Commit은 Service method가 반환된 이후
Spring Transaction Proxy에서 수행될 수 있습니다.

따라서:

```text
Redis reserve 성공

↓

saveAndFlush 성공

↓

Service 반환

↓

실제 DB COMMIT 실패
```

상황에서는 Service 내부 `catch`가
실패를 감지하지 못할 가능성이 있습니다.

---

## 13.1 보상 자체가 실패하는 경우

다음 상황도 존재합니다.

```text
DB INSERT 실패

↓

Redis release 수행

↓

Redis 장애

↓

보상 실패
```

따라서 현재 Compensation은
가능한 불일치를 줄이는 구조이지
분산 트랜잭션을 완전히 해결하는 것은 아닙니다.

---

## 13.2 향후 확장 가능성

필요한 시스템이라면 다음 방식들을 검토할 수 있습니다.

```text
TransactionSynchronization
Retry Queue
Reconciliation
Outbox Pattern
주기적 Redis / DB 정합성 검사
```

XA / 2PC는 현재 프로젝트 요구사항에서는
복잡도가 과도하다고 판단해 적용하지 않았습니다.

---

# 14. Redis + MySQL 정원 동시성 검증

서로 다른 사용자 200명이
정원 100명 이벤트에 신청했습니다.

```text
요청 수 = 200
정원 = 100
Worker Thread = 32

성공 = 100
정원 초과 실패 = 100
예상하지 못한 실패 = 0

Redis count = 100
EventEntry count = 100

처리 시간 = 877 ms
```

정합성:

```text
Success
==
Redis count
==
EventEntry count
==
capacity
```

을 만족했습니다.

단일 실행의 `877 ms` 값은
Warm-up 및 실행 환경에 따른 편차가 있기 때문에
최종 성능 비교 결과에는 사용하지 않았습니다.

---

# 15. Integration Test 기준 성능 비교

## 15.1 조건

```text
요청 Task = 200
정원 = 100
Worker Thread = 32
전략별 반복 = 5회
```

각 회차마다 새로운 Event를 생성했습니다.

측정과 함께:

```text
성공 수
실패 수
Count
EventEntry
```

정합성도 함께 검증했습니다.

---

## 15.2 결과

| Strategy | Average | Min | Max |
|---|---:|---:|---:|
| Redis + MySQL | **183.40 ms** | 153 ms | 231 ms |
| Pessimistic Lock | 670.60 ms | 634 ms | 759 ms |
| Atomic Update | 683.60 ms | 568 ms | 920 ms |
| Optimistic Lock | 1096.40 ms | 992 ms | 1196 ms |

---

## 15.3 해석

### Redis + MySQL

이번 Integration Test에서는
가장 짧은 처리 시간을 기록했습니다.

DB 기반 전략에서는 성공 요청이 모두:

```text
events.current_count
```

라는 동일 row를 갱신합니다.

Redis 전략에서는:

```text
정원 경쟁
→ Redis Lua Script
```

로 이동시키고 MySQL에서는:

```text
서로 다른 EventEntry row INSERT
```

가 수행됩니다.

Hot Row Write Contention 감소가
결과에 영향을 준 것으로 판단합니다.

---

### Atomic vs Pessimistic

두 전략은 이번 조건에서는
비슷한 평균 처리 시간을 기록했습니다.

따라서:

```text
Pessimistic Lock은 항상 느리다
```

라고 결론 내리지 않습니다.

Critical Section이 짧았기 때문에
Lock을 명시적으로 사용하더라도
대기 시간이 크게 증가하지 않은 것으로 해석합니다.

---

### Optimistic

높은 충돌 환경에서:

```text
Rollback
Retry
Backoff
```

비용이 누적되면서 가장 긴 처리 시간을 기록했습니다.

Optimistic Lock 자체가 느리다는 의미가 아니라
이번처럼 하나의 Hot Row에 쓰기가 집중되는 환경과
잘 맞지 않았다고 판단합니다.

---

# 16. Integration Test 결과의 한계

Integration Test 결과는 HTTP 요청을 포함하지 않습니다.

측정 범위에는:

```text
Spring Service
Transaction
JPA
MySQL
Redis
```

등이 포함되지만 실제:

```text
HTTP Controller
JSON Serialization
HTTP Connection
Web Server
```

경로 전체를 측정하지는 않습니다.

따라서 별도로 k6를 사용해
실제 HTTP API 부하 테스트를 수행했습니다.

---

# 17. k6 HTTP 부하 테스트

## 17.1 목적

다음 API를 실제 HTTP 요청으로 호출했습니다.

```text
POST /api/events/{eventId}/entries/strategies/{strategy}
```

각 동시성 전략을
동일한 HTTP 경로에서 비교했습니다.

---

## 17.2 테스트 구성

k6 Executor:

```text
shared-iterations
```

조건:

```text
총 Iteration = 200
VUs = 32
Maximum Duration = 30s
Event Capacity = 100
```

따라서 이 테스트는:

```text
200개의 요청이 정확히 동일한 순간에 발생
```

하는 테스트는 아닙니다.

32개의 VU가 총 200개의 요청을 수행하는 구조입니다.

---

## 17.3 User ID

각 요청이 중복 사용자로 실패하지 않도록:

```javascript
exec.scenario.iterationInTest + 1
```

을 User ID로 사용했습니다.

따라서:

```text
1
2
3
...
200
```

서로 다른 사용자가 요청합니다.

---

## 17.4 응답 분류

예상 가능한 HTTP Status:

```text
201 → 신청 성공
400 → 정원 초과 등의 비즈니스 실패
409 → 중복 신청
```

k6에서는 다음 Counter를 별도로 측정했습니다.

```text
success
business_failure
unexpected_failure
```

정상적인 정원 테스트의 목표:

```text
success = 100
business_failure = 100
unexpected_failure = 0
```

입니다.

---

# 18. k6 HTTP 최종 결과

| Strategy | Avg | p95 | p99 | Req/s | Unexpected |
|---|---:|---:|---:|---:|---:|
| **Redis + MySQL** | **30.89 ms** | **46.34 ms** | **50.10 ms** | **924.39** | 0 |
| Pessimistic Lock | 118.94 ms | 200.29 ms | 221.20 ms | 257.87 | 0 |
| Atomic Update | 123.38 ms | 230.27 ms | 269.33 ms | 247.34 | 0 |
| Optimistic Lock | 163.45 ms | 588.31 ms | 659.14 ms | 191.93 | 0 |

최종 테스트에서 모든 전략은:

```text
Success = 100
Business Failure = 100
Unexpected Failure = 0
```

을 만족했습니다.

---

# 19. HTTP 결과 분석

## 19.1 Redis + MySQL

```text
Avg = 30.89 ms
p95 = 46.34 ms
p99 = 50.10 ms
Req/s = 924.39
```

네 전략 중 가장 낮은 응답시간과
가장 높은 처리량을 기록했습니다.

하지만 이 결과만으로:

```text
Redis가 무조건 최선
```

이라고 판단하지 않습니다.

Redis를 도입하면:

```text
추가 인프라
Dual Write
Compensation
장애 복구
정합성 Reconciliation
```

문제가 함께 생깁니다.

---

## 19.2 Pessimistic Lock

```text
Avg = 118.94 ms
p95 = 200.29 ms
p99 = 221.20 ms
Req/s = 257.87
```

Atomic Update와 비교했을 때
현재 실험에서는 큰 차이가 나지 않았습니다.

Lock 자체보다:

```text
Transaction 길이
Critical Section 크기
경합 정도
```

가 중요하다는 점을 확인했습니다.

---

## 19.3 Atomic Update

최종:

```text
Avg = 123.38 ms
p95 = 230.27 ms
p99 = 269.33 ms
Req/s = 247.34
```

외부 인프라 없이 비교적 단순한 구조로
정합성을 보장할 수 있다는 장점이 있습니다.

다만 부하 테스트 과정에서
Atomic Update의 실패 처리 경로에서
예상하지 못한 HTTP 500 문제가 발견되었습니다.

이 문제는 아래에서 별도로 설명합니다.

---

## 19.4 Optimistic Lock

```text
Avg = 163.45 ms
p95 = 588.31 ms
p99 = 659.14 ms
Req/s = 191.93
```

특히:

```text
p95
p99
```

Tail Latency가 크게 증가했습니다.

많은 요청이 같은 Event row를 수정하면서
Version 충돌이 반복되고:

```text
Rollback
Retry
Backoff
```

비용이 누적된 것으로 판단합니다.

---

# 20. Atomic Update 부하 테스트 장애 발견

초기 Atomic k6 테스트 결과는 다음과 같았습니다.

```text
Success = 100
Business Failure = 91
Unexpected Failure = 9
```

정원이 100명이므로 정상이라면:

```text
100 성공
100 비즈니스 실패
0 예상하지 못한 실패
```

가 되어야 합니다.

하지만 9건의 HTTP 500이 발생했습니다.

---

# 21. 서버 로그 분석

서버 로그를 확인한 결과
Deadlock이 직접적인 원인은 아니었습니다.

발생 예외:

```text
IllegalStateException:
이벤트 신청 실패 원인을 확인할 수 없습니다.
```

발생 위치:

```text
EventEntryService.throwEntryFailure(...)
```

였습니다.

---

# 22. 기존 Atomic 실패 처리

Atomic Update 결과가:

```text
updated = 1
```

이면 정원 확보 성공입니다.

반대로:

```text
updated = 0
```

이면 다음 중 하나일 수 있습니다.

```text
이벤트 없음
신청 기간 아님
정원 초과
```

따라서 기존 코드는 `updated == 0`이면
Event를 다시 조회해 실패 원인을 판별했습니다.

```text
Atomic UPDATE

↓

updated == 0

↓

findById(eventId)

↓

Event 상태 확인

↓

적절한 비즈니스 예외 발생
```

그러나 일부 요청에서는
어떤 비즈니스 조건에도 해당하지 않는 상태가 나타났고:

```text
IllegalStateException
```

으로 이어졌습니다.

---

# 23. MySQL REPEATABLE READ와 Snapshot

MySQL의 Transaction Isolation Level은:

```text
REPEATABLE_READ
```

였습니다.

Atomic UPDATE는 일반적인 Consistent Read와 다르게
현재 최신 row 상태를 기준으로 조건을 평가할 수 있습니다.

반면 같은 Transaction에서 이후 수행된 일반 SELECT는
기존 Consistent Read Snapshot을 볼 수 있습니다.

---

## 23.1 발생 가능한 흐름

실제 최신 DB:

```text
currentCount = 100
capacity = 100
```

Atomic UPDATE:

```text
WHERE currentCount < capacity
```

조건이 false이므로:

```text
updated = 0
```

이 됩니다.

여기까지는 정상입니다.

하지만 이후 실패 원인을 확인하는:

```text
findById()
```

일반 SELECT가 이전 Snapshot을 조회한다고 가정하면:

```text
currentCount = 95
capacity = 100
```

처럼 보일 수 있습니다.

그러면 애플리케이션에서는:

```text
이벤트 존재함
신청 기간 정상
정원도 남아 있음
```

으로 판단합니다.

결과적으로:

```text
Atomic UPDATE는 실패했는데
실패 원인을 찾을 수 없음
```

상태가 됩니다.

마지막 fallback:

```java
throw new IllegalStateException(
    "이벤트 신청 실패 원인을 확인할 수 없습니다."
);
```

가 실행되면서 HTTP 500이 발생했습니다.

---

# 24. Persistence Context Clear와 MVCC Snapshot

Atomic Repository에는:

```java
clearAutomatically = true
```

가 적용되어 있습니다.

이 옵션은 Bulk Update 이후
JPA Persistence Context를 비워
Entity의 stale state 문제를 줄이는 데 도움이 됩니다.

하지만:

```text
JPA Persistence Context
```

와:

```text
MySQL Transaction MVCC Snapshot
```

은 다른 개념입니다.

Persistence Context를 clear했다고 해서
DB Transaction의 Consistent Read Snapshot 자체가
새로 만들어지는 것은 아닙니다.

따라서 이 문제를:

```text
JPA Cache 문제
```

로만 해석해서는 안 된다고 판단했습니다.

---

# 25. Atomic Update 실패 경로 개선

실패 원인을 판별할 때
일반 SELECT 대신 Locking Read를 사용하도록 변경했습니다.

기존:

```text
findById(eventId)
```

개선:

```text
findByIdWithPessimisticLock(eventId)
```

Repository:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
    SELECT e
    FROM Event e
    WHERE e.id = :eventId
""")
Optional<Event> findByIdWithPessimisticLock(
        Long eventId
);
```

Locking Read를 통해
실패 판별 시 최신 row 상태를 기준으로 확인하도록 변경했습니다.

---

# 26. Atomic 전략이 Pessimistic으로 바뀐 것은 아니다

중요한 점은 Pessimistic Lock을 사용하는 곳이:

```text
Atomic Update가 실패한 뒤
실패 원인을 판별하는 경로
```

뿐이라는 것입니다.

정상 신청 처리:

```text
Atomic Conditional UPDATE
```

는 그대로 유지됩니다.

즉 전체 Atomic 전략을:

```text
Pessimistic Lock 전략
```

으로 변경한 것이 아닙니다.

---

# 27. Atomic 장애 개선 전 / 후

## Before

```text
Success = 100
Business Failure = 91
Unexpected Failure = 9
```

HTTP 500:

```text
9건
```

---

## After

```text
Success = 100
Business Failure = 100
Unexpected Failure = 0
```

최종 성능:

```text
Avg = 123.38 ms
p95 = 230.27 ms
p99 = 269.33 ms
Req/s = 247.34
```

---

# 28. Atomic 장애에서 얻은 점

이번 문제는 단순 Unit Test나 Integration Test만으로는
발견하지 못했습니다.

실제 HTTP 부하 테스트를 수행하면서:

```text
부하 테스트

↓

예상하지 못한 HTTP 500 발견

↓

Server Stack Trace 확인

↓

실패 발생 위치 확인

↓

Transaction Isolation 확인

↓

REPEATABLE READ / MVCC 분석

↓

일반 SELECT와 Locking Read 차이 확인

↓

실패 판별 경로 수정

↓

k6 재실행

↓

Unexpected Failure
9 → 0
```

과정을 거쳤습니다.

이 프로젝트에서 성능 수치보다
더 의미 있었던 실험 중 하나였습니다.

---

# 29. 성능 수치를 해석할 때의 제한사항

현재 측정은 로컬 개발 환경에서 수행했습니다.

따라서 다음 환경까지 포함한
운영 환경 벤치마크는 아닙니다.

```text
다중 Application Instance
실제 Network Latency
운영 수준의 MySQL Cluster
Redis Cluster
Load Balancer
Container Resource Limit
Cloud 환경
```

또한 k6의:

```text
32 VUs
200 shared iterations
```

은 200개 요청이 물리적으로 정확히 같은 시각에
시작된다는 의미가 아닙니다.

따라서 측정값은:

```text
절대적인 성능 수치
```

가 아니라:

```text
동일한 로컬 조건에서
각 전략의 상대적 특성을 비교한 결과
```

로 해석합니다.

---

# 30. 전략별 비교 정리

| 전략 | 장점 | 단점 | 이번 실험에서 관찰한 특징 |
|---|---|---|---|
| Atomic Update | 단순, 외부 인프라 없음 | Hot Row 경합 | 구현/운영 복잡도 대비 안정적 |
| Pessimistic Lock | 직관적, 충돌 사전 차단 | Lock 대기 | 짧은 Critical Section에서는 Atomic과 유사 |
| Optimistic Lock | 충돌 적을 때 효율적 | Retry 비용 | Hot Row 환경에서 Tail Latency 증가 |
| Redis + MySQL | 높은 처리량, DB 정원 경합 감소 | Dual Write, 운영 복잡도 | 현재 로컬 테스트에서 가장 높은 처리량 |

---

# 31. 현재 결론

## 31.1 Naive Read-Modify-Write

높은 동시성 환경에서는
Lost Update가 발생할 수 있었습니다.

```text
조회
→ 판단
→ 변경
```

이 분리되어 있기 때문입니다.

---

## 31.2 Atomic Update

외부 인프라 없이
비교적 단순한 구조로 정합성을 확보할 수 있었습니다.

하지만 동일 Event row에 쓰기가 집중되는
Hot Row 구조라는 특성은 남습니다.

---

## 31.3 Pessimistic Lock

Lock이라는 이유만으로
항상 성능이 나쁘다고 볼 수 없었습니다.

이번처럼 Transaction과 Critical Section이 짧다면
충돌이 많은 환경에서도 실용적인 선택이 될 수 있습니다.

---

## 31.4 Optimistic Lock

충돌이 적은 환경에서는 장점이 있지만
하나의 Event row에 요청이 집중되면:

```text
Version Conflict
Retry
Rollback
Backoff
```

비용이 크게 증가할 수 있었습니다.

---

## 31.5 Redis

정원 경쟁을 Redis Lua Script로 이동시키면서
이번 실험에서는 가장 높은 처리량을 기록했습니다.

하지만:

```text
Redis
+
MySQL
```

두 데이터 저장소를 함께 사용하면서
Dual Write라는 새로운 문제가 생겼습니다.

따라서:

```text
성능 향상
=
복잡도 감소
```

가 아니라는 점을 확인했습니다.

---

## 31.6 DB Constraint

동시성 환경에서는:

```text
사전 조회
```

만으로 중복을 완전히 막을 수 없습니다.

최종 불변식은:

```text
UNIQUE(event_id, user_id)
```

DB Constraint로 보장했습니다.

---

## 31.7 부하 테스트

부하 테스트는 단순히:

```text
누가 몇 ms 빠른가
```

를 비교하기 위한 용도로만 사용하지 않았습니다.

실제 Atomic Update의 HTTP 500 문제를 발견하고:

```text
MySQL REPEATABLE READ
MVCC
Consistent Read
Locking Read
```

까지 분석하는 계기가 되었습니다.

---

# 32. 최종적으로 동시성 전략을 선택할 때

이번 프로젝트의 결론은:

```text
Redis가 가장 빠르니 Redis를 사용한다
```

가 아닙니다.

실제 선택에서는 다음을 함께 고려해야 합니다.

```text
정합성 요구사항

트래픽 크기

경합 빈도

Hot Row 여부

Transaction 길이

외부 인프라 운영 비용

장애 복구 방식

데이터 정합성 복구 전략

실제 부하 테스트 결과
```

따라서 특정 기술의 이름보다
**문제의 특성과 실제 측정 결과를 기반으로 전략을 선택하는 것이 중요하다**
는 결론을 얻었습니다.

---

# 33. 향후 개선

현재 구현 이후 추가로 검토할 수 있는 작업은 다음과 같습니다.

### Redis / MySQL 정합성

```text
TransactionSynchronization
Retry Queue
Reconciliation
Outbox Pattern
```

### 운영 환경

```text
Metrics
Tracing
Structured Logging
```

### 실행 환경

```text
Docker Compose
다중 Application Instance
```

### API 문서

```text
Swagger
OpenAPI
```

---

# 34. 관련 파일

상세 구현은 프로젝트 코드에서 확인할 수 있습니다.

```text
src/main/java/com/seonchaksun/event
src/main/java/com/seonchaksun/entry
src/test
k6/entry-burst.js
```

프로젝트 전체 설명:

```text
README.md
```