# 선착순

> 200명이 동시에 신청해도, 정확하게 100명만 받을 수 있을까?

동시에 다수의 요청이 몰리는 선착순 신청 환경에서  

**정확한 정원 제어와 동시성 처리 전략을 비교·검증하는 백엔드 프로젝트**입니다.

단순 CRUD 기능을 늘리는 대신 하나의 핵심 문제인 **동시성 제어**를 깊게 다룹니다.

![선착순 처리 시스템 전체 화면](/docs/images/dashboard.png)



```text
가장 단순한 구현
→ 문제 재현
→ 원인 분석
→ 동시성 전략 적용
→ 정합성 검증
→ 부하 테스트
→ 장애 발견
→ 개선
→ 재측정
```

---

## 프로젝트에서 다룬 문제

정원이 100명인 이벤트에 200개의 요청이 동시에 들어오는 상황을 가정했습니다.

목표는 단순합니다.

```text
요청 수 = 200
정원 = 100

성공 = 100
정원 초과 실패 = 100
실제 저장된 신청 = 100
```

하지만 동시 요청 환경에서는 다음과 같은 문제가 발생합니다.

- Race Condition
- Lost Update
- 동일 사용자의 중복 신청
- DB Lock 경합
- Optimistic Lock Retry 비용
- Redis / MySQL Dual Write
- Redis 예약 후 DB 저장 실패
- Transaction Isolation에 따른 예상하지 못한 조회 결과

이 문제를 직접 재현하고 여러 동시성 제어 전략을 적용해 비교했습니다.

---

## Demo UI

백엔드 동시성 처리 전략을 직접 선택하고 테스트할 수 있도록  
React 기반의 데모 화면을 구현했습니다.

주요 기능:

- Event ID 기반 이벤트 조회
- Atomic Update 전략 신청
- Pessimistic Lock 전략 신청
- Optimistic Lock 전략 신청
- Redis + MySQL 전략 신청
- 사용자 ID 기반 실제 신청 API 호출
- 전략별 현재 신청 인원 확인
- 남은 정원 확인
- MySQL / Redis Count Source 표시
- k6 HTTP 부하 테스트 결과 시각화
- Atomic Update 장애 개선 Before / After 표시

프론트엔드는 별도 서비스 기능을 확장하기 위한 목적이 아니라  
**백엔드 동시성 처리 방식과 실험 결과를 쉽게 확인하기 위한 Demo Console**로 구성했습니다.

<!-- 최종 스크린샷 추가 후 주석 제거
![선착순 동시성 데모 UI](docs/images/dashboard.png)
-->

---

## 기술 스택

### Backend

- Java 17
- Spring Boot 4.1.0
- Spring Data JPA
- Spring Web MVC
- Bean Validation
- Flyway
- Gradle 9.5.1

### Database / Infrastructure

- MySQL 8.4
- Redis 8
- Docker

### Test

- JUnit 5
- Mockito
- Testcontainers 2.0.5
- k6

### Frontend

- React
- Vite
- JavaScript
- CSS

---

# 문제 재현

## Naive Read-Modify-Write

최초 구현은 일반적인 JPA Read-Modify-Write 방식이었습니다.

```text
Event 조회
→ currentCount 확인
→ currentCount + 1
→ EventEntry 저장
```

다음 조건으로 동시 요청 테스트를 수행했습니다.

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
실제 EventEntry 수 = 40
```

실제로 신청 데이터는 40건 저장되었지만  
이벤트의 `currentCount`는 20만 증가했습니다.

여러 Transaction이 동일한 값을 읽고 수정하면서  
다른 Transaction의 결과를 덮어쓰는 **Lost Update**가 발생했습니다.

테스트 과정에서는 MySQL Deadlock도 관찰했습니다.

단, InnoDB Deadlock Graph를 별도로 수집하지 않았기 때문에  
Deadlock의 정확한 쿼리 관계까지 단정하지는 않습니다.

---

# 동시성 제어 전략

총 네 가지 전략을 구현하고 동일한 요구사항에서 비교했습니다.

```text
Atomic Update

Pessimistic Lock

Optimistic Lock

Redis Lua Script + MySQL
```

---

## 1. Atomic Update

정원 확인과 신청 인원 증가를 하나의 UPDATE 연산으로 처리합니다.

```sql
UPDATE events
SET current_count = current_count + 1,
    version = version + 1
WHERE id = ?
  AND current_count < capacity
  AND open_at <= ?
  AND close_at > ?;
```

기존 방식은:

```text
SELECT
→ 애플리케이션에서 조건 확인
→ UPDATE
```

였습니다.

Atomic 전략에서는:

```text
조건 확인 + 증가
→ 하나의 UPDATE
```

로 처리합니다.

### 장점

- 구현 구조가 비교적 단순
- 외부 인프라 불필요
- Lost Update 방지
- 별도의 Retry 불필요

### 고려사항

- 모든 성공 요청이 동일 Event row를 수정
- 높은 트래픽에서는 Hot Row Contention 발생 가능
- JPQL Bulk Update는 JPA Dirty Checking을 우회
- `@Version`을 사용할 경우 version 처리에 주의 필요

현재 구현에서는 Atomic Bulk Update 시 `version`도 함께 증가시킵니다.

---

## 2. Pessimistic Lock

Event를 조회할 때 `PESSIMISTIC_WRITE` Lock을 획득합니다.

```text
SELECT ... FOR UPDATE

→ Lock 획득
→ 신청 처리
→ COMMIT
→ 다음 Transaction 처리
```

### 장점

- 충돌을 사전에 방지
- 처리 흐름이 직관적
- 높은 충돌 환경에서 Retry 비용 없음

### 고려사항

- Lock 대기 발생
- Transaction이 길어지면 성능 저하
- DB Connection 점유 시간이 증가할 수 있음

이번 프로젝트처럼 Critical Section이 짧은 환경에서는  
Pessimistic Lock도 비교적 안정적인 성능을 보였습니다.

---

## 3. Optimistic Lock

Event에 `@Version`을 적용하고  
동시에 같은 데이터를 수정했는지 version으로 확인합니다.

```text
SELECT
→ Event 수정
→ UPDATE WHERE version = ?
→ 충돌 발생
→ Rollback
→ Backoff
→ Retry
```

현재 구현에서는 다음 예외를 Retry 대상으로 처리합니다.

```text
ObjectOptimisticLockingFailureException
CannotAcquireLockException
```

그리고 재충돌을 완화하기 위해 Random Backoff를 적용했습니다.

### 장점

- 충돌이 적은 환경에서 DB Lock 대기 최소화
- Read 비중이 높은 환경에서 유리할 수 있음

### 고려사항

- 높은 충돌 환경에서는 Retry 폭증
- SELECT / UPDATE / Rollback 비용 반복
- Hot Row 구조에서는 성능 저하 가능

이번 테스트에서는 네 전략 중 가장 높은 응답시간을 기록했습니다.

---

## 4. Redis Lua Script + MySQL

Redis에서 정원 경쟁을 처리하고  
MySQL은 실제 신청 내역을 저장하도록 역할을 분리했습니다.

```text
Redis
→ 정원 Admission Control

MySQL
→ EventEntry 영속화
```

Redis에서는 다음 연산이 필요합니다.

```text
현재 신청 수 조회
→ 정원과 비교
→ 자리가 있으면 +1
```

이를 Lua Script 하나로 처리합니다.

```lua
local current = tonumber(redis.call('GET', KEYS[1]) or '0')
local capacity = tonumber(ARGV[1])

if current >= capacity then
    return 0
end

redis.call('INCR', KEYS[1])

return 1
```

Redis는 Lua Script 실행을 원자적으로 처리하므로:

```text
GET
→ 비교
→ INCR
```

사이에 다른 요청이 끼어들지 않습니다.

### Redis 전략의 Count Source

DB 기반 전략:

```text
Event.currentCount
```

Redis 전략:

```text
Redis Counter
```

따라서 Redis 전략에서는 `events.current_count`를 증가시키지 않습니다.

정합성 기준도 다음과 같이 다릅니다.

DB 전략:

```text
Event.currentCount == EventEntry count
```

Redis 전략:

```text
Redis count == EventEntry count
```

Demo UI에서도 현재 선택 전략에 따라  
Count Source를 `MYSQL` 또는 `REDIS`로 표시합니다.

---

# 중복 신청 Race Condition

다음 검증만으로는 동시 요청에서 중복 신청을 완전히 차단할 수 없습니다.

```text
existsByEventIdAndUserId()
→ false
→ INSERT
```

두 Transaction이 동시에 `false`를 확인할 수 있기 때문입니다.

따라서 DB에 최종 방어선으로 UNIQUE Constraint를 적용했습니다.

```text
UNIQUE(event_id, user_id)
```

동일 사용자가 동시에 20번 요청한 테스트 결과:

```text
요청 = 20

성공 = 1
중복 실패 = 19
예상하지 못한 실패 = 0
```

DB 기반 전략에서는:

```text
Event.currentCount = 1
EventEntry count = 1
```

Redis 전략에서는:

```text
Redis count = 1
EventEntry count = 1
```

을 유지했습니다.

---

# Redis + MySQL Dual Write

Redis 전략은 성능상의 장점이 있지만  
새로운 정합성 문제가 발생합니다.

Redis와 MySQL은 하나의 로컬 DB Transaction으로 묶이지 않습니다.

예를 들어:

```text
Redis reserve 성공
0 → 1

↓

MySQL EventEntry 저장 실패

↓

Redis에는 1이 남을 수 있음
```

이 상태가 지속되면:

```text
Redis count > 실제 신청자 수
```

가 되어 실제로 사용 가능한 정원이 사라질 수 있습니다.

---

## 현재 보상 처리

현재 구현에서는 DB 저장 실패 시 Redis Counter를 감소시킵니다.

```text
Redis reserve

↓

EventEntry saveAndFlush

├─ 성공
│   └─ 종료
│
└─ 실패
    └─ Redis release
```

release 역시 Lua Script로 처리합니다.

```text
현재 Counter 조회
→ 0보다 크면 DECR
```

Mock을 이용해 DB 저장 실패를 발생시킨 테스트에서도:

```text
Redis reserve
→ DB 저장 실패
→ Redis release

최종 Redis count = 0
```

을 확인했습니다.

---

## 남아 있는 한계

현재 보상 처리가 모든 장애를 해결하는 것은 아닙니다.

예를 들어:

```text
Redis reserve 성공

↓

MySQL saveAndFlush 성공

↓

Service method 종료

↓

실제 Transaction Commit 실패
```

가 발생하면 Service 내부의 `catch`에서는 이를 감지할 수 없습니다.

또한:

```text
DB 실패
→ Redis release 시도
→ Redis 장애
```

상황도 발생할 수 있습니다.

향후 필요하다면 다음 구조를 검토할 수 있습니다.

- TransactionSynchronization
- Retry Queue
- Reconciliation
- Outbox Pattern
- 비동기 정합성 복구

XA / 2PC는 현재 프로젝트 규모에서는 과도한 복잡도라고 판단해 적용하지 않았습니다.

---

# Integration Test 기준 성능 비교

먼저 Spring Integration Test 환경에서  
각 전략을 동일한 조건으로 5회 반복 측정했습니다.

```text
요청 Task = 200
정원 = 100
최대 Worker Thread = 32
전략별 반복 = 5회
```

| Strategy | Average | Min | Max |
|---|---:|---:|---:|
| Redis + MySQL | **183.40 ms** | 153 ms | 231 ms |
| Pessimistic Lock | 670.60 ms | 634 ms | 759 ms |
| Atomic Update | 683.60 ms | 568 ms | 920 ms |
| Optimistic Lock | 1096.40 ms | 992 ms | 1196 ms |

이 테스트는 Spring Service / Transaction / MySQL / Redis를 포함한  
**로컬 Integration-level 동시성 비교**입니다.

HTTP 요청 자체는 포함하지 않습니다.

---

# HTTP API 기준 k6 부하 테스트

Integration Test 이후 실제 HTTP API를 대상으로  
k6 부하 테스트를 수행했습니다.

## 테스트 조건

```text
Executor = shared-iterations

총 요청 = 200
VUs = 32
Event Capacity = 100

Expected:
Success = 100
Business Failure = 100
Unexpected Failure = 0
```

테스트에서는 요청마다 서로 다른 User ID를 사용했습니다.

```text
userId =
exec.scenario.iterationInTest + 1
```

따라서 중복 사용자로 인한 실패가 아니라  
정원 경쟁 자체를 측정합니다.

---

## 최종 결과

| Strategy | Avg | p95 | p99 | Req/s | Unexpected |
|---|---:|---:|---:|---:|---:|
| **Redis + MySQL** | **30.89 ms** | **46.34 ms** | **50.10 ms** | **924.39** | 0 |
| Pessimistic Lock | 118.94 ms | 200.29 ms | 221.20 ms | 257.87 | 0 |
| Atomic Update | 123.38 ms | 230.27 ms | 269.33 ms | 247.34 | 0 |
| Optimistic Lock | 163.45 ms | 588.31 ms | 659.14 ms | 191.93 | 0 |

모든 최종 결과에서:

```text
Success = 100
Business Failure = 100
Unexpected Failure = 0
```

을 만족했습니다.

---

## 결과 해석

### Redis + MySQL

현재 로컬 HTTP 테스트에서는:

```text
Avg = 30.89 ms
Req/s = 924.39
```

로 가장 좋은 결과를 기록했습니다.

DB 기반 전략에서는 성공 요청이 동일한 `events.current_count`를 수정하지만,  
Redis 전략에서는 정원 경쟁이 Redis Lua Script에서 처리됩니다.

MySQL에는 서로 다른 `event_entries` row가 INSERT되므로  
동일 Event row의 Write Contention을 줄일 수 있었습니다.

다만 Redis 전략은:

- Redis 운영
- Redis / MySQL Dual Write
- Compensation
- 장애 복구

라는 추가 복잡도를 갖습니다.

따라서 단순히 가장 빠르다는 이유만으로  
항상 Redis를 선택해야 한다는 의미는 아닙니다.

---

### Pessimistic Lock

```text
Avg = 118.94 ms
Req/s = 257.87
```

현재 테스트에서는 Atomic Update와 큰 차이가 없었습니다.

따라서:

> Pessimistic Lock은 무조건 느리다.

라고 결론 내리지 않습니다.

이번 프로젝트의 Critical Section이 짧다는 점이 결과에 영향을 준 것으로 판단합니다.

---

### Atomic Update

```text
Avg = 123.38 ms
Req/s = 247.34
```

외부 인프라 없이 비교적 단순한 구조로 정합성을 보장할 수 있습니다.

다만 동일 Event row를 계속 수정하기 때문에  
트래픽이 증가하면 Hot Row 경합이 발생할 수 있습니다.

---

### Optimistic Lock

```text
Avg = 163.45 ms
p95 = 588.31 ms
p99 = 659.14 ms
```

평균 응답시간뿐 아니라 Tail Latency도 가장 높았습니다.

하나의 Event row에 요청이 집중되면서:

```text
Version Conflict
→ Rollback
→ Retry
→ Backoff
```

비용이 반복된 것이 주요 원인으로 판단합니다.

---

# 부하 테스트에서 발견한 Atomic Update 장애

k6 테스트 과정에서 Atomic Update 전략에  
예상하지 못한 HTTP 500 응답이 발생했습니다.

초기 결과:

```text
Success = 100
Business Failure = 91
Unexpected Failure = 9
```

서버 로그에서는 다음 예외를 확인했습니다.

```text
IllegalStateException:
이벤트 신청 실패 원인을 확인할 수 없습니다.
```

---

## 문제 상황

Atomic Update는 다음 조건으로 정원을 확보합니다.

```text
UPDATE Event
SET currentCount = currentCount + 1
WHERE currentCount < capacity
```

정원이 모두 찼다면:

```text
updated = 0
```

이 됩니다.

Service는 `updated == 0`인 경우  
왜 신청이 실패했는지 확인하기 위해 Event를 다시 조회했습니다.

기존 구조:

```text
Atomic UPDATE

↓

updated == 0

↓

일반 SELECT

↓

신청 기간 / 정원 상태 확인
```

---

## 원인

MySQL Transaction Isolation Level은:

```text
REPEATABLE_READ
```

였습니다.

Atomic UPDATE는 Locking / Current Read 특성상  
최신 row 상태를 기준으로 조건을 평가할 수 있습니다.

반면 이후 수행된 일반 SELECT는  
같은 Transaction의 이전 Consistent Read Snapshot을 볼 수 있습니다.

따라서 다음 상황이 가능했습니다.

```text
실제 DB 최신 상태

current_count = 100
capacity = 100

↓

Atomic UPDATE

조건 불만족
updated = 0

↓

실패 원인 확인 SELECT

이전 Snapshot
current_count = 95

↓

아직 정원이 남은 것으로 판단

↓

실패 이유를 찾지 못함

↓

IllegalStateException

↓

HTTP 500
```

`clearAutomatically = true`로 Persistence Context를 비우더라도  
DB Transaction의 MVCC Snapshot 자체가 새로 만들어지는 것은 아니었습니다.

---

# Atomic Update 실패 경로 개선

실패 원인을 확인할 때 일반 SELECT 대신  
Locking Read를 사용하도록 변경했습니다.

기존:

```text
findById()
```

개선:

```text
findByIdWithPessimisticLock()
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

이 조회는 Atomic Update의 **실패 원인 판별 경로에서만 사용합니다.**

정상 처리 경로는 여전히:

```text
Atomic Conditional UPDATE
```

이므로 전체 전략이 Pessimistic Lock으로 변경된 것은 아닙니다.

---

## 개선 전 / 후

### Before

```text
Success = 100
Business Failure = 91
Unexpected Failure = 9
```

### After

```text
Success = 100
Business Failure = 100
Unexpected Failure = 0
```

최종 Atomic k6 결과:

```text
Avg = 123.38 ms
p95 = 230.27 ms
p99 = 269.33 ms
Req/s = 247.34
Unexpected Failure = 0
```

성능 수치는 일부 감소했지만  
예상하지 못한 HTTP 500을 제거해 **정확성과 신뢰성을 우선했습니다.**

이 과정은 이번 프로젝트에서 중요한 경험 중 하나였습니다.

```text
부하 테스트

↓

HTTP 500 발견

↓

서버 로그 분석

↓

REPEATABLE READ / MVCC 분석

↓

Locking Read 적용

↓

재테스트

↓

Unexpected Failure
9 → 0
```

---

# API

## 이벤트 생성

```http
POST /api/events
```

예시:

```json
{
  "name": "한정판 키보드 사전예약",
  "capacity": 100,
  "openAt": "2026-08-10T14:00:00",
  "closeAt": "2026-08-10T18:00:00"
}
```

---

## 이벤트 조회

```http
GET /api/events/{eventId}
```

---

## 이벤트 신청

기본 전략:

```http
POST /api/events/{eventId}/entries
```

전략 선택:

```http
POST /api/events/{eventId}/entries/strategies/{strategy}
```

지원 전략:

```text
atomic
pessimistic
optimistic
redis
```

Request:

```json
{
  "userId": 1001
}
```

---

## 전략별 현재 상태 조회

```http
GET /api/events/{eventId}/status?strategy={strategy}
```

DB 기반 전략 응답 예:

```json
{
  "eventId": 15,
  "capacity": 100,
  "currentCount": 4,
  "remainingCount": 96,
  "countSource": "MYSQL"
}
```

Redis 전략 응답 예:

```json
{
  "eventId": 15,
  "capacity": 100,
  "currentCount": 1,
  "remainingCount": 99,
  "countSource": "REDIS"
}
```

---

# 프로젝트 구조

```text
seonchaksun
├─ src
│  ├─ main
│  │  ├─ java
│  │  │  └─ com.seonchaksun
│  │  │     ├─ event
│  │  │     ├─ entry
│  │  │     └─ common
│  │  └─ resources
│  │     └─ db
│  │        └─ migration
│  │
│  └─ test
│
├─ frontend
│  ├─ src
│  │  ├─ api
│  │  ├─ components
│  │  ├─ App.jsx
│  │  └─ App.css
│  └─ public
│
├─ k6
│  └─ entry-burst.js
│
├─ docs
│  └─ concurrency-strategy.md
│
├─ build.gradle
└─ README.md
```

---

# 로컬 실행

## 1. MySQL

```powershell
docker run -d `
  --name seonchaksun-mysql `
  -e MYSQL_DATABASE=seonchaksun `
  -e MYSQL_USER=seonchaksun `
  -e MYSQL_PASSWORD=seonchaksun `
  -e MYSQL_ROOT_PASSWORD=root `
  -p 3307:3306 `
  mysql:8.4
```

이미 Container가 존재한다면:

```powershell
docker start seonchaksun-mysql
```

---

## 2. Redis

```powershell
docker run -d `
  --name seonchaksun-redis `
  -p 6379:6379 `
  redis:8
```

이미 Container가 존재한다면:

```powershell
docker start seonchaksun-redis
```

Redis 연결 확인:

```powershell
docker exec -it seonchaksun-redis redis-cli ping
```

결과:

```text
PONG
```

---

## 3. Backend

IntelliJ에서:

```text
SeonchaksunApplication
```

을 실행합니다.

Backend:

```text
http://localhost:8080
```

---

## 4. Frontend

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

Frontend:

```text
http://localhost:5173
```

---

# 테스트

전체 테스트:

```powershell
.\gradlew.bat test
```

Clean Test:

```powershell
.\gradlew.bat clean test
```

---

# k6 테스트

예:

```powershell
k6 run `
  -e EVENT_ID=15 `
  -e STRATEGY=redis `
  .\k6\entry-burst.js
```

전략:

```text
atomic
pessimistic
optimistic
redis
```

k6 테스트는 `shared-iterations` 방식이며  
32 VU가 총 200개의 요청을 처리합니다.

따라서 **200개의 요청이 정확히 같은 순간에 시작된다는 의미는 아닙니다.**

---

# 테스트에서 중요하게 확인한 불변식

## DB 기반 전략

```text
Success
==
Event.currentCount
==
EventEntry count
<=
capacity
```

## Redis 전략

```text
Success
==
Redis count
==
EventEntry count
<=
capacity
```

## 동일 사용자

```text
동일 사용자 N회 동시 신청

→ 성공 = 1
→ EventEntry = 1
```

---

# 현재 결론

이번 프로젝트를 통해 얻은 결론은 다음과 같습니다.

1. 일반적인 JPA Read-Modify-Write 방식은 높은 동시성에서 Lost Update가 발생할 수 있습니다.

2. 동시성 문제는 단순히 `synchronized`나 특정 Lock 하나를 선택하는 문제가 아니라 데이터 저장 위치와 Transaction 경계를 함께 고려해야 합니다.

3. Atomic Update는 외부 인프라 없이 비교적 단순하게 높은 정합성을 제공할 수 있습니다.

4. Pessimistic Lock은 높은 충돌 환경에서도 충분히 실용적인 선택이 될 수 있으며, Lock이라는 이유만으로 항상 느리다고 볼 수 없습니다.

5. Optimistic Lock은 충돌이 적을 때 장점이 있지만 Hot Row 환경에서는 Retry 비용이 크게 증가할 수 있습니다.

6. Redis Lua Script로 정원 경쟁을 DB 밖으로 이동시키자 이번 로컬 테스트에서는 가장 높은 처리량을 기록했습니다.

7. Redis 도입은 성능 개선 대신 Redis / MySQL Dual Write와 장애 복구라는 새로운 복잡성을 만듭니다.

8. DB UNIQUE Constraint는 동시 중복 신청을 막기 위한 최종 불변식 방어선으로 사용했습니다.

9. 부하 테스트는 단순히 성능 수치를 얻기 위한 작업이 아니라 실제 HTTP 500 장애를 발견하고 Transaction Isolation / MVCC 문제를 분석하는 도구로 활용할 수 있었습니다.

10. 최종 동시성 전략은 기술의 이름이나 벤치마크 순위가 아니라 **정합성 요구사항, 경합 정도, 장애 시나리오, 인프라 복잡도와 실제 측정 결과를 함께 고려해 선택해야 합니다.**

---

# 상세 문서

구현 과정과 각 전략의 상세 설계는 다음 문서에서 확인할 수 있습니다.

[동시성 전략 설계 문서](docs/concurrency-strategy.md)

---

# 다음 단계

- Swagger / OpenAPI 문서화
- Docker Compose 기반 실행 환경 구성
- Redis / MySQL 보상 실패 시나리오 고도화
- TransactionSynchronization 기반 Commit 이후 처리 검토
- Redis / DB Reconciliation 구조 검토
- 운영 관측성을 위한 Metrics 추가 검토

---

## Disclaimer

본 프로젝트의 성능 수치는 로컬 개발 환경에서 수행한 실험 결과입니다.

```text
Spring Boot
MySQL
Redis
k6
```

의 환경과 실행 조건에 따라 결과는 달라질 수 있습니다.

따라서 절대적인 성능 수치보다는  
**동일한 조건에서 각 동시성 전략이 어떤 특성을 보이는지 비교하는 용도**로 해석합니다.