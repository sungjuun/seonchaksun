import {
    useState,
} from "react";

import StrategyCard from "./StrategyCard";

const strategies = [
    "atomic",
    "pessimistic",
    "optimistic",
    "redis",
];

function EntryPanel({
                        onSubmit,
                        loading,
                        result,
                        onStrategyChange,
                    }) {

    const [userId, setUserId] =
        useState("1001");

    const [strategy, setStrategy] =
        useState("atomic");

    function handleStrategyChange(
        nextStrategy
    ) {

        setStrategy(
            nextStrategy
        );

        onStrategyChange?.(
            nextStrategy
        );
    }

    function handleSubmit(event) {

        event.preventDefault();

        if (
            !userId ||
            Number(userId) <= 0
        ) {
            return;
        }

        onSubmit({
            userId,
            strategy,
        });
    }

    return (
        <article className="console-card entry-console-card">

            <div className="card-topline">

                <div>

                    <span className="section-kicker">
                        신청 테스트
                    </span>

                    <h2>
                        신청 전략 선택
                    </h2>

                </div>

                <span className="console-number">
                    01
                </span>

            </div>

            <p className="event-subtitle">
                동일한 이벤트에 서로 다른
                동시성 제어 전략을 적용합니다.
            </p>

            <div className="strategy-grid">

                {strategies.map(
                    (item) => (

                        <StrategyCard
                            key={item}
                            strategy={item}
                            selected={
                                strategy ===
                                item
                            }
                            onSelect={
                                handleStrategyChange
                            }
                        />

                    )
                )}

            </div>

            <form
                className="entry-form"
                onSubmit={
                    handleSubmit
                }
            >

                <label className="entry-input-label">

                    <span>
                        USER ID
                    </span>

                    <div className="entry-input-shell">

                        <span>
                            #
                        </span>

                        <input
                            type="number"
                            min="1"
                            value={userId}
                            onChange={
                                (event) =>
                                    setUserId(
                                        event
                                            .target
                                            .value
                                    )
                            }
                        />

                    </div>

                </label>

                <button
                    type="submit"
                    className="primary-button"
                    disabled={loading}
                >

                    <span>
                        {loading
                            ? "처리 중..."
                            : "선착순 신청하기"}
                    </span>

                    {!loading && (
                        <span className="button-arrow">
                            →
                        </span>
                    )}

                </button>

            </form>

            {result && (

                <div
                    className={
                        `result-box ${
                            result.success
                                ? "result-success"
                                : "result-failure"
                        }`
                    }
                >

                    <div className="result-symbol">

                        {result.success
                            ? "✓"
                            : "!"}

                    </div>

                    <div className="result-content">

                        <div className="result-title-row">

                            <strong>
                                {result.title}
                            </strong>

                            {result.elapsed != null && (

                                <span>
                                    {result.elapsed} ms
                                </span>

                            )}

                        </div>

                        <p>
                            {result.message}
                        </p>

                    </div>

                </div>

            )}

        </article>
    );
}

export default EntryPanel;