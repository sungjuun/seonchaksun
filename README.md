# 선착순

동시에 다수의 요청이 발생하는 환경에서 공정하고 정확한 선착순 처리를 보장하기 위한 백엔드 프로젝트입니다.

## 프로젝트 목표

단순한 예약 서비스를 구현하는 것이 아니라, 선착순 처리 과정에서 발생하는 동시성 문제를 재현하고 여러 해결 방법을 비교합니다.

## 핵심 학습 주제

- Race Condition
- Transaction
- Isolation Level
- Pessimistic Lock
- Optimistic Lock
- Redis Distributed Lock
- Idempotency
- 부하 테스트
- 성능 측정
- 모니터링

## 기술 스택

- Java 17
- Spring Boot 3
- Spring Data JPA
- MySQL
- Redis
- Docker
- JUnit 5
- Testcontainers
- Gradle

## 로컬 실행

### MySQL 실행

```bash
docker compose up -d