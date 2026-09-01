import {
    useState,
} from "react";

const STRATEGIES = [
    {
        value: "ATOMIC",
        label: "Atomic - 조건부 업데이트",
    },
    {
        value: "PESSIMISTIC",
        label: "Pessimistic - DB 잠금",
    },
    {
        value: "OPTIMISTIC",
        label: "Optimistic - 버전 충돌 재시도",
    },
    {
        value: "REDIS",
        label: "Redis - Redis 선점",
    },
];

function EventCreatePanel({
                              onCreate,
                              loading,
                          }) {

    const [name, setName] =
        useState("동시성 테스트 이벤트");

    const [capacity, setCapacity] =
        useState("100");

    const [strategy, setStrategy] =
        useState("ATOMIC");

    function handleSubmit(event) {
        event.preventDefault();

        const numericCapacity =
            Number(capacity);

        if (
            !name.trim()
            || !numericCapacity
            || numericCapacity < 1
        ) {
            return;
        }

        const now = new Date();

        const openAt = new Date(
            now.getTime() - 60_000
        );

        const closeAt = new Date(
            now.getTime()
            + 2 * 60 * 60 * 1000
        );

        onCreate({
            name: name.trim(),
            capacity: numericCapacity,
            strategy,
            openAt:
                toLocalDateTime(openAt),
            closeAt:
                toLocalDateTime(closeAt),
        });
    }

    return (
        <section className="create-card">

            <div className="selector-copy">

                <span className="section-kicker">
                    새 테스트
                </span>

                <strong>
                    테스트 이벤트 생성
                </strong>

                <p>
                    이벤트를 만들 때 처리 전략을 하나 고정합니다.
                    생성 후에는 다른 전략으로 바꿀 수 없습니다.
                </p>

            </div>

            <form
                className="create-form"
                onSubmit={handleSubmit}
            >

                <label className="create-field create-field-name">
                    <span>
                        이벤트명
                    </span>

                    <input
                        type="text"
                        maxLength="100"
                        value={name}
                        onChange={
                            (event) =>
                                setName(
                                    event.target.value
                                )
                        }
                    />
                </label>

                <label className="create-field create-field-capacity">
                    <span>
                        정원
                    </span>

                    <input
                        type="number"
                        min="1"
                        value={capacity}
                        onChange={
                            (event) =>
                                setCapacity(
                                    event.target.value
                                )
                        }
                    />
                </label>

                <label className="create-field create-field-strategy">
                    <span>
                        처리 전략
                    </span>

                    <select
                        value={strategy}
                        onChange={
                            (event) =>
                                setStrategy(
                                    event.target.value
                                )
                        }
                    >
                        {STRATEGIES.map(
                            (item) => (
                                <option
                                    key={item.value}
                                    value={item.value}
                                >
                                    {item.label}
                                </option>
                            )
                        )}
                    </select>
                </label>

                <button
                    type="submit"
                    className="secondary-button create-button"
                    disabled={loading}
                >
                    {loading
                        ? "생성 중..."
                        : "이벤트 생성"}
                </button>

            </form>

        </section>
    );
}

function toLocalDateTime(date) {
    const pad = (value) =>
        String(value).padStart(2, "0");

    return (
        `${date.getFullYear()}-`
        + `${pad(date.getMonth() + 1)}-`
        + `${pad(date.getDate())}T`
        + `${pad(date.getHours())}:`
        + `${pad(date.getMinutes())}:`
        + `${pad(date.getSeconds())}`
    );
}

export default EventCreatePanel;
