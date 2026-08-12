const benchmarks = [
    {
        strategy: "Redis + MySQL",
        avg: 30.89,
        p95: 46.34,
        p99: 50.10,
        rps: 924.39,
        width: 19,
        rank: "01",
    },
    {
        strategy: "Pessimistic",
        avg: 118.94,
        p95: 200.29,
        p99: 221.20,
        rps: 257.87,
        width: 73,
        rank: "02",
    },
    {
        strategy: "Atomic",
        avg: 123.38,
        p95: 230.27,
        p99: 269.33,
        rps: 247.34,
        width: 75,
        rank: "03",
    },
    {
        strategy: "Optimistic",
        avg: 163.45,
        p95: 588.31,
        p99: 659.14,
        rps: 191.93,
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
                        동시성 전략 성능 비교
                    </h2>

                    <p>
                        k6 · 200개 요청 · 동시 사용자 32명 · 정원 100명
                    </p>

                </div>

                <div className="benchmark-summary">

                    <span>
                        Fastest
                    </span>

                    <strong>
                        Redis + MySQL
                    </strong>

                    <em>
                        924.39 req/s
                    </em>

                </div>

            </div>

            <div className="benchmark-table-card">

                <div className="benchmark-table-head">

                    <span>
                        전략
                    </span>

                    <span>
                        평균응답
                    </span>

                    <span>
                        p95
                    </span>

                    <span>
                        p99
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
                        Atomic Update 실패 경로 개선
                    </h3>

                    <p>
                        MySQL REPEATABLE READ 환경에서
                        실패 원인을 확인하는 과정 중
                        이전 스냅샷을 조회해 HTTP 500이 발생하는 문제를
                        부하 테스트로 발견하고,
                        Locking Read를 적용해 개선했습니다.
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
                            BEFORE
                        </span>

                        <strong>
                            9
                        </strong>

                        <p>
                            unexpected
                            HTTP 500
                        </p>

                    </div>

                    <div className="incident-arrow">
                        →
                    </div>

                    <div className="incident-stat after">

                        <span>
                            AFTER
                        </span>

                        <strong>
                            0
                        </strong>

                        <p>
                            unexpected
                            failure
                        </p>

                    </div>

                </div>

            </div>

            <p className="benchmark-footnote">
                * 로컬 개발 환경에서 수행한
                HTTP 부하 테스트 결과이며,
                절대적인 서비스 성능 지표가 아닌
                전략 간 비교를 위한 측정값입니다.
            </p>

        </section>
    );
}

export default BenchmarkSection;