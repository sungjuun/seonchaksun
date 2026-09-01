import {
    useCallback,
    useEffect,
    useState,
} from "react";

import "./App.css";

import {
    createEvent,
    enterEvent,
    getEvent,
    getEventStatus,
} from "./api/eventApi";

import BenchmarkSection from "./components/BenchmarkSection";
import EntryPanel from "./components/EntryPanel";
import EventCreatePanel from "./components/EventCreatePanel";
import EventStatus from "./components/EventStatus";

function App() {

    const [eventId, setEventId] =
        useState("");

    const [
        activeEventId,
        setActiveEventId,
    ] = useState(null);

    const [event, setEvent] =
        useState(null);

    const [status, setStatus] =
        useState(null);

    const [loading, setLoading] =
        useState(false);

    const [
        eventLoading,
        setEventLoading,
    ] = useState(false);

    const [error, setError] =
        useState("");

    const [result, setResult] =
        useState(null);

    const loadEvent =
        useCallback(
            async (id) => {

                if (!id) {
                    return;
                }

                setEventLoading(true);
                setError("");

                try {

                    const data =
                        await getEvent(id);

                    setEvent(data);
                    setActiveEventId(id);
                    setEventId(
                        String(id)
                    );

                } catch (error) {

                    setEvent(null);
                    setStatus(null);

                    setError(
                        error.message
                    );

                } finally {

                    setEventLoading(false);
                }
            },
            []
        );

    const loadStatus =
        useCallback(
            async (
                id,
                strategy
            ) => {

                if (!id || !strategy) {
                    return;
                }

                try {

                    const data =
                        await getEventStatus(
                            id,
                            strategy
                        );

                    setStatus(data);

                } catch (error) {

                    setStatus(null);

                    console.error(
                        error
                    );
                }
            },
            []
        );

    useEffect(
        () => {

            if (!activeEventId) {
                return;
            }

            loadEvent(
                activeEventId
            );

        },
        [
            activeEventId,
            loadEvent,
        ]
    );

    useEffect(
        () => {

            if (!event || !activeEventId) {
                return;
            }

            loadStatus(
                activeEventId,
                toStrategyPath(
                    event.strategy
                )
            );

        },
        [
            event,
            activeEventId,
            loadStatus,
        ]
    );

    async function handleEventCreate(
        request
    ) {
        setEventLoading(true);
        setError("");
        setResult(null);

        try {
            const created =
                await createEvent(
                    request
                );

            setEvent(created);
            setStatus(null);
            setEventId(
                String(created.id)
            );
            setActiveEventId(
                created.id
            );

        } catch (error) {
            setError(
                error.message
            );
        } finally {
            setEventLoading(false);
        }
    }

    function handleEventSearch(e) {

        e.preventDefault();

        const id =
            Number(eventId);

        if (!id || id <= 0) {
            return;
        }

        if (
            id ===
            activeEventId
        ) {

            loadEvent(id);

            if (event) {
                loadStatus(
                    id,
                    toStrategyPath(
                        event.strategy
                    )
                );
            }

            return;
        }

        setActiveEventId(id);
        setResult(null);
    }

    async function handleEntry({
                                   userId,
                               }) {

        if (!event || !activeEventId) {
            return;
        }

        const strategy =
            toStrategyPath(
                event.strategy
            );

        setLoading(true);
        setResult(null);

        const startedAt =
            performance.now();

        try {

            const response =
                await enterEvent(
                    activeEventId,
                    userId,
                    strategy
                );

            const elapsed =
                Math.round(
                    (
                        performance.now()
                        - startedAt
                    ) * 100
                ) / 100;

            setResult({
                success: true,
                title: "신청 성공",
                message:
                    `사용자 ${response.userId}의 신청이 완료되었습니다.`,
                elapsed,
            });

        } catch (error) {

            const elapsed =
                Math.round(
                    (
                        performance.now()
                        - startedAt
                    ) * 100
                ) / 100;

            setResult({
                success: false,

                title:
                    error.status === 409
                        ? "중복 신청"
                        : error.status === 400
                            ? "신청 불가"
                            : "신청 실패",

                message:
                    error.message,

                elapsed,
            });

        } finally {

            await loadStatus(
                activeEventId,
                strategy
            );

            await loadEvent(
                activeEventId
            );

            setLoading(false);
        }
    }

    const activeStrategy =
        event
            ? toStrategyPath(
                event.strategy
            )
            : null;

    return (
        <div className="app-shell">

            <header className="topbar">

                <div className="topbar-inner">

                    <div className="brand">

                        <div className="brand-mark">
                            S
                        </div>

                        <div className="brand-copy">

                            <strong>
                                선착순
                            </strong>

                            <span>
                                동시성 처리 테스트
                            </span>

                        </div>

                    </div>

                    <div className="topbar-actions">

                        <div className="environment-badge">
                            <span className="environment-dot" />
                            로컬 환경
                        </div>

                        <a
                            href="#benchmark"
                            className="topbar-link"
                        >
                            성능 비교
                        </a>

                    </div>

                </div>

            </header>

            <main className="main-content">

                <section className="hero">

                    <div className="hero-copy">

                        <span className="hero-eyebrow">
                            동시 요청 처리 비교
                        </span>

                        <h1>
                            200개의 신청 요청이 몰려도,
                            정확하게 100명만 받을 수 있을까?
                        </h1>

                        <p>
                            같은 선착순 문제를 여러 동시성 제어 전략으로 각각 구현하고,
                            정합성과 처리 성능이 어떻게 달라지는지 비교했습니다.
                        </p>

                    </div>

                    <div className="hero-endpoint">

                        <div className="endpoint-header">

                            <span>
                                신청 API
                            </span>

                            <span className="endpoint-status">
                                사용 가능
                            </span>

                        </div>

                        <div className="endpoint-code">

                            <span className="method-badge">
                                POST
                            </span>

                            <code>
                                /api/events/
                                {"{id}"}
                                /entries/strategies/
                                {"{strategy}"}
                            </code>

                        </div>

                        <div className="endpoint-stack">

                            <span>
                                Spring Boot
                            </span>

                            <span>
                                MySQL
                            </span>

                            <span>
                                Redis
                            </span>

                            <span>
                                k6
                            </span>

                        </div>

                    </div>

                </section>

                <EventCreatePanel
                    onCreate={
                        handleEventCreate
                    }
                    loading={
                        eventLoading
                    }
                />

                <section className="selector-card">

                    <div className="selector-copy">

                        <span className="section-kicker">
                            이벤트 선택
                        </span>

                        <strong>
                            기존 테스트 이벤트 불러오기
                        </strong>

                        <p>
                            이미 생성한 이벤트 번호가 있다면
                            입력해서 다시 불러올 수 있습니다.
                        </p>

                    </div>

                    <form
                        className="selector-form"
                        onSubmit={
                            handleEventSearch
                        }
                    >

                        <div className="input-shell">

                            <span>
                                이벤트 번호
                            </span>

                            <input
                                type="number"
                                min="1"
                                placeholder="예: 1"
                                value={
                                    eventId
                                }
                                onChange={
                                    (e) =>
                                        setEventId(
                                            e.target.value
                                        )
                                }
                            />

                        </div>

                        <button
                            type="submit"
                            className="secondary-button"
                        >
                            불러오기
                        </button>

                    </form>

                </section>

                {eventLoading && (

                    <div className="state-card">
                        이벤트 정보를 처리하고 있습니다.
                    </div>

                )}

                {error && (

                    <div className="state-card state-error">
                        {error}
                    </div>

                )}

                {event
                    && !eventLoading && (

                        <section className="console-grid">

                            <EventStatus
                                event={event}
                                status={status}
                                strategy={
                                    activeStrategy
                                }
                            />

                            <EntryPanel
                                onSubmit={
                                    handleEntry
                                }
                                loading={
                                    loading
                                }
                                result={
                                    result
                                }
                                strategy={
                                    activeStrategy
                                }
                            />

                        </section>

                    )}

                <div
                    id="benchmark"
                    className="benchmark-wrapper"
                >
                    <BenchmarkSection />
                </div>

            </main>

            <footer className="footer">

                <div>

                    <strong>
                        SEONCHAKSUN
                    </strong>

                    <span>
                        백엔드 동시성
                        포트폴리오 프로젝트
                    </span>

                </div>

                <span>
                    Spring Boot · MySQL ·
                    Redis · k6
                </span>

            </footer>

        </div>
    );
}

function toStrategyPath(
    strategy
) {
    return strategy
        ?.toLowerCase();
}

export default App;
