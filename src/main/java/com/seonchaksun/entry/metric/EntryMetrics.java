package com.seonchaksun.entry.metric;

import com.seonchaksun.entry.service.EntryStrategy;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class EntryMetrics {

    private static final String RESULT_SUCCESS =
            "success";

    private static final String RESULT_BUSINESS_FAILURE =
            "business_failure";

    private static final String RESULT_UNEXPECTED_FAILURE =
            "unexpected_failure";

    private final MeterRegistry meterRegistry;

    public EntryMetrics(
            MeterRegistry meterRegistry
    ) {
        this.meterRegistry = meterRegistry;
    }

    public void recordSuccess(
            EntryStrategy strategy,
            long durationNanos
    ) {

        recordRequest(
                strategy,
                RESULT_SUCCESS
        );

        recordDuration(
                strategy,
                RESULT_SUCCESS,
                durationNanos
        );
    }

    public void recordBusinessFailure(
            EntryStrategy strategy,
            long durationNanos
    ) {

        recordRequest(
                strategy,
                RESULT_BUSINESS_FAILURE
        );

        recordDuration(
                strategy,
                RESULT_BUSINESS_FAILURE,
                durationNanos
        );
    }

    public void recordUnexpectedFailure(
            EntryStrategy strategy,
            long durationNanos
    ) {

        recordRequest(
                strategy,
                RESULT_UNEXPECTED_FAILURE
        );

        recordDuration(
                strategy,
                RESULT_UNEXPECTED_FAILURE,
                durationNanos
        );
    }

    private void recordRequest(
            EntryStrategy strategy,
            String result
    ) {

        Counter.builder(
                        "seonchaksun.entry.requests"
                )
                .description(
                        "선착순 전략별 신청 요청 수"
                )
                .tag(
                        "strategy",
                        strategy.name().toLowerCase()
                )
                .tag(
                        "result",
                        result
                )
                .register(meterRegistry)
                .increment();
    }

    private void recordDuration(
            EntryStrategy strategy,
            String result,
            long durationNanos
    ) {

        Timer.builder(
                        "seonchaksun.entry.duration"
                )
                .description(
                        "선착순 전략별 신청 처리 시간"
                )
                .tag(
                        "strategy",
                        strategy.name().toLowerCase()
                )
                .tag(
                        "result",
                        result
                )
                .register(meterRegistry)
                .record(
                        durationNanos,
                        TimeUnit.NANOSECONDS
                );
    }
}