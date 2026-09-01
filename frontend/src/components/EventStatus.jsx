function EventStatus({
                         event,
                         status,
                         strategy,
                     }) {

    const currentCount =
        status?.currentCount ?? 0;

    const capacity =
        status?.capacity ??
        event.capacity;

    const remaining =
        status?.remainingCount ??
        Math.max(
            capacity - currentCount,
            0
        );

    const percentage =
        capacity === 0
            ? 0
            : Math.min(
                100,
                (
                    currentCount /
                    capacity
                ) * 100
            );

    const countSource =
        status?.countSource ??
        (strategy === "redis"
            ? "REDIS"
            : "MYSQL");

    return (
        <article className="console-card event-status-card">

            <div className="card-topline">

                <div>

                    <span className="section-kicker">
                        현재 이벤트
                    </span>

                    <h2>
                        {event.name}
                    </h2>

                </div>

                <div className="live-badge">
                    <span />
                    진행 중
                </div>

            </div>

            <p className="event-subtitle">
                이 이벤트에 고정된 처리 방식 기준으로
                현재 신청 인원과 남은 자리를 확인합니다.
            </p>

            <div className="event-count-area">

                <div className="event-count">

                    <span>
                        현재 신청 인원
                    </span>

                    <div>

                        <strong>
                            {currentCount}
                        </strong>

                        <em>
                            / {capacity}
                        </em>

                    </div>

                </div>

                <div className="remaining-count">

                    <span>
                        남은 자리
                    </span>

                    <strong>
                        {remaining}
                    </strong>

                    <small>
                        자리
                    </small>

                </div>

            </div>

            <div className="progress-wrapper">

                <div className="progress-meta">

                    <span>
                        정원 사용률
                    </span>

                    <strong>
                        {Math.round(
                            percentage
                        )}
                        %
                    </strong>

                </div>

                <div className="progress-track">

                    <div
                        className="progress-value"
                        style={{
                            width:
                                `${percentage}%`,
                        }}
                    />

                </div>

            </div>

            <div className="status-metadata-grid">

                <div className="metadata-item">

                    <span>
                        인원 관리 기준
                    </span>

                    <strong
                        className={
                            countSource ===
                            "REDIS"
                                ? "source-badge redis-source"
                                : "source-badge mysql-source"
                        }
                    >
                        {countSource === "REDIS"
                            ? "Redis"
                            : "MySQL"}
                    </strong>

                </div>

                <div className="metadata-item">

                    <span>
                        현재 처리 방식
                    </span>

                    <strong>
                        {formatStrategy(
                            strategy
                        )}
                    </strong>

                </div>

                <div className="metadata-item">

                    <span>
                        신청 시작
                    </span>

                    <strong>
                        {formatDate(
                            event.openAt
                        )}
                    </strong>

                </div>

                <div className="metadata-item">

                    <span>
                        신청 마감
                    </span>

                    <strong>
                        {formatDate(
                            event.closeAt
                        )}
                    </strong>

                </div>

            </div>

            <div className="status-note">

                <span className="status-note-icon">
                    i
                </span>

                <p>
                    {countSource === "REDIS"
                        ? "Redis 선점 방식은 Redis에서 현재 신청 인원을 관리합니다."
                        : "현재 방식은 MySQL에서 현재 신청 인원을 관리합니다."}
                </p>

            </div>

        </article>
    );
}

function formatStrategy(strategy) {

    switch (strategy) {

        case "atomic":
            return "조건부 업데이트";

        case "pessimistic":
            return "DB 잠금 방식";

        case "optimistic":
            return "버전 충돌 재시도";

        case "redis":
            return "Redis 선점 방식";

        default:
            return strategy;
    }
}

function formatDate(value) {

    if (!value) {
        return "-";
    }

    const date =
        new Date(value);

    return new Intl.DateTimeFormat(
        "ko-KR",
        {
            month: "2-digit",
            day: "2-digit",
            hour: "2-digit",
            minute: "2-digit",
        }
    ).format(date);
}

export default EventStatus;