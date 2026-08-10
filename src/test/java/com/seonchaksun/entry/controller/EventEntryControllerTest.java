package com.seonchaksun.entry.controller;

import com.seonchaksun.entry.domain.DuplicateEntryException;
import com.seonchaksun.entry.dto.EventEntryRequest;
import com.seonchaksun.entry.dto.EventEntryResponse;
import com.seonchaksun.entry.service.EntryStrategy;
import com.seonchaksun.entry.service.EventEntryService;
import com.seonchaksun.entry.service.EventEntryStrategyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventEntryController.class)
class EventEntryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private EventEntryService eventEntryService;

    /*
     * EventEntryController에 새로 추가된 의존성.
     *
     * @WebMvcTest에서는 Service Bean을 자동으로
     * 생성하지 않으므로 Mock Bean으로 등록한다.
     */
    @MockitoBean
    private EventEntryStrategyService
            eventEntryStrategyService;

    @Test
    @DisplayName("이벤트 신청에 성공하면 201 응답을 반환한다")
    void enter() throws Exception {

        // given
        EventEntryRequest request =
                new EventEntryRequest(1001L);

        EventEntryResponse response =
                new EventEntryResponse(
                        10L,
                        1L,
                        1001L,
                        LocalDateTime.of(
                                2026,
                                8,
                                10,
                                12,
                                0
                        )
                );

        when(
                eventEntryService.enter(
                        1L,
                        1001L
                )
        ).thenReturn(response);

        // when & then
        mockMvc.perform(
                        post(
                                "/api/events/{eventId}/entries",
                                1L
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        jsonMapper
                                                .writeValueAsString(
                                                        request
                                                )
                                )
                )
                .andExpect(
                        status().isCreated()
                )
                .andExpect(
                        header().string(
                                "Location",
                                "/api/events/1/entries/10"
                        )
                )
                .andExpect(
                        jsonPath("$.id")
                                .value(10)
                )
                .andExpect(
                        jsonPath("$.eventId")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.userId")
                                .value(1001)
                );

        verify(eventEntryService)
                .enter(
                        1L,
                        1001L
                );
    }

    @Test
    @DisplayName("사용자 ID가 없으면 400 응답을 반환한다")
    void rejectMissingUserId() throws Exception {

        String request = """
                {
                }
                """;

        mockMvc.perform(
                        post(
                                "/api/events/{eventId}/entries",
                                1L
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(request)
                )
                .andExpect(
                        status().isBadRequest()
                )
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "INVALID_REQUEST"
                                )
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "사용자 ID는 필수입니다."
                                )
                );
    }

    @Test
    @DisplayName("중복 신청하면 409 응답을 반환한다")
    void rejectDuplicateEntry() throws Exception {

        EventEntryRequest request =
                new EventEntryRequest(1001L);

        when(
                eventEntryService.enter(
                        1L,
                        1001L
                )
        ).thenThrow(
                new DuplicateEntryException(
                        1L,
                        1001L
                )
        );

        mockMvc.perform(
                        post(
                                "/api/events/{eventId}/entries",
                                1L
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        jsonMapper
                                                .writeValueAsString(
                                                        request
                                                )
                                )
                )
                .andExpect(
                        status().isConflict()
                )
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "DUPLICATE_ENTRY"
                                )
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "이미 신청한 이벤트입니다. eventId=1, userId=1001"
                                )
                );
    }

    /*
     * 새로 추가한 전략 선택 API도
     * Controller 테스트에 포함한다.
     */
    @Test
    @DisplayName(
            "Redis 전략으로 이벤트 신청에 성공하면 201 응답을 반환한다"
    )
    void enterWithRedisStrategy()
            throws Exception {

        // given
        EventEntryRequest request =
                new EventEntryRequest(
                        1001L
                );

        EventEntryResponse response =
                new EventEntryResponse(
                        10L,
                        1L,
                        1001L,
                        LocalDateTime.of(
                                2026,
                                8,
                                10,
                                12,
                                0
                        )
                );

        when(
                eventEntryStrategyService.enter(
                        EntryStrategy.REDIS,
                        1L,
                        1001L
                )
        ).thenReturn(
                response
        );

        // when & then
        mockMvc.perform(
                        post(
                                "/api/events/{eventId}/entries/strategies/{strategy}",
                                1L,
                                "redis"
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        jsonMapper
                                                .writeValueAsString(
                                                        request
                                                )
                                )
                )
                .andExpect(
                        status().isCreated()
                )
                .andExpect(
                        jsonPath("$.id")
                                .value(10)
                )
                .andExpect(
                        jsonPath("$.eventId")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.userId")
                                .value(1001)
                );

        verify(
                eventEntryStrategyService
        )
                .enter(
                        EntryStrategy.REDIS,
                        1L,
                        1001L
                );
    }
}