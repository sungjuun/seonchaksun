package com.seonchaksun.event.controller;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventController.class)
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private EventService eventService;

    @Test
    @DisplayName("이벤트 생성 요청이 성공하면 201 응답을 반환한다")
    void createEvent() throws Exception {
        LocalDateTime openAt =
                LocalDateTime.of(2026, 8, 10, 10, 0);

        LocalDateTime closeAt =
                LocalDateTime.of(2026, 8, 10, 18, 0);

        EventCreateRequest request = new EventCreateRequest(
                "한정판 키보드 사전예약",
                100,
                openAt,
                closeAt
        );

        EventResponse response = new EventResponse(
                1L,
                "한정판 키보드 사전예약",
                100,
                0,
                openAt,
                closeAt
        );

        when(eventService.createEvent(any(EventCreateRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        "/api/events/1"
                ))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name")
                        .value("한정판 키보드 사전예약"))
                .andExpect(jsonPath("$.capacity").value(100))
                .andExpect(jsonPath("$.currentCount").value(0))
                .andExpect(jsonPath("$.openAt")
                        .value("2026-08-10T10:00:00"))
                .andExpect(jsonPath("$.closeAt")
                        .value("2026-08-10T18:00:00"));

        verify(eventService)
                .createEvent(any(EventCreateRequest.class));
    }

    @Test
    @DisplayName("이벤트명이 비어 있으면 400 응답을 반환한다")
    void rejectBlankEventName() throws Exception {
        String request = """
                {
                  "name": " ",
                  "capacity": 100,
                  "openAt": "2026-08-10T10:00:00",
                  "closeAt": "2026-08-10T18:00:00"
                }
                """;

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("이벤트 정원이 0이면 400 응답을 반환한다")
    void rejectZeroCapacity() throws Exception {
        String request = """
                {
                  "name": "한정판 키보드 사전예약",
                  "capacity": 0,
                  "openAt": "2026-08-10T10:00:00",
                  "closeAt": "2026-08-10T18:00:00"
                }
                """;

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("이벤트 시작 시간이 없으면 400 응답을 반환한다")
    void rejectMissingOpenAt() throws Exception {
        String request = """
                {
                  "name": "한정판 키보드 사전예약",
                  "capacity": 100,
                  "closeAt": "2026-08-10T18:00:00"
                }
                """;

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest());
    }
}