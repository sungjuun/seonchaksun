import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

/*
 * 신청 결과를 비즈니스 관점에서 구분한다.
 *
 * entry_success
 *   -> 실제 선착순 신청 성공
 *
 * entry_business_failure
 *   -> 정원 마감, 중복 신청 등 정상적인 비즈니스 실패
 *
 * entry_unexpected_failure
 *   -> 500 등 예상하지 못한 시스템 실패
 */
const successCount =
    new Counter('entry_success');

const businessFailureCount =
    new Counter('entry_business_failure');

const unexpectedFailureCount =
    new Counter('entry_unexpected_failure');


/*
 * 선착순 시스템에서는
 * 201 = 신청 성공
 * 400 = 정원 마감 등의 비즈니스 실패
 * 409 = 중복 신청
 *
 * 모두 우리가 예상한 응답으로 본다.
 *
 * 따라서 k6의 http_req_failed에
 * 정상적인 비즈니스 실패가 잡히지 않도록 설정한다.
 */
http.setResponseCallback(
    http.expectedStatuses(
        201,
        400,
        409
    )
);


export const options = {

    /*
     * 기본 출력보다 상세한 latency 지표를 확인하기 위해
     * p99까지 출력한다.
     */
    summaryTrendStats: [
        'avg',
        'med',
        'p(90)',
        'p(95)',
        'p(99)',
        'max',
    ],

    scenarios: {

        entryBurst: {

            /*
             * 전체 200개의 iteration을
             * 32개의 VU가 나눠서 실행한다.
             */
            executor: 'shared-iterations',

            /*
             * 최대 동시 VU 수.
             *
             * 기존 Java 동시성 테스트의
             * Thread Pool 32와 비슷한 조건으로 맞춘다.
             */
            vus: 32,

            /*
             * 총 HTTP 요청 수.
             */
            iterations: 200,

            /*
             * 비정상적으로 테스트가 오래 걸리는 상황을 막는다.
             */
            maxDuration: '30s',
        },
    },
};


/*
 * 환경 변수.
 *
 * 실행 예:
 *
 * k6 run `
 *   -e EVENT_ID=10 `
 *   -e STRATEGY=atomic `
 *   .\k6\entry-burst.js
 */

const BASE_URL =
    __ENV.BASE_URL
    || 'http://localhost:8080';


const EVENT_ID =
    __ENV.EVENT_ID
    || '1';


const STRATEGY =
    __ENV.STRATEGY
    || 'redis';


export default function () {

    /*
     * 각 요청마다 중복되지 않는 userId를 만든다.
     *
     * __ITER를 사용하면 각 VU마다
     * 0부터 다시 시작하기 때문에
     * userId가 중복될 수 있다.
     *
     * exec.scenario.iterationInTest는
     * Scenario 전체에서 유일한 iteration 번호다.
     *
     * 0 ~ 199
     *
     * 따라서 userId:
     *
     * 1 ~ 200
     */
    const userId =
        exec.scenario.iterationInTest + 1;


    /*
     * 전략 선택 API.
     *
     * atomic
     * pessimistic
     * optimistic
     * redis
     */
    const url =
        `${BASE_URL}`
        + `/api/events/${EVENT_ID}`
        + `/entries/strategies/${STRATEGY}`;


    const payload =
        JSON.stringify({
            userId: userId,
        });


    const params = {

        headers: {
            'Content-Type': 'application/json',
        },

        /*
         * 전략별 결과를 구분하기 위한 tag.
         */
        tags: {
            strategy: STRATEGY,
        },
    };


    /*
     * 실제 HTTP 요청
     */
    const response =
        http.post(
            url,
            payload,
            params
        );


    /*
     * 201 Created
     *
     * 실제 신청 성공
     */
    if (
        response.status === 201
    ) {

        successCount.add(1);

        /*
         * 400 / 409
         *
         * 정원 마감 또는 중복 신청 등
         * 정상적인 비즈니스 실패
         */
    } else if (
        response.status === 400
        || response.status === 409
    ) {

        businessFailureCount.add(1);

        /*
         * 위에서 예상하지 않은 상태코드.
         *
         * 예:
         *
         * 404
         * 500
         * 503
         *
         * Atomic 전략의 unexpected failure 원인을
         * 확인하기 위해 status와 body를 출력한다.
         */
    } else {

        unexpectedFailureCount.add(1);

        console.error(
            `[UNEXPECTED]`
            + ` strategy=${STRATEGY}`
            + ` eventId=${EVENT_ID}`
            + ` userId=${userId}`
            + ` status=${response.status}`
            + ` body=${response.body}`
        );
    }


    /*
     * 응답 상태가 우리가 예상한 범위인지 검증한다.
     *
     * 정상적인 테스트라면:
     *
     * 201
     * 400
     * 409
     *
     * 중 하나여야 한다.
     */
    check(
        response,
        {
            'status is expected':
                (r) =>
                    r.status === 201
                    || r.status === 400
                    || r.status === 409,
        }
    );
}