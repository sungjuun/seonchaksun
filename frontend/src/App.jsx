import {
    useCallback,
    useEffect,
    useState,
} from "react";

import "./App.css";

import {
    enterEvent,
    getEvent,
    getEventStatus,
} from "./api/eventApi";

import BenchmarkSection from "./components/BenchmarkSection";
import EntryPanel from "./components/EntryPanel";
import EventStatus from "./components/EventStatus";

function App() {

    const [eventId, setEventId] =
        useState("15");

    const [
        activeEventId,
        setActiveEventId,
    ] = useState(15);

    const [event, setEvent] =
        useState(null);

    const [status, setStatus] =
        useState(null);

    const [
        selectedStrategy,
        setSelectedStrategy,
    ] = useState("atomic");

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

                setEventLoading(true);
                setError("");

                try {

                    const data =
                        await getEvent(id);

                    setEvent(data);
                    setActiveEventId(id);

                } catch (error) {

                    setEvent(null);

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

            if (!event) {
                return;
            }

            loadStatus(
                activeEventId,
                selectedStrategy
            );

        },
        [
            event,
            activeEventId,
            selectedStrategy,
            loadStatus,
        ]
    );

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

            loadStatus(
                id,
                selectedStrategy
            );

            return;
        }

        setActiveEventId(id);
        setResult(null);
    }

    async function handleEntry({
                                   userId,
                                   strategy,
                               }) {

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

    function handleStrategyChange(
        strategy
    ) {

        setSelectedStrategy(
            strategy
        );

        setResult(null);
    }

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
                                Concurrency Lab
                            </span>

                        </div>

                    </div>

                    <div className="topbar-actions">

                        <div className="environment-badge">
                            <span className="environment-dot" />
                            LOCAL
                        </div>

                        <a
                            href="#benchmark"
                            className="topbar-link"
                        >
                            Benchmark
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
                            200명이 동시에 신청해도,
                            정확하게 100명만 받을 수 있을까?
                        </h1>

                        <p>
                            같은 선착순 신청에 여러 동시성 제어 전략을 적용하고,
                            정합성과 처리 성능이 어떻게 달라지는지 직접 비교했습니다.
                        </p>

                    </div>

                    <div className="hero-endpoint">

                        <div className="endpoint-header">

                            <span>
                                ENTRY API
                            </span>

                            <span className="endpoint-status">
                                Ready
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

                <section className="selector-card">

                    <div className="selector-copy">

                        <span className="section-kicker">
                            EVENT SELECTOR
                        </span>

                        <strong>
                            테스트 이벤트 선택
                        </strong>

                        <p>
                            Event ID를 입력해
                            실제 데이터를 불러옵니다.
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
                                Event ID
                            </span>

                            <input
                                type="number"
                                min="1"
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
                        이벤트 정보를 불러오고 있습니다.
                    </div>

                )}

                {error && (

                    <div className="state-card state-error">
                        {error}
                    </div>

                )}

                {event &&
                    !eventLoading && (

                        <section className="console-grid">

                            <EventStatus
                                event={event}
                                status={status}
                                strategy={
                                    selectedStrategy
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
                                onStrategyChange={
                                    handleStrategyChange
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
                        Backend Concurrency
                        Portfolio Project
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

export default App;