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
        "MYSQL";

    return (
        <article className="console-card event-status-card">

            <div className="card-topline">

                <div>

                    <span className="section-kicker">
                        LIVE EVENT
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
                선택한 동시성 전략 기준으로
                현재 신청 상태를 조회합니다.
            </p>

            <div className="event-count-area">

                <div className="event-count">

                    <span>
                        신청 인원
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
                        seats
                    </small>

                </div>

            </div>

            <div className="progress-wrapper">

                <div className="progress-meta">

                    <span>
                        Capacity usage
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
                        Count Source
                    </span>

                    <strong
                        className={
                            countSource ===
                            "REDIS"
                                ? "source-badge redis-source"
                                : "source-badge mysql-source"
                        }
                    >
                        {countSource}
                    </strong>

                </div>

                <div className="metadata-item">

                    <span>
                        Strategy
                    </span>

                    <strong>
                        {formatStrategy(
                            strategy
                        )}
                    </strong>

                </div>

                <div className="metadata-item">

                    <span>
                        Open
                    </span>

                    <strong>
                        {formatDate(
                            event.openAt
                        )}
                    </strong>

                </div>

                <div className="metadata-item">

                    <span>
                        Close
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
                        ? "Redis 전략은 Redis Counter를 정원 기준으로 사용합니다."
                        : "현재 전략은 MySQL events.current_count를 정원 기준으로 사용합니다."}
                </p>

            </div>

        </article>
    );
}

function formatStrategy(strategy) {

    switch (strategy) {

        case "atomic":
            return "Atomic Update";

        case "pessimistic":
            return "Pessimistic Lock";

        case "optimistic":
            return "Optimistic Lock";

        case "redis":
            return "Redis + MySQL";

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