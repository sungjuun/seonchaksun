# 선착순 프로젝트 수정 사항

## 핵심 변경

1. 이벤트 생성 시 `strategy`를 필수로 저장
2. 한 이벤트에서 다른 전략을 섞어 사용할 수 없도록 검증
3. 기본 `/entries` API도 이벤트에 저장된 전략을 자동 사용
4. 잘못된 전략 문자열은 `400 INVALID_STRATEGY` 반환
5. 다른 전략으로 요청하면 `400 STRATEGY_MISMATCH` 반환
6. Optimistic 전략의 동시 중복 신청 UNIQUE 오류를 `409 DUPLICATE_ENTRY`로 변환
7. `OptimisticEventEntryTransactionService`를 올바른 패키지 경로로 이동
8. `EventStatusService`의 Redis 조회 로직을 `RedisCapacityService`로 통일
9. Frontend의 Event ID 15 하드코딩 제거
10. Frontend에서 전략을 선택해 새 테스트 이벤트를 바로 생성하도록 추가
11. 생성된 이벤트에서는 전략 변경 UI 제거
12. k6 벤치마크용 이벤트 생성 시 전략도 함께 저장
13. README의 "200명이 동시에" 표현을 실제 테스트 조건에 맞게 수정

## DB Migration

새 파일:

`src/main/resources/db/migration/V4__add_event_strategy.sql`

기존 이벤트는 과거 어떤 전략으로 생성됐는지 알 수 없기 때문에 migration 시 `ATOMIC`으로 설정됩니다.
Redis/Pessimistic/Optimistic 테스트는 새 이벤트를 생성해서 사용하세요.

## 이벤트 생성 예시

```json
{
  "name": "Redis 테스트",
  "capacity": 100,
  "strategy": "REDIS",
  "openAt": "2026-09-01T00:00:00",
  "closeAt": "2026-12-31T23:59:59"
}
```

지원 전략:

- `ATOMIC`
- `PESSIMISTIC`
- `OPTIMISTIC`
- `REDIS`

## 적용 후 권장 확인

Windows 로컬 환경에서 다음을 실행하세요.

```powershell
.\gradlew.bat test
```

Frontend:

```powershell
cd frontend
npm.cmd install
npm.cmd run build
npm.cmd run dev
```

현재 ChatGPT 분석 환경에서는 Gradle 배포본 다운로드를 위한 외부 DNS가 차단되어 새 테스트 실행까지는 수행하지 못했습니다.
