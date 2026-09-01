# Concurrency Strategy

이 문서는 `선착순` 프로젝트에서 다룬 동시성 문제와 해결 전략을 기술적으로 정리한 문서입니다.

프로젝트의 핵심 목표는 단순히 "정원 100명까지만 신청을 받는다"가 아니라, 높은 경합 상황에서 발생하는 동시성 문제를 직접 재현하고 여러 해결 전략의 특성과 한계를 비교하는 것입니다.

---

## 1. 문제 정의

정원이 100명인 이벤트에 다수의 사용자가 동시에 신청한다고 가정합니다.

가장 단순한 구현은 다음과 같습니다.

```text
Event 조회
→ 현재 신청자 수 확인
→ 정원이 남아 있으면 currentCount 증가
→ EventEntry 저장
```

개념적으로:

```java
Event event = eventRepository.findById(eventId)
        .orElseThrow();

event.enter(now);

eventEntryRepository.save(
        new EventEntry(event, userId)
);
```

단일 요청에서는 정상적으로 동작하지만 여러 트랜잭션이 동시에 같은 Event를 조회하면 문제가 발생할 수 있습니다.

---

## 2. Lost Update

예를 들어 현재 신청자 수가 20명이라고 가정합니다.

```text
currentCount = 20
```

여러 요청이 동시에 조회하면:

```text
Thread A → 20 조회
Thread B → 20 조회
Thread C → 20 조회
```

각 요청은 독립적으로:

```text
20 + 1 = 21
```

을 계산합니다.

결과적으로 실제 신청은 3건 발생했지만 최종 `currentCount`는 21이 될 수 있습니다.

```text
Expected
currentCount = 23

Actual
currentCount = 21
```

이런 문제를 Lost Update라고 볼 수 있습니다.

---

## 3. Naive 구현을 통한 문제 재현

동시성 제어를 적용하기 전에 가장 단순한 구현으로 문제를 재현했습니다.

테스트 조건:

```text
요청 수       : 200
Event Capacity: 100
Thread Pool    : 32
```

실제 테스트에서:

```text
Success       : 40
Failure       : 160

EventEntry    : 40
currentCount  : 20
```

과 같은 결과를 확인했습니다.

신청 내역은 40건인데 Event의 `currentCount`는 20으로 기록되었습니다.

즉 다음 invariant가 깨졌습니다.

```text
Event.currentCount == EventEntry count
```

이 결과를 기준으로 동시성 전략을 하나씩 적용했습니다.

---

# 4. 동시성 제어 전략

프로젝트에서는 다음 네 가지 전략을 구현했습니다.

```text
1. Atomic Update
2. Pessimistic Lock
3. Optimistic Lock
4. Redis Lua Script
```

모든 전략은 동일한 비즈니스 요구사항을 만족해야 합니다.

```text
capacity = 100

200개의 신청 요청이 들어와도

Success             = 100
Business Failure    = 100
Unexpected Failure  = 0
```

---

# 5. Atomic Update

## 5.1 기본 아이디어

애플리케이션에서:

```text
SELECT
→ 정원 확인
→ UPDATE
```

를 나누어 처리하지 않고 DB가 하나의 UPDATE 문으로 정원 확인과 증가를 처리하도록 합니다.

개념적으로:

```sql
UPDATE events
SET current_count = current_count + 1
WHERE id = ?
  AND current_count < capacity;
```

UPDATE 결과:

```text
affected rows = 1
→ 정원 확보 성공

affected rows = 0
→ 신청 불가능
```

이 방식에서는 여러 요청이 같은 값을 조회한 뒤 증가시키는 과정을 제거할 수 있습니다.

---

## 5.2 구현

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
        @Param("eventId") Long eventId,
        @Param("now") LocalDateTime now
);
```

Atomic Update 성공 후 `EventEntry`를 저장합니다.

```text
Atomic UPDATE
      ↓
affected rows = 1
      ↓
EventEntry INSERT
```

---

## 5.3 Version 직접 증가

Event에는 Optimistic Lock 테스트를 위해 `@Version` 필드가 존재합니다.

```java
@Version
private Long version;
```

하지만 JPQL Bulk Update는 일반적인 JPA Dirty Checking을 우회합니다.

따라서 다음과 같이 version도 직접 증가시켰습니다.

```java
e.version = e.version + 1
```

---

## 5.4 장점

```text
- 구현이 비교적 단순함
- 별도의 SELECT FOR UPDATE가 필요하지 않음
- 높은 경합에서도 안정적인 처리 가능
- DB 한 곳에서 정합성 제어 가능
```

---

## 5.5 단점 및 고려사항

```text
- Bulk Update가 영속성 컨텍스트를 우회함
- 실패 원인을 추가로 조회해야 할 수 있음
- DB 조건식에 비즈니스 조건이 들어갈 수 있음
```

---

# 6. Pessimistic Lock

## 6.1 기본 아이디어

충돌이 발생할 것이라고 가정하고 Event Row에 쓰기 잠금을 획득합니다.

개념적으로:

```sql
SELECT *
FROM events
WHERE id = ?
FOR UPDATE;
```

JPA에서는:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
```

를 사용합니다.

---

## 6.2 처리 흐름

```text
Thread A
→ Event Row Lock 획득
→ 정원 확인
→ currentCount 증가
→ EventEntry 저장
→ Commit
→ Lock 해제

Thread B
→ A의 Lock 해제까지 대기
→ 처리
```

동일 Event에 대한 신청은 사실상 순차적으로 처리됩니다.

---

## 6.3 장점

```text
- 데이터 정합성을 이해하기 쉬움
- 충돌이 많은 환경에서도 명확하게 동작
- Retry 로직이 필요하지 않음
```

---

## 6.4 단점

```text
- Lock Wait 발생
- 긴 트랜잭션은 처리량 저하 가능
- Deadlock 가능성 고려 필요
```

따라서 Pessimistic Lock에서는 트랜잭션 범위를 가능한 짧게 유지하는 것이 중요합니다.

---

# 7. Optimistic Lock

## 7.1 기본 아이디어

충돌이 적을 것이라고 가정하고 DB Row를 미리 잠그지 않습니다.

대신 version 값을 이용해서 다른 트랜잭션이 먼저 데이터를 변경했는지 감지합니다.

```java
@Version
private Long version;
```

예:

```text
currentCount = 20
version      = 5
```

업데이트는 개념적으로:

```sql
UPDATE events
SET current_count = 21,
    version = 6
WHERE id = ?
  AND version = 5;
```

형태로 수행됩니다.

---

## 7.2 충돌 발생

두 트랜잭션이 동시에:

```text
version = 5
```

를 조회했다고 가정합니다.

첫 번째 요청:

```text
version 5 → 6
성공
```

두 번째 요청:

```text
WHERE version = 5

이미 DB version = 6

→ UPDATE 실패
→ Optimistic Lock 충돌
```

이 됩니다.

---

# 8. Optimistic Retry

Optimistic Lock에서 충돌 자체는 정상적인 상황으로 볼 수 있습니다.

따라서 프로젝트에서는 충돌 발생 시 Retry하도록 구현했습니다.

```text
조회
↓
수정
↓
Optimistic Lock 충돌
↓
Backoff
↓
재조회
↓
Retry
```

현재 설정:

```text
MAX_RETRY = 100
Backoff   = random 1~5ms
```

Retry를 새로운 트랜잭션에서 수행하기 위해 Worker와 Retry Facade를 별도의 Spring Bean으로 분리했습니다.

---

## 8.1 Retry를 별도 트랜잭션으로 처리한 이유

하나의 트랜잭션 내부에서 실패한 Entity 상태를 그대로 재사용하면 정상적인 재시도가 어려울 수 있습니다.

따라서:

```text
Retry Facade
     ↓
Worker
     ↓
새 Transaction
```

구조로 구성했습니다.

---

## 8.2 Optimistic Lock의 특징

Optimistic 방식에서는 대부분의 요청은 빠르게 처리될 수 있지만 충돌한 요청은 여러 번 재시도할 수 있습니다.

따라서:

```text
Median은 낮음

하지만

p95 / p99는 높아질 수 있음
```

이라는 특징이 나타날 수 있습니다.

실제 최종 테스트에서도 이 패턴이 확인되었습니다.

---

# 9. Redis Lua Script

## 9.1 Redis를 선택한 이유

DB Row를 모든 요청이 경쟁하도록 하는 대신 정원 Counter를 Redis에서 관리합니다.

Redis는 INCR 자체도 Atomic하지만 다음 로직 전체를 원자적으로 실행해야 합니다.

```text
현재 Counter 조회
→ capacity 비교
→ 증가
```

따라서 Lua Script를 사용했습니다.

---

## 9.2 Reserve Script

```lua
local current = tonumber(redis.call('GET', KEYS[1]) or '0')
local capacity = tonumber(ARGV[1])

if current >= capacity then
    return 0
end

redis.call('INCR', KEYS[1])

return 1
```

Key:

```text
event:capacity:{eventId}
```

예:

```text
event:capacity:45
```

---

## 9.3 원자성

Redis는 Lua Script 실행 중 다른 명령이 끼어들지 않습니다.

따라서:

```text
GET
→ capacity check
→ INCR
```

전체를 하나의 원자적 작업처럼 실행할 수 있습니다.

---

# 10. Redis + MySQL 구조

Redis 전략에서도 실제 신청 기록은 MySQL의 `EventEntry`에 저장합니다.

```text
Redis
→ 정원 확보

MySQL
→ 실제 신청 내역 저장
```

따라서 Redis 전략의 처리 흐름은:

```text
Event 조회
↓
중복 신청 사전 검사
↓
신청 기간 검증
↓
Redis Lua Reserve
↓
EventEntry INSERT
↓
응답
```

입니다.

---

## 10.1 Count Source

Redis 전략에서는 Redis Counter를 신청 수의 Source of Truth로 사용합니다.

따라서:

```text
events.current_count = 0
```

으로 유지합니다.

정상적인 최종 상태:

```text
Redis Counter  = 100
EventEntry     = 100
```

입니다.

MySQL의 `events.current_count`는 Redis 전략에서는 신청 수 검증에 사용하지 않습니다.

---

# 11. Redis 보상 처리

Redis Reserve가 성공한 뒤 MySQL 저장이 실패할 수 있습니다.

예:

```text
Redis Counter
99 → 100

↓

EventEntry INSERT 실패
```

이 상태를 그대로 두면 실제 신청은 99건인데 Redis는 100명으로 판단합니다.

따라서 DB 저장 실패 시 Redis Counter를 감소시키는 보상 처리를 수행합니다.

```text
Reserve
↓
DB INSERT 실패
↓
Release
```

---

## 11.1 Release Script

Counter가 0보다 클 경우에만 감소시킵니다.

개념적으로:

```lua
local current = tonumber(redis.call('GET', KEYS[1]) or '0')

if current > 0 then
    redis.call('DECR', KEYS[1])
end
```

---

# 12. Redis Dual Write의 한계

현재 보상 방식만으로 Redis와 MySQL 사이의 완벽한 원자성을 보장할 수는 없습니다.

예를 들어:

```text
Redis Reserve 성공
↓
saveAndFlush 성공
↓
Service Method 정상 종료
↓
Transaction Commit 단계에서 실패
```

하는 경우를 생각할 수 있습니다.

`saveAndFlush()` 시점에는 성공했기 때문에 서비스 내부 `catch`에서 Redis Release를 수행하지 못할 수 있습니다.

또한:

```text
Redis Release 자체 실패
```

가능성도 존재합니다.

따라서 Redis + MySQL 조합에서는 성능을 얻는 대신 분산 정합성 문제를 별도로 고려해야 합니다.

향후 개선 방향:

```text
- TransactionSynchronization
- Retry Queue
- Reconciliation Job
- Transactional Outbox
```

XA / 2PC는 현재 프로젝트 규모 대비 복잡도가 지나치게 크다고 판단하여 적용하지 않았습니다.

---

# 13. 중복 신청 문제

정원 제어만으로는 충분하지 않습니다.

동일 사용자가 동시에 여러 번 신청할 수도 있습니다.

애플리케이션에서:

```java
existsByEventIdAndUserId(...)
```

를 먼저 확인해도 Race Condition은 남아 있습니다.

---

## 13.1 Race Condition

```text
Request A → 중복 신청 없음 확인
Request B → 중복 신청 없음 확인

A → INSERT
B → INSERT
```

두 요청 모두 중복 검사 시점에서는 신청 데이터가 존재하지 않을 수 있습니다.

---

## 13.2 DB UNIQUE Constraint

따라서 DB를 최종 방어선으로 사용합니다.

```text
UNIQUE(event_id, user_id)
```

동일 사용자의 동시 신청 테스트 결과:

```text
Concurrent Requests : 20

Success              : 1
Duplicate            : 19
Unexpected           : 0

Event Count          : 1
EventEntry           : 1
```

을 확인했습니다.

즉:

```text
Application Check
+
Database UNIQUE Constraint
```

방식으로 중복 신청을 방어합니다.

---

# 14. Atomic 전략의 MVCC 문제

프로젝트에서 가장 중요한 장애 분석 사례 중 하나입니다.

Atomic 전략을 HTTP 부하 테스트하던 중 예상하지 못한 500 오류가 발생했습니다.

결과:

```text
Success             : 100
Business Failure    : 91
Unexpected Failure  : 9
```

---

## 14.1 초기 흐름

Atomic UPDATE가 0을 반환하면 신청이 실패했다는 의미입니다.

이후 실패 원인을 확인하기 위해 Event를 다시 조회했습니다.

```text
Atomic UPDATE
↓
affected rows = 0
↓
Event SELECT
↓
실패 이유 판단
```

문제는 이 SELECT였습니다.

---

## 14.2 MySQL REPEATABLE_READ와 MVCC

MySQL의 기본 Transaction Isolation Level은:

```text
REPEATABLE_READ
```

입니다.

Atomic UPDATE는 현재 최신 Row를 기준으로 조건을 평가합니다.

예를 들어 실제 DB:

```text
current_count = 100
capacity      = 100
```

이면:

```sql
current_count < capacity
```

조건이 false이므로 UPDATE 결과는 0입니다.

하지만 같은 트랜잭션에서 수행한 일반 SELECT는 MVCC Snapshot을 사용하면서 이전 값을 볼 수 있습니다.

예:

```text
Atomic UPDATE가 확인한 값 = 100

일반 SELECT가 본 Snapshot = 95
```

이 경우 애플리케이션은:

```text
95 < 100
```

으로 판단해서 capacity 초과 예외를 발생시키지 못합니다.

최종적으로 fallback `IllegalStateException`이 발생하면서 HTTP 500으로 응답했습니다.

---

# 15. clearAutomatically가 해결하지 못한 이유

Atomic Update에는:

```java
clearAutomatically = true
```

가 적용되어 있습니다.

하지만 이것은 JPA Persistence Context를 clear하는 기능입니다.

```text
Persistence Context Clear
≠
MySQL MVCC Snapshot Reset
```

즉 Entity Cache를 지운다고 해서 현재 트랜잭션의 DB Snapshot 자체가 바뀌는 것은 아닙니다.

---

# 16. Atomic MVCC 문제 해결

실패 원인을 판단하는 조회만 Locking Read로 변경했습니다.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
    SELECT e
      FROM Event e
     WHERE e.id = :eventId
""")
Optional<Event> findByIdWithPessimisticLock(
        @Param("eventId") Long eventId
);
```

Locking Read는 현재 Row를 기준으로 조회하므로 최신 상태를 확인할 수 있습니다.

---

## 16.1 중요한 설계 포인트

Atomic 전략 전체를 Pessimistic Lock 방식으로 변경한 것은 아닙니다.

정상적인 신청 흐름:

```text
Atomic UPDATE
```

는 그대로 유지합니다.

오직:

```text
Atomic UPDATE 실패 후
실패 이유를 확인하는 조회
```

에만 Locking Read를 사용했습니다.

수정 후:

```text
Success             : 100
Business Failure    : 100
Unexpected Failure  : 0
```

으로 정상화되었습니다.

---

# 17. 정확성 검증 기준

모든 전략에서 가장 먼저 확인한 것은 성능이 아니라 정확성입니다.

이벤트:

```text
capacity = 100
```

요청:

```text
200
```

정상 결과:

```text
Success             = 100
Business Failure    = 100
Unexpected Failure  = 0
```

DB 기반 전략:

```text
Event.currentCount = 100
EventEntry          = 100
```

Redis 전략:

```text
Redis Counter       = 100
EventEntry          = 100
```

정확성을 만족하지 못한 성능 결과는 유효한 Benchmark로 사용하지 않습니다.

---

# 18. k6 HTTP Benchmark

통합 테스트뿐 아니라 실제 HTTP 레이어까지 포함한 성능 비교를 위해 k6를 사용했습니다.

테스트 설정:

```text
Scenario       : shared-iterations
VUs            : 32
Iterations     : 200
Capacity       : 100
```

모든 전략에 동일한 조건을 적용했습니다.

---

## 18.1 기대 HTTP 결과

```text
100 Success
100 Business Failure
0 Unexpected Failure
```

`201`, 예상 가능한 `400 / 409`는 테스트상 정상적인 응답으로 처리합니다.

따라서:

```text
http_req_failed = 0%
```

이 정상입니다.

---

## 18.2 동시성 표현에 대한 주의

현재 k6 테스트는:

```text
32 VUs
200 shared iterations
```

입니다.

따라서 정확히 200개의 요청이 동일한 순간에 시작하는 테스트라고 표현하지 않습니다.

문서에서는:

> 32 VUs가 200개의 요청을 shared-iterations 방식으로 수행했다.

라고 표현합니다.

---

# 19. Warm-up

초기 수동 반복 테스트에서는 대부분의 전략이 Run이 반복될수록 빨라지는 경향을 보였습니다.

특히 Redis에서는 첫 실행과 이후 실행 사이의 차이가 컸습니다.

가능한 원인:

```text
- JVM JIT
- Connection Pool
- Redis Client Connection
- DB Buffer / Cache
- 애플리케이션 초기화 비용
```

따라서 최종 Benchmark에서는:

```text
Warm-up 1회
→ 결과 제외

Measured Runs 5회
→ 평균 사용
```

방식으로 변경했습니다.

매 회차 새로운 Event를 생성했습니다.

---

# 20. Benchmark Automation

반복 테스트를 수동으로 수행하지 않도록 PowerShell Script를 추가했습니다.

```text
scripts/benchmark.ps1
```

사용 예:

```powershell
.\scripts\benchmark.ps1 -Strategy atomic
```

지원 전략:

```text
atomic
pessimistic
optimistic
redis
```

자동화 흐름:

```text
Event 생성
↓
Warm-up 1회
↓
10초 대기
↓
Measured Run 1
↓
10초
↓
...
↓
Measured Run 5
```

---

# 21. Final Benchmark Result

테스트 조건:

```text
Event Capacity : 100
Requests       : 200
VUs            : 32

Warm-up        : 1
Measured Runs  : 5
```

모든 최종 측정 회차에서:

```text
Success             = 100
Business Failure    = 100
Unexpected Failure  = 0
```

을 만족했습니다.

---

## 21.1 Atomic

| Run | Avg | p95 | p99 | Max | Req/s |
|---|---:|---:|---:|---:|---:|
| 1 | 97.78 ms | 144.27 ms | 146.10 ms | 146.37 ms | 309.95 |
| 2 | 83.39 ms | 134.48 ms | 135.25 ms | 141.27 ms | 362.17 |
| 3 | 81.09 ms | 126.98 ms | 129.36 ms | 136.76 ms | 371.51 |
| 4 | 75.08 ms | 118.96 ms | 119.81 ms | 124.45 ms | 402.71 |
| 5 | 74.65 ms | 120.05 ms | 120.84 ms | 125.61 ms | 406.01 |

평균:

```text
Avg   = 82.40 ms
p95   = 128.95 ms
p99   = 130.27 ms
Max   = 134.89 ms
Req/s = 370.47
```

---

## 21.2 Pessimistic

| Run | Avg | p95 | p99 | Max | Req/s |
|---|---:|---:|---:|---:|---:|
| 1 | 81.42 ms | 138.45 ms | 139.51 ms | 148.87 ms | 374.34 |
| 2 | 79.73 ms | 142.74 ms | 143.91 ms | 144.25 ms | 381.66 |
| 3 | 81.05 ms | 146.65 ms | 147.54 ms | 148.54 ms | 376.25 |
| 4 | 76.44 ms | 131.09 ms | 132.71 ms | 136.66 ms | 396.87 |
| 5 | 76.72 ms | 133.09 ms | 134.96 ms | 135.47 ms | 397.46 |

평균:

```text
Avg   = 79.07 ms
p95   = 138.40 ms
p99   = 139.73 ms
Max   = 142.76 ms
Req/s = 385.32
```

---

## 21.3 Optimistic

| Run | Avg | p95 | p99 | Max | Req/s |
|---|---:|---:|---:|---:|---:|
| 1 | 115.19 ms | 409.83 ms | 579.69 ms | 651.54 ms | 270.93 |
| 2 | 109.21 ms | 364.34 ms | 475.04 ms | 600.81 ms | 286.11 |
| 3 | 102.80 ms | 327.40 ms | 460.84 ms | 561.14 ms | 302.01 |
| 4 | 111.49 ms | 390.65 ms | 671.49 ms | 673.19 ms | 280.09 |
| 5 | 91.97 ms | 343.59 ms | 548.88 ms | 550.93 ms | 338.14 |

평균:

```text
Avg   = 106.13 ms
p95   = 367.16 ms
p99   = 547.19 ms
Max   = 607.52 ms
Req/s = 295.46
```

---

## 21.4 Redis

| Run | Avg | p95 | p99 | Max | Req/s |
|---|---:|---:|---:|---:|---:|
| 1 | 19.93 ms | 28.62 ms | 31.36 ms | 44.59 ms | 1394.46 |
| 2 | 20.84 ms | 30.27 ms | 44.91 ms | 50.38 ms | 1349.40 |
| 3 | 18.91 ms | 28.49 ms | 29.81 ms | 38.35 ms | 1485.47 |
| 4 | 18.96 ms | 27.29 ms | 38.77 ms | 39.07 ms | 1473.18 |
| 5 | 22.83 ms | 37.15 ms | 39.96 ms | 48.20 ms | 1249.43 |

평균:

```text
Avg   = 20.29 ms
p95   = 30.36 ms
p99   = 36.96 ms
Max   = 44.12 ms
Req/s = 1390.39
```

---

# 22. Final Comparison

| Strategy | Avg | p95 | p99 | Req/s |
|---|---:|---:|---:|---:|
| Atomic | 82.40 ms | 128.95 ms | 130.27 ms | 370.47 |
| Pessimistic | 79.07 ms | 138.40 ms | 139.73 ms | 385.32 |
| Optimistic | 106.13 ms | 367.16 ms | 547.19 ms | 295.46 |
| Redis | **20.29 ms** | **30.36 ms** | **36.96 ms** | **1390.39** |

---

# 23. 결과 분석

## Atomic

Atomic은 명시적인 Row Lock 없이도 안정적인 성능을 보였습니다.

특히:

```text
p95 = 128.95 ms
p99 = 130.27 ms
```

로 MySQL 기반 전략 중 가장 낮은 Tail Latency를 기록했습니다.

구현 복잡도와 성능 사이의 균형이 좋은 방식이라고 판단했습니다.

---

## Pessimistic

이번 환경에서는 MySQL 기반 전략 중:

```text
Avg
Req/s
```

가 가장 좋았습니다.

```text
Avg   = 79.07 ms
Req/s = 385.32
```

Atomic과 큰 차이는 아니었습니다.

높은 경합에서는 요청을 Lock으로 직렬화하는 방식이 오히려 안정적으로 동작할 수 있음을 확인했습니다.

---

## Optimistic

Optimistic은 가장 명확하게 Tail Latency 증가가 나타났습니다.

```text
Avg = 106.13 ms

p95 = 367.16 ms
p99 = 547.19 ms
```

평균 응답시간과 비교했을 때 p95/p99가 크게 증가했습니다.

이는 높은 경합에서 Optimistic Lock 충돌이 반복되고 Retry 비용이 일부 요청에 집중되기 때문이라고 해석할 수 있습니다.

따라서 선착순 시스템처럼 특정 Row에 요청이 집중되는 환경에서는 Optimistic Lock이 반드시 가장 효율적인 방식이라고 볼 수 없습니다.

---

## Redis

현재 Benchmark 조건에서는 Redis가:

```text
Avg
p95
p99
Req/s
```

모두 가장 좋은 결과를 기록했습니다.

```text
Avg   = 20.29 ms
p95   = 30.36 ms
p99   = 36.96 ms
Req/s = 1390.39
```

DB Row의 동일 Counter를 계속 수정하는 대신 Redis에서 Lua Script로 Counter를 관리한 것이 큰 차이를 만들었습니다.

하지만 Redis를 도입하면서:

```text
Redis
+
MySQL
```

두 저장소 사이의 정합성 문제가 새롭게 발생합니다.

따라서 Redis 전략은 성능 측면에서는 유리하지만 운영 복잡도와 장애 복구 전략까지 함께 고려해야 합니다.

---

# 24. 전략별 적합한 상황

## Atomic Update

적합한 경우:

```text
- 단일 DB 중심 시스템
- 단순한 Counter 조건
- 구현 복잡도를 낮추고 싶음
- 높은 경합에서도 안정적인 Tail Latency 필요
```

---

## Pessimistic Lock

적합한 경우:

```text
- 충돌이 빈번함
- 데이터 정합성이 매우 중요함
- Row Lock 대기를 허용할 수 있음
```

---

## Optimistic Lock

적합한 경우:

```text
- 충돌 빈도가 낮음
- 긴 DB Lock을 피하고 싶음
- Retry 비용이 크지 않음
```

충돌이 많은 환경에서는 Retry 비용을 반드시 고려해야 합니다.

---

## Redis

적합한 경우:

```text
- 높은 처리량 필요
- DB Row 경합을 줄이고 싶음
- Redis 운영이 가능함
- Dual Write 정합성 문제를 해결할 수 있음
```

---

# 25. Monitoring

전략별 동작을 운영 관점에서도 확인하기 위해 Micrometer Custom Metric을 추가했습니다.

```text
EventEntryStrategyService
        ↓
EntryMetrics
        ↓
Micrometer
        ↓
/actuator/prometheus
        ↓
Prometheus
        ↓
Grafana
```

---

## 25.1 Metric

Request Counter:

```text
seonchaksun.entry.requests
```

Prometheus:

```text
seonchaksun_entry_requests_total
```

Timer:

```text
seonchaksun.entry.duration
```

Prometheus:

```text
seonchaksun_entry_duration_seconds_count
seonchaksun_entry_duration_seconds_sum
```

---

# 26. Metric Result 분리

단순 성공/실패가 아니라 다음 세 상태로 구분합니다.

```text
success
business_failure
unexpected_failure
```

---

## success

```text
정상 신청 성공
```

---

## business_failure

예:

```text
- 정원 초과
- 중복 신청
- 신청 가능 시간이 아님
- 존재하지 않는 이벤트
```

예상 가능한 비즈니스 예외입니다.

---

## unexpected_failure

예상하지 못한 시스템 오류입니다.

예:

```text
RuntimeException
500 Internal Server Error
```

이를 통해 정원 초과 같은 정상적인 비즈니스 거절과 실제 시스템 장애를 모니터링에서 분리할 수 있습니다.

---

# 27. Grafana Dashboard

현재 Dashboard에서는 다음 지표를 확인합니다.

```text
JVM 힙 메모리 사용량
백엔드 CPU 사용률
초당 HTTP 요청 수
HTTP 평균 응답 시간

전략별 신청 요청 수
전략별 신청 성공 수
전략별 비즈니스 실패 수
전략별 시스템 오류 수
전략별 평균 처리 시간
```

Dashboard는 JSON으로 Repository에서 관리하고 Grafana Provisioning을 사용해 자동 로드합니다.

---

# 28. Benchmark의 한계

이번 Benchmark 환경:

```text
Windows Local PC
Docker Compose

Backend    : Single Instance
MySQL      : Single Instance
Redis      : Single Instance
Prometheus : Single Instance
Grafana    : Single Instance
```

따라서 결과를 일반화해서:

```text
Redis는 항상 Atomic보다 몇 배 빠르다
```

라고 표현할 수는 없습니다.

실제 운영 환경에서는 다음 요소의 영향을 받습니다.

```text
- Hardware
- Network Latency
- DB Instance Spec
- Redis Deployment
- Connection Pool
- Backend Instance Count
- Traffic Pattern
- DB Replication
- Redis Cluster
- GC
- JVM Warm-up
```

현재 결과는 동일한 로컬 환경에서 각 전략의 상대적인 특성을 비교하기 위한 Benchmark입니다.

---

# 29. 프로젝트를 통해 확인한 점

이 프로젝트를 통해 가장 중요하게 확인한 것은 동시성 전략에는 하나의 정답이 없다는 점입니다.

Atomic Update는 간단하면서 안정적이었고,

Pessimistic Lock은 높은 경합 환경에서 예상보다 좋은 성능을 보였습니다.

Optimistic Lock은 대부분의 요청은 빠르지만 Retry로 인해 Tail Latency가 크게 증가했습니다.

Redis는 높은 처리량을 얻을 수 있었지만 MySQL과의 Dual Write 문제라는 새로운 복잡도를 만들었습니다.

또한 Atomic 전략에서는 단순한 코드 분석만으로 발견하기 어려운:

```text
MySQL REPEATABLE_READ
+
MVCC Snapshot
```

문제를 실제 부하 테스트를 통해 발견했습니다.

따라서 최종적으로 중요하다고 판단한 것은:

```text
구현
↓
동시성 테스트
↓
HTTP 부하 테스트
↓
DB 상태 확인
↓
모니터링
↓
실패 원인 분석
```

까지 이어지는 검증 과정입니다.

---

# 30. 최종 결론

이 프로젝트는 선착순 신청 API를 만드는 것이 핵심이 아닙니다.

높은 경합 상황을 이용해:

```text
Lost Update
Atomic Update
Pessimistic Lock
Optimistic Lock
Retry
Redis Lua Script
Duplicate Race
MVCC
Dual Write
Observability
Load Test
```

를 하나의 흐름으로 직접 구현하고 검증하는 것이 목적입니다.

최종적으로 다음 과정을 경험했습니다.

```text
문제 재현
↓
원인 분석
↓
해결 전략 구현
↓
정확성 검증
↓
성능 측정
↓
예상하지 못한 장애 발견
↓
DB 내부 동작 분석
↓
수정
↓
재검증
↓
모니터링 및 문서화
```

즉 단순히 "동시성 제어를 적용했다"가 아니라,

> **어떤 문제가 발생했고, 왜 발생했으며, 여러 해결책이 어떤 트레이드오프를 가지는지를 실제 테스트 결과로 비교하는 것**

이 프로젝트의 핵심입니다.