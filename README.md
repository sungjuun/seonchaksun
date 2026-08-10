# 선착순

동시에 다수의 요청이 몰리는 선착순 신청 환경에서 **정확한 정원 제어와 동시성 처리 전략을 비교·검증하는 백엔드 프로젝트**입니다.

단순 CRUD 기능을 늘리는 대신, 하나의 핵심 문제인 **동시성 제어**를 깊게 다룹니다.

> 가장 단순한 구현 → 문제 재현 → 원인 분석 → 개선 → 측정 → 설계 비교

---

## 프로젝트 목표

선착순 이벤트에서 다음과 같은 문제를 직접 재현하고 해결합니다.

- 여러 요청이 동시에 같은 이벤트에 접근할 때 발생하는 Race Condition
- Read-Modify-Write 방식에서 발생하는 Lost Update
- 동일 사용자의 중복 신청 Race Condition
- DB Lock / Atomic Update / Redis Lua Script의 차이
- 높은 경합 상황에서 전략별 처리 시간 차이
- Redis와 MySQL을 함께 사용할 때 발생하는 Dual Write 정합성 문제
- DB 저장 실패 시 Redis 예약을 되돌리는 보상 처리

---

## 기술 스택

- Java 17
- Spring Boot 4.1.0
- Spring Data JPA
- MySQL 8.4
- Redis 8
- Flyway
- Docker
- JUnit 5
- Mockito
- Testcontainers 2.x
- Gradle 9.5.1

---

## 문제 재현

최초 구현은 일반적인 JPA Read-Modify-Write 방식이었습니다.

```text
Event 조회
→ currentCount 확인
→ currentCount + 1
→ EventEntry 저장
```

동일 이벤트에 대해 다음 조건으로 동시 요청을 발생시켰습니다.

```text
요청 수: 200
정원: 100
최대 동시 Thread: 32
```

최초 Naive 구현 결과:

```text
서비스 성공 수 = 40
서비스 실패 수 = 160
Event.currentCount = 20
실제 EventEntry 수 = 40
```

40건의 신청이 실제 저장되었지만 `currentCount`는 20만 증가했습니다.

즉, 여러 트랜잭션이 같은 값을 읽고 갱신하면서 **Lost Update**가 발생했습니다. 테스트 과정에서는 MySQL deadlock도 함께 관찰되었습니다.

---

## 적용한 동시성 전략

### 1. Atomic Update

정원 확인과 카운트 증가를 하나의 UPDATE 쿼리로 처리합니다.

```sql
UPDATE events
SET current_count = current_count + 1
WHERE id = ?
  AND current_count < capacity;
```

애플리케이션에서 `조회 → 판단 → 수정`하지 않고 DB가 하나의 원자적 연산으로 처리하도록 변경했습니다.

### 2. Pessimistic Lock

이벤트 조회 시 `PESSIMISTIC_WRITE` Lock을 획득해 동일 Event row에 대한 접근을 직렬화합니다.

장점은 동작이 직관적이고 충돌을 사전에 막을 수 있다는 점이지만, Lock 대기 시간이 발생할 수 있습니다.

### 3. Optimistic Lock

`@Version`을 사용해 충돌을 감지하고 실패한 요청을 Retry + Random Backoff 방식으로 재시도합니다.

경합이 낮은 환경에서는 유효한 방식이지만, 이번 프로젝트처럼 하나의 인기 Event row에 요청이 집중되는 환경에서는 충돌과 재시도 비용이 크게 발생했습니다.

### 4. Redis Lua Script + MySQL

Redis Lua Script에서 다음 연산을 하나의 원자적 작업으로 수행합니다.

```text
GET current
→ capacity 비교
→ INCR
```

정원 경쟁을 MySQL의 동일 Event row에서 Redis로 이동시키고, 실제 신청 내역은 MySQL `event_entries`에 저장합니다.

DB 저장에 실패한 경우 이미 증가한 Redis Counter를 `release` Lua Script로 감소시키는 **보상 처리**를 추가했습니다.

---

## 중복 신청 Race Condition

애플리케이션의 다음 검증만으로는 동시 요청에서 중복 신청을 완전히 막을 수 없습니다.

```text
existsByEventIdAndUserId()
→ false
→ INSERT
```

동일 사용자의 두 트랜잭션이 모두 `false`를 확인할 수 있기 때문입니다.

따라서 DB에 다음 UNIQUE Constraint를 두어 최종 불변식을 보장합니다.

```text
UNIQUE(event_id, user_id)
```

동일 사용자가 동시에 20번 신청하도록 테스트한 결과:

```text
동일 사용자 요청 수 = 20
성공 수 = 1
중복 신청 실패 수 = 19
예상하지 못한 실패 수 = 0
Redis count = 1
실제 EventEntry 수 = 1
```

Redis 예약 후 DB UNIQUE Constraint에서 중복이 확인되면 Redis Counter를 다시 감소시켜 정합성을 유지합니다.

---

## 동시성 전략 성능 비교

다음과 같은 동일 조건으로 각 전략을 5회 반복 측정했습니다.

```text
요청 수: 200
정원: 100
최대 동시 Thread: 32
측정 횟수: 전략별 5회
```

모든 측정은 정합성 검증을 통과한 결과만 사용했습니다.

| Strategy | Average | Min | Max |
|---|---:|---:|---:|
| Redis + MySQL | **183.40 ms** | 153 ms | 231 ms |
| Pessimistic Lock | 670.60 ms | 634 ms | 759 ms |
| Atomic Update | 683.60 ms | 568 ms | 920 ms |
| Optimistic Lock | 1096.40 ms | 992 ms | 1196 ms |

현재 로컬 통합 테스트 환경에서는 **Redis + MySQL 전략이 가장 짧은 처리시간**을 기록했습니다.

DB 기반 전략은 동일한 `events` row의 `current_count`를 갱신하면서 hot-row contention이 발생합니다. 반면 Redis 전략은 정원 경쟁을 Redis Lua Script로 이동시키고 MySQL에서는 개별 `EventEntry`를 저장하기 때문에 동일 row의 쓰기 경쟁을 줄일 수 있었습니다.

단, 이 수치는 로컬 환경에서 수행한 **integration-level concurrency comparison**입니다. HTTP 요청, 실제 네트워크 환경, 다중 애플리케이션 인스턴스, 운영 수준의 Redis/MySQL 구성까지 포함한 최종 성능 벤치마크는 아닙니다.

---

## Redis를 사용하면서 새롭게 발생한 문제

Redis 전략은 이번 실험에서 가장 좋은 처리 시간을 기록했지만 복잡도가 증가했습니다.

Redis와 MySQL은 하나의 로컬 트랜잭션으로 묶이지 않습니다.

```text
Redis 예약 성공
→ MySQL 저장 실패
→ Redis에는 예약 수가 남을 수 있음
```

이를 줄이기 위해 현재는 다음 보상 처리를 적용합니다.

```text
Redis reserve
→ MySQL EventEntry 저장
→ 성공: 종료

MySQL 저장 실패
→ Redis release
→ 예외 처리
```

그러나 이 방식도 완전한 분산 트랜잭션을 제공하지 않습니다.

예를 들어 MySQL `flush` 이후 실제 transaction commit 단계에서 장애가 발생하거나, 보상 과정에서 Redis가 장애가 나면 Redis와 MySQL의 상태가 달라질 가능성이 있습니다.

따라서 **Redis가 단순히 더 빠르다는 이유만으로 무조건 최종 선택이 되는 것은 아니며**, 처리량과 함께 운영 복잡도, 장애 복구, 정합성 복구 전략을 고려해야 합니다.

상세한 설계와 실험 과정은 [동시성 전략 설계 문서](docs/concurrency-strategy.md)에 정리합니다.

---

## 현재 결론

이번 실험에서 얻은 핵심 결론은 다음과 같습니다.

1. 단순 JPA Read-Modify-Write 방식은 높은 동시성에서 Lost Update가 발생할 수 있습니다.
2. Atomic Update, Pessimistic Lock, Optimistic Lock 모두 정합성을 보장할 수 있지만 경합 특성에 따라 비용이 다릅니다.
3. 높은 hot-row contention 환경에서는 Optimistic Lock의 retry 비용이 크게 나타났습니다.
4. Redis Lua Script를 이용해 정원 경쟁을 DB 밖으로 이동시키자 이번 테스트에서는 처리시간이 크게 감소했습니다.
5. Redis 도입은 성능 문제를 완화하는 대신 Redis/MySQL Dual Write와 보상 처리라는 새로운 복잡도를 만듭니다.
6. 따라서 동시성 전략은 이름이나 유행이 아니라 **요구사항, 경합 정도, 장애 시나리오와 실제 측정 결과를 기준으로 선택해야 합니다.**

---

## 다음 단계

- Redis/MySQL 장애 및 보상 실패 시나리오 테스트
- HTTP API 기준 k6 부하 테스트
- TPS / 평균 응답시간 / p95 / p99 비교
- Swagger API 문서화
- Docker 실행 환경 정리
- 필요 시 관측성 지표 추가

---

## 로컬 인프라

### MySQL

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

### Redis

```powershell
docker run -d `
  --name seonchaksun-redis `
  -p 6379:6379 `
  redis:8
```

Redis 연결 확인:

```powershell
docker exec -it seonchaksun-redis redis-cli ping
```

```text
PONG
```

### Test

```powershell
.\gradlew.bat clean test
```
