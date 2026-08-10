package com.seonchaksun.event.controller;

import com.seonchaksun.event.service.EventStatusService;
import com.seonchaksun.event.domain.EventException;
import com.seonchaksun.event.domain.EventNotFoundException;
import com.seonchaksun.event.dto.EventCreateRequest;
import com.seonchaksun.event.dto.EventResponse;
import com.seonchaksun.event.service.EventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventController.class)
class EventControllerTest {

    private static final LocalDateTime OPEN_AT =
            LocalDateTime.of(2026, 8, 10, 10, 0);

    private static final LocalDateTime CLOSE_AT =
            LocalDateTime.of(2026, 8, 10, 18, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private EventService eventService;

    @MockitoBean
    private EventStatusService eventStatusService;

    @Test
    @DisplayName("이벤트 생성 요청이 성공하면 201 응답을 반환한다")
    void createEvent() throws Exception {
        // given
        EventCreateRequest request = new EventCreateRequest(
                "한정판 키보드 사전예약",
                100,
                OPEN_AT,
                CLOSE_AT
        );

        EventResponse response = new EventResponse(
                1L,
                "한정판 키보드 사전예약",
                100,
                0,
                OPEN_AT,
                CLOSE_AT
        );

        when(eventService.createEvent(
                any(EventCreateRequest.class)
        )).thenReturn(response);

        // when & then
        mockMvc.perform(
                        post("/api/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        jsonMapper.writeValueAsString(request)
                                )
                )
                .andExpect(status().isCreated())
                .andExpect(
                        header().string(
                                "Location",
                                "/api/events/1"
                        )
                )
                .andExpect(
                        jsonPath("$.id")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.name")
                                .value("한정판 키보드 사전예약")
                )
                .andExpect(
                        jsonPath("$.capacity")
                                .value(100)
                )
                .andExpect(
                        jsonPath("$.currentCount")
                                .value(0)
                )
                .andExpect(
                        jsonPath("$.openAt")
                                .value("2026-08-10T10:00:00")
                )
                .andExpect(
                        jsonPath("$.closeAt")
                                .value("2026-08-10T18:00:00")
                );

        verify(eventService)
                .createEvent(any(EventCreateRequest.class));
    }

    @Test
    @DisplayName("이벤트 ID로 이벤트를 조회하면 200 응답을 반환한다")
    void getEvent() throws Exception {
        // given
        EventResponse response = new EventResponse(
                1L,
                "한정판 키보드 사전예약",
                100,
                0,
                OPEN_AT,
                CLOSE_AT
        );

        when(eventService.getEvent(1L))
                .thenReturn(response);

        // when & then
        mockMvc.perform(
                        get("/api/events/{eventId}", 1L)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.name")
                                .value("한정판 키보드 사전예약")
                )
                .andExpect(
                        jsonPath("$.capacity")
                                .value(100)
                )
                .andExpect(
                        jsonPath("$.currentCount")
                                .value(0)
                )
                .andExpect(
                        jsonPath("$.openAt")
                                .value("2026-08-10T10:00:00")
                )
                .andExpect(
                        jsonPath("$.closeAt")
                                .value("2026-08-10T18:00:00")
                );

        verify(eventService)
                .getEvent(1L);
    }

    @Test
    @DisplayName("존재하지 않는 이벤트를 조회하면 404 응답을 반환한다")
    void getEventNotFound() throws Exception {
        // given
        when(eventService.getEvent(999L))
                .thenThrow(
                        new EventNotFoundException(999L)
                );

        // when & then
        mockMvc.perform(
                        get("/api/events/{eventId}", 999L)
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.code")
                                .value("EVENT_NOT_FOUND")
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "이벤트를 찾을 수 없습니다. eventId=999"
                                )
                );
    }

    @Test
    @DisplayName("이벤트명이 비어 있으면 400 오류 응답을 반환한다")
    void rejectBlankEventName() throws Exception {
        // given
        String request = """
                {
                  "name": " ",
                  "capacity": 100,
                  "openAt": "2026-08-10T10:00:00",
                  "closeAt": "2026-08-10T18:00:00"
                }
                """;

        // when & then
        mockMvc.perform(
                        post("/api/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("INVALID_REQUEST")
                )
                .andExpect(
                        jsonPath("$.message")
                                .value("이벤트명은 필수입니다.")
                );
    }

    @Test
    @DisplayName("이벤트 정원이 0이면 400 오류 응답을 반환한다")
    void rejectZeroCapacity() throws Exception {
        // given
        String request = """
                {
                  "name": "한정판 키보드 사전예약",
                  "capacity": 0,
                  "openAt": "2026-08-10T10:00:00",
                  "closeAt": "2026-08-10T18:00:00"
                }
                """;

        // when & then
        mockMvc.perform(
                        post("/api/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("INVALID_REQUEST")
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "이벤트 정원은 1명 이상이어야 합니다."
                                )
                );
    }

    @Test
    @DisplayName("이벤트 시작 시간이 없으면 400 오류 응답을 반환한다")
    void rejectMissingOpenAt() throws Exception {
        // given
        String request = """
                {
                  "name": "한정판 키보드 사전예약",
                  "capacity": 100,
                  "closeAt": "2026-08-10T18:00:00"
                }
                """;

        // when & then
        mockMvc.perform(
                        post("/api/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("INVALID_REQUEST")
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "이벤트 시작 시간은 필수입니다."
                                )
                );
    }

    @Test
    @DisplayName("이벤트 기간이 잘못되면 400 오류 응답을 반환한다")
    void rejectInvalidEventPeriod() throws Exception {
        // given
        when(eventService.createEvent(
                any(EventCreateRequest.class)
        )).thenThrow(
                new EventException(
                        "이벤트 시작 시간은 종료 시간보다 빨라야 합니다."
                )
        );

        String request = """
                {
                  "name": "잘못된 이벤트",
                  "capacity": 100,
                  "openAt": "2026-08-10T18:00:00",
                  "closeAt": "2026-08-10T10:00:00"
                }
                """;

        // when & then
        mockMvc.perform(
                        post("/api/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("INVALID_EVENT")
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "이벤트 시작 시간은 종료 시간보다 빨라야 합니다."
                                )
                );
    }
}