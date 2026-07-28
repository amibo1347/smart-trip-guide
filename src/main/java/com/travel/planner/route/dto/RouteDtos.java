package com.travel.planner.route.dto;

import com.travel.planner.planning.entity.PlanItemType;
import com.travel.planner.tracking.dto.MomentResponses.MomentResponse;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** 노선도(일정 기반 지도) 응답 DTO 묶음. */
public final class RouteDtos {

    private RouteDtos() {
    }

    /** 위치 수동 지정 요청(지도에서 찍은 좌표 또는 검색으로 고른 후보). */
    public record SetLocationRequest(
            @Size(max = 150) String name,
            @Size(max = 255) String address,
            @NotNull BigDecimal latitude,
            @NotNull BigDecimal longitude) {
    }

    /**
     * 한 장소에 남긴 기록(사진·지출·메모·기분을 함께 담는 moment). 원래 UX(위치에 사진+지출+메모)를
     * 노선도의 장소 단위로 되살린 것.
     */
    public record RecordResponse(
            Long id, String photoUrl, String mood, BigDecimal amount, String category, String memo) {
        public static RecordResponse from(MomentResponse m) {
            return new RecordResponse(m.id(), m.photoUrl(), m.mood(), m.amount(), m.category(), m.memo());
        }
    }

    /**
     * 노선 위의 한 정거장(= 좌표가 있는 일정 항목).
     * order 는 여행 전체를 통틀어 방문 순서(1부터) — 지도 마커 번호로 쓴다.
     */
    public record Stop(
            Long itemId,
            int order,
            int dayNo,
            LocalDate date,
            String title,
            PlanItemType type,
            LocalTime plannedStart,
            BigDecimal latitude,
            BigDecimal longitude,
            String address,
            List<RecordResponse> records) {
    }

    /**
     * 좌표를 아직 얻지 못한(또는 장소가 없는) 항목 — 지도에는 못 올리지만
     * 기록(사진·지출·메모)은 붙일 수 있도록 목록으로 함께 내려준다.
     */
    public record UnlocatedStop(
            Long itemId, int dayNo, LocalDate date, String title, PlanItemType type,
            List<RecordResponse> records) {
    }

    /**
     * 노선도 전체.
     * @param pending 아직 지오코딩하지 못한(다음 호출에서 변환될) 장소 수. 0 이면 좌표 변환 완료.
     */
    public record RouteResponse(
            boolean hasPlan,
            BigDecimal centerLat,
            BigDecimal centerLng,
            List<Stop> stops,
            List<UnlocatedStop> unlocated,
            int pending) {
    }
}
