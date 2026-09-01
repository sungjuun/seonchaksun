const strategyMeta = {

    atomic: {
        title: "조건부 업데이트",
        short: "ATOMIC",
        description:
            "DB에서 정원 조건을 확인하면서 신청자 수를 한 번에 증가시킵니다.",
        tag: "MYSQL",
    },

    pessimistic: {
        title: "DB 잠금 방식",
        short: "PESSIMISTIC",
        description:
            "이벤트 데이터를 잠근 뒤 요청을 순서대로 안전하게 처리합니다.",
        tag: "LOCK",
    },

    optimistic: {
        title: "버전 충돌 재시도",
        short: "OPTIMISTIC",
        description:
            "동시 수정을 버전으로 감지하고 충돌한 요청을 다시 시도합니다.",
        tag: "VERSION",
    },

    redis: {
        title: "Redis 선점 방식",
        short: "REDIS",
        description:
            "Redis에서 먼저 자리를 확보한 뒤 신청 정보를 DB에 저장합니다.",
        tag: "REDIS",
    },
};

function StrategyCard({
                          strategy,
                          selected,
                          onSelect,
                          disabled = false,
                      }) {

    const meta =
        strategyMeta[strategy];

    if (!meta) {
        return null;
    }

    return (
        <button
            type="button"
            className={
                `strategy-card ${
                    selected
                        ? "strategy-card-selected"
                        : ""
                }`
            }
            disabled={disabled}
            onClick={
                () => {
                    if (!disabled) {
                        onSelect?.(
                            strategy
                        );
                    }
                }
            }
        >

            <div className="strategy-card-top">

                <span className="strategy-code">
                    {meta.short}
                </span>

                <span className="strategy-tag">
                    {meta.tag}
                </span>

            </div>

            <strong>
                {meta.title}
            </strong>

            <p>
                {meta.description}
            </p>

            <div className="strategy-card-footer">

                <span
                    className={
                        `strategy-radio ${
                            selected
                                ? "strategy-radio-selected"
                                : ""
                        }`
                    }
                >
                    {selected && (
                        <span />
                    )}
                </span>

                <span>
                    {disabled
                        ? "이 이벤트에 고정된 방식"
                        : selected
                            ? "현재 선택됨"
                            : "이 방식으로 테스트"}
                </span>

            </div>

        </button>
    );
}

export default StrategyCard;
