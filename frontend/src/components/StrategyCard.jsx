const strategyMeta = {

    atomic: {
        title: "Atomic Update",
        short: "ATOMIC",
        description:
            "조건부 UPDATE 한 번으로 정원을 확보합니다.",
        tag: "DB",
    },

    pessimistic: {
        title: "Pessimistic Lock",
        short: "PESSIMISTIC",
        description:
            "행 잠금을 획득한 뒤 순차적으로 처리합니다.",
        tag: "LOCK",
    },

    optimistic: {
        title: "Optimistic Lock",
        short: "OPTIMISTIC",
        description:
            "Version 충돌을 감지하고 재시도합니다.",
        tag: "VERSION",
    },

    redis: {
        title: "Redis + MySQL",
        short: "REDIS",
        description:
            "Lua Script로 Redis Counter를 원자적으로 증가시킵니다.",
        tag: "LUA",
    },
};

function StrategyCard({
                          strategy,
                          selected,
                          onSelect,
                      }) {

    const meta =
        strategyMeta[strategy];

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
            onClick={
                () =>
                    onSelect(
                        strategy
                    )
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
                    {selected
                        ? "현재 선택됨"
                        : "이 전략으로 테스트"}
                </span>

            </div>

        </button>
    );
}

export default StrategyCard;