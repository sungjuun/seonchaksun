import {
    useState,
} from "react";

import StrategyCard from "./StrategyCard";

function EntryPanel({
                        onSubmit,
                        loading,
                        result,
                        strategy,
                    }) {

    const [userId, setUserId] =
        useState("1001");

    function handleSubmit(event) {

        event.preventDefault();

        if (
            !userId
            || Number(userId) <= 0
        ) {
            return;
        }

        onSubmit({
            userId,
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
                        이벤트 처리 방식
                    </h2>

                </div>

                <span className="console-number">
                    01
                </span>

            </div>

            <p className="event-subtitle">
                이 이벤트는 생성할 때 선택한
                동시성 처리 방식 하나만 사용합니다.
            </p>

            <div className="strategy-grid strategy-grid-single">

                <StrategyCard
                    strategy={strategy}
                    selected
                    disabled
                />

            </div>

            <form
                className="entry-form"
                onSubmit={
                    handleSubmit
                }
            >

                <label className="entry-input-label">

                    <span>
                        사용자 번호
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
                            ? "신청 처리 중..."
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
