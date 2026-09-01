const benchmarks = [
    {
        strategy: "Redis 선점 방식",
        technicalName: "Redis",
        avg: 20.29,
        p95: 30.36,
        p99: 36.96,
        rps: 1390.39,
        width: 19,
        rank: "01",
    },
    {
        strategy: "DB 잠금 방식",
        technicalName: "Pessimistic Lock",
        avg: 79.07,
        p95: 138.40,
        p99: 139.73,
        rps: 385.32,
        width: 74,
        rank: "02",
    },
    {
        strategy: "조건부 업데이트",
        technicalName: "Atomic Update",
        avg: 82.40,
        p95: 128.95,
        p99: 130.27,
        rps: 370.47,
        width: 78,
        rank: "03",
    },
    {
        strategy: "버전 충돌 재시도",
        technicalName: "Optimistic Lock",
        avg: 106.13,
        p95: 367.16,
        p99: 547.19,
        rps: 295.46,
        width: 100,
        rank: "04",
    },
];

function BenchmarkSection() {

    return (
        <section className="benchmark-section">

            <div className="benchmark-heading">

                <div>

                    <span className="section-kicker">
                        성능 비교
                    </span>

                    <h2>
                        동시성 처리 방식 성능 비교
                    </h2>

                    <p>
                        k6 · 요청 200건 · 동시 사용자 32명 · 정원 100명 ·
                        1회 예열 후 5회 평균
                    </p>

                </div>

                <div className="benchmark-summary">

                    <span>
                        최고 처리량
                    </span>

                    <strong>
                        Redis 선점 방식
                    </strong>

                    <em>
                        1390.39 req/s
                    </em>

                </div>

            </div>

            <div className="benchmark-table-card">

                <div className="benchmark-table-head">

                    <span>
                        처리 방식
                    </span>

                    <span>
                        평균 응답
                    </span>

                    <span>
                        상위 95%
                    </span>

                    <span>
                        상위 99%
                    </span>

                    <span>
                        초당 처리량
                    </span>

                </div>

                {benchmarks.map(
                    (item) => (

                        <div
                            className="benchmark-row"
                            key={
                                item.strategy
                            }
                        >

                            <div className="benchmark-strategy">

                                <span className="benchmark-rank">
                                    {item.rank}
                                </span>

                                <div>

                                    <strong>
                                        {item.strategy}
                                    </strong>

                                    <small>
                                        {item.technicalName}
                                    </small>

                                    <div className="benchmark-bar">

                                        <span
                                            style={{
                                                width:
                                                    `${item.width}%`,
                                            }}
                                        />

                                    </div>

                                </div>

                            </div>

                            <span>
                                {item.avg}
                                <small>
                                    ms
                                </small>
                            </span>

                            <span>
                                {item.p95}
                                <small>
                                    ms
                                </small>
                            </span>

                            <span>
                                {item.p99}
                                <small>
                                    ms
                                </small>
                            </span>

                            <strong>
                                {item.rps}
                            </strong>

                        </div>

                    )
                )}

            </div>

            <div className="incident-card">

                <div className="incident-copy">

                    <span className="section-kicker">
                        장애 분석
                    </span>

                    <h3>
                        조건부 업데이트 실패 경로 개선
                    </h3>

                    <p>
                        MySQL의 REPEATABLE READ 환경에서
                        신청 실패 원인을 확인하는 과정 중 이전 데이터를 조회해
                        HTTP 500이 발생하는 문제를 부하 테스트로 발견했습니다.
                        실패 원인 조회에 Locking Read를 적용해
                        시스템 오류를 제거했습니다.
                    </p>

                    <div className="incident-tags">

                        <span>
                            MySQL MVCC
                        </span>

                        <span>
                            REPEATABLE READ
                        </span>

                        <span>
                            Locking Read
                        </span>

                    </div>

                </div>

                <div className="incident-stats">

                    <div className="incident-stat before">

                        <span>
                            개선 전
                        </span>

                        <strong>
                            9
                        </strong>

                        <p>
                            시스템 오류
                            HTTP 500
                        </p>

                    </div>

                    <div className="incident-arrow">
                        →
                    </div>

                    <div className="incident-stat after">

                        <span>
                            개선 후
                        </span>

                        <strong>
                            0
                        </strong>

                        <p>
                            시스템 오류
                        </p>

                    </div>

                </div>

            </div>

            <p className="benchmark-footnote">
                * 로컬 Docker 환경에서 1회 예열 후
                5회 반복 측정한 평균값입니다.
                실제 운영 환경의 절대적인 성능이 아니라
                각 동시성 처리 방식의 상대적인 특성을 비교하기 위한 결과입니다.
            </p>

        </section>
    );
}

export default BenchmarkSection;