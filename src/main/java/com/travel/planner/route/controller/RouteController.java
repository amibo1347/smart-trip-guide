package com.travel.planner.route.controller;

import com.travel.planner.account.security.CurrentUser;
import com.travel.planner.route.dto.RouteDtos.RouteResponse;
import com.travel.planner.route.dto.RouteDtos.SetLocationRequest;
import com.travel.planner.route.service.RouteService;
import com.travel.planner.route.service.StopLocationService;
import com.travel.planner.tracking.service.GeocodingService;
import com.travel.planner.tracking.service.GeocodingService.PlaceCandidate;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 일정 기반 노선도 API.
 * - GET   /api/trips/{tripId}/route            : 노선도(좌표 변환된 일정 + 장소별 기록)
 * - PATCH /api/plan-items/{itemId}/place       : 위치 수동 지정/수정
 * - POST  /api/plan-items/{itemId}/place/auto  : 수동 지정 해제 → 자동으로 다시 찾기
 * - GET   /api/places/search?q=                : 수동 지정 화면의 장소 검색
 *
 * <p>장소에 사진·지출·메모를 남기는 건 기존 통합 기록 API(POST /api/trips/{id}/moments)를 재사용한다
 * — 기록 payload 에 planItemId 를 넣으면 그 장소에 연결된다.
 */
@RestController
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;
    private final StopLocationService stopLocationService;
    private final GeocodingService geocodingService;
    private final CurrentUser currentUser;

    @GetMapping("/api/trips/{tripId}/route")
    public RouteResponse route(@PathVariable Long tripId, Authentication auth) {
        return routeService.getRoute(tripId, currentUser.requireId(auth));
    }

    /** 지도에서 직접 찍은(또는 검색해 고른) 좌표로 위치 지정. */
    @PatchMapping("/api/plan-items/{itemId}/place")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setLocation(@PathVariable Long itemId,
                            @Valid @RequestBody SetLocationRequest request,
                            Authentication auth) {
        stopLocationService.setLocation(itemId, request.name(), request.address(),
                request.latitude(), request.longitude(), currentUser.requireId(auth));
    }

    /** 수동 지정 해제 — 다음 노선도 조회에서 자동 지오코딩이 다시 시도한다. */
    @PostMapping("/api/plan-items/{itemId}/place/auto")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetToAuto(@PathVariable Long itemId, Authentication auth) {
        stopLocationService.resetToAuto(itemId, currentUser.requireId(auth));
    }

    /** 장소 검색(수동 지정용 후보 목록). */
    @GetMapping("/api/places/search")
    public List<PlaceCandidate> search(@RequestParam("q") String query) {
        return geocodingService.search(query, 6);
    }
}
