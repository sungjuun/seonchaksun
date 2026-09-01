const BASE_URL = "http://localhost:8080";

export async function createEvent(data) {
    const response = await fetch(
        `${BASE_URL}/api/events`,
        {
            method: "POST",
            headers: {
                "Content-Type":
                    "application/json",
            },
            body: JSON.stringify(data),
        }
    );

    return parseResponse(
        response,
        "이벤트를 생성하지 못했습니다."
    );
}

export async function getEvent(eventId) {
    const response = await fetch(
        `${BASE_URL}/api/events/${eventId}`
    );

    return parseResponse(
        response,
        "이벤트 정보를 불러오지 못했습니다."
    );
}

export async function getEventStatus(
    eventId,
    strategy
) {
    const response = await fetch(
        `${BASE_URL}/api/events/${eventId}/status?strategy=${strategy}`
    );

    return parseResponse(
        response,
        "이벤트 신청 현황을 불러오지 못했습니다."
    );
}

export async function enterEvent(
    eventId,
    userId,
    strategy
) {
    const response = await fetch(
        `${BASE_URL}/api/events/${eventId}/entries/strategies/${strategy}`,
        {
            method: "POST",
            headers: {
                "Content-Type":
                    "application/json",
            },
            body: JSON.stringify({
                userId:
                    Number(userId),
            }),
        }
    );

    return parseResponse(
        response,
        getDefaultErrorMessage(
            response.status
        )
    );
}

async function parseResponse(
    response,
    fallbackMessage
) {
    let body = null;

    try {
        body =
            await response.json();
    } catch {
        // body가 없는 응답 허용
    }

    if (!response.ok) {
        const error =
            new Error(
                body?.message
                || body?.error
                || fallbackMessage
            );

        error.status =
            response.status;

        error.body =
            body;

        throw error;
    }

    return body;
}

function getDefaultErrorMessage(
    status
) {

    switch (status) {

        case 400:
            return "현재 이벤트에 신청할 수 없습니다.";

        case 409:
            return "이미 신청한 사용자입니다.";

        case 404:
            return "이벤트를 찾을 수 없습니다.";

        default:
            return "요청 처리 중 오류가 발생했습니다.";
    }
}
