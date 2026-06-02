package com.travel.planner.planning.service;

import com.travel.planner.planning.dto.BookingLinksResponse;
import com.travel.planner.planning.dto.BookingLinksResponse.Link;
import com.travel.planner.planning.entity.Plan;
import com.travel.planner.planning.repository.PlanRepository;
import com.travel.planner.trip.entity.Trip;
import com.travel.planner.trip.service.TripService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 항공/숙소 예약 '핸드오프' 딥링크 생성 (설계 4.E·4.C: 실시간 가용성/가격은 외부 사이트에서).
 * 무료·키 불필요·환각 없음. 검색이 미리 채워지는 구글/네이버는 prefilled=true.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingLinkService {

    private final TripService tripService;
    private final PlanRepository planRepository;

    public BookingLinksResponse build(Long tripId, Long userId, String originParam) {
        Trip trip = tripService.getOwnedTrip(tripId, userId);

        // 목적지: 최신 AI 플랜이 파악한 도시 → 없으면 여행 제목
        String destination = planRepository.findTopByTripIdOrderByVersionDesc(tripId)
                .map(Plan::getDestinationCity)
                .filter(s -> s != null && !s.isBlank())
                .orElse(trip.getTitle());
        String origin = (originParam == null || originParam.isBlank()) ? "서울" : originParam.trim();

        String ci = trip.getStartDate().toString();   // YYYY-MM-DD
        String co = trip.getEndDate().toString();
        int pax = trip.getHeadcount();

        List<Link> flights = List.of(
                new Link("구글 항공권",
                        google("travel/flights", origin + "에서 " + destination + " 가는 항공권 " + ci), true),
                new Link("네이버 항공권",
                        naver(destination + " 항공권 " + ci), true),
                new Link("스카이스캐너", "https://www.skyscanner.co.kr/", false));

        List<Link> hotels = List.of(
                new Link("구글 호텔",
                        google("travel/search", destination + " 호텔 " + ci + " ~ " + co + " " + pax + "명"), true),
                new Link("네이버 숙소",
                        naver(destination + " 호텔 " + pax + "인"), true),
                new Link("야놀자", "https://www.yanolja.com/", false),
                new Link("여기어때", "https://www.goodchoice.kr/", false),
                new Link("아고다", "https://www.agoda.com/", false));

        return new BookingLinksResponse(destination, origin, flights, hotels);
    }

    private static String google(String path, String query) {
        return "https://www.google.com/" + path + "?q=" + enc(query);
    }

    private static String naver(String query) {
        return "https://search.naver.com/search.naver?query=" + enc(query);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
