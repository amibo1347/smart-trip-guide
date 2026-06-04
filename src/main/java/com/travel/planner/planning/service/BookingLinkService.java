package com.travel.planner.planning.service;

import com.travel.planner.planning.dto.BookingLinksResponse;
import com.travel.planner.planning.dto.BookingLinksResponse.Link;
import com.travel.planner.planning.entity.Plan;
import com.travel.planner.planning.repository.PlanRepository;
import com.travel.planner.trip.entity.Trip;
import com.travel.planner.trip.service.TripService;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 항공/숙소 예약 '핸드오프' 딥링크 생성 (설계 4.E·4.C: 실시간 가용성/가격은 외부 사이트에서).
 * 무료·키 불필요·환각 없음.
 *
 * <p>조건(날짜·인원·목적지) 반영 방식 — prefilled=true 면 검색이 미리 채워짐:
 * <ul>
 *   <li><b>부킹닷컴</b>: 자유 텍스트 목적지(ss) + 날짜 + 인원(group_adults). 목적지/날짜/인원 모두 안정적으로 반영.</li>
 *   <li><b>구글 항공권</b>: tfs(protobuf, base64url) 딥링크로 출발/도착·왕복 날짜·인원 반영.
 *       (구글 항공권은 ?q= 자유텍스트를 폼에 안 채워서, 공식이 쓰는 tfs 방식으로 구성)</li>
 *   <li><b>스카이스캐너</b>: 경로에 IATA 코드 + 날짜(yyMMdd) + 인원(adults).</li>
 *   <li><b>아고다</b>: /ko-kr/search 에 체크인/아웃·숙박일수·인원·목적지(textToSearch) 반영. cityId 아는 도시는 city=도 부여해 도시 확정.</li>
 *   <li><b>구글 호텔</b>: 목적지+날짜는 반영(인원은 구글 제약으로 기본값 2명 고정).</li>
 *   <li><b>야놀자·여기어때</b>: 국내 숙소 위주라 해외 목적지에선 숨김(국내 목적지에서만 노출).</li>
 * </ul>
 * 항공/구글항공/스카이스캐너는 출발·도착 IATA 코드를 둘 다 아는 경우에만 조건 반영, 모르면 홈으로 핸드오프.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingLinkService {

    private final TripService tripService;
    private final PlanRepository planRepository;

    /** 도시/지역명 → IATA 공항 코드(구글항공 tfs·스카이스캐너 경로용). 목적지/출발지 문자열에 부분일치로 탐색. */
    private static final Map<String, String> AIRPORTS = Map.ofEntries(
            Map.entry("서울", "ICN"), Map.entry("인천", "ICN"), Map.entry("김포", "GMP"),
            Map.entry("부산", "PUS"), Map.entry("제주", "CJU"), Map.entry("대구", "TAE"),
            Map.entry("광주", "GWJ"), Map.entry("청주", "CJJ"), Map.entry("무안", "MWX"),
            Map.entry("오사카", "KIX"), Map.entry("도쿄", "NRT"), Map.entry("동경", "NRT"),
            Map.entry("후쿠오카", "FUK"), Map.entry("삿포로", "CTS"), Map.entry("오키나와", "OKA"),
            Map.entry("나고야", "NGO"), Map.entry("도야마", "TOY"), Map.entry("기타큐슈", "KKJ"),
            Map.entry("방콕", "BKK"), Map.entry("다낭", "DAD"), Map.entry("하노이", "HAN"),
            Map.entry("호치민", "SGN"), Map.entry("나트랑", "CXR"), Map.entry("푸꾸옥", "PQC"),
            Map.entry("싱가포르", "SIN"), Map.entry("쿠알라룸푸르", "KUL"), Map.entry("발리", "DPS"),
            Map.entry("세부", "CEB"), Map.entry("마닐라", "MNL"), Map.entry("보라카이", "MPH"),
            Map.entry("홍콩", "HKG"), Map.entry("타이베이", "TPE"), Map.entry("대만", "TPE"),
            Map.entry("괌", "GUM"), Map.entry("사이판", "SPN"),
            Map.entry("파리", "CDG"), Map.entry("런던", "LHR"), Map.entry("로마", "FCO"),
            Map.entry("바르셀로나", "BCN"), Map.entry("프랑크푸르트", "FRA"), Map.entry("암스테르담", "AMS"),
            Map.entry("뉴욕", "JFK"), Map.entry("로스앤젤레스", "LAX"), Map.entry("하와이", "HNL"),
            Map.entry("호놀룰루", "HNL"), Map.entry("시드니", "SYD"));

    /**
     * 도시명 → 아고다 검색용 숫자 cityId. 아고다는 /ko-kr/search?city={id}&... 형태라야 필터(날짜·인원)가 걸린다.
     * (slug 페이지 /city/..-jp.html 는 랜딩일 뿐 필터가 안 걸림.)
     * cityId를 모르는 도시는 textToSearch만으로 검색 시도(아고다가 못 풀면 검색창은 채워짐).
     * 새 도시 추가법: 아고다에서 그 도시를 검색한 뒤 주소창의 city= 값을 그대로 넣으면 됨.
     */
    private static final Map<String, String> AGODA_CITY = Map.ofEntries(
            Map.entry("오사카", "9590"),   // 확인됨
            Map.entry("방콕", "9395"));    // 확인됨

    /** 국내 도시/지역 키워드. 야놀자·여기어때는 해당 목적지에서만 노출. */
    private static final List<String> DOMESTIC = List.of(
            "서울", "인천", "부산", "제주", "대구", "광주", "대전", "울산", "세종",
            "강릉", "속초", "양양", "경주", "여수", "전주", "춘천", "가평", "포항", "통영",
            "안동", "목포", "순천", "거제", "남해", "태안", "보령", "평창", "정선", "담양",
            "수원", "용인", "파주", "경기", "강원", "충청", "전라", "경상");

    private static final DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("yyMMdd");

    public BookingLinksResponse build(Long tripId, Long userId, String originParam) {
        Trip trip = tripService.getOwnedTrip(tripId, userId);

        // 목적지: 최신 AI 플랜이 파악한 도시 → 없으면 여행 제목
        String destination = planRepository.findTopByTripIdOrderByVersionDesc(tripId)
                .map(Plan::getDestinationCity)
                .filter(s -> s != null && !s.isBlank())
                .orElse(trip.getTitle());
        String origin = (originParam == null || originParam.isBlank()) ? "서울" : originParam.trim();

        LocalDate start = trip.getStartDate();
        LocalDate end = trip.getEndDate();
        String ci = start.toString();   // YYYY-MM-DD
        String co = end.toString();
        int pax = Math.max(1, trip.getHeadcount());
        long nights = Math.max(1, ChronoUnit.DAYS.between(start, end));

        // ---------- 항공권 ----------
        List<Link> flights = new ArrayList<>();
        String oc = airport(origin);
        String dc = airport(destination);
        if (oc != null && dc != null) {
            flights.add(new Link("구글 항공권", googleFlightsUrl(oc, dc, start, end, pax), true));
            flights.add(new Link("스카이스캐너",
                    "https://www.skyscanner.co.kr/transport/flights/"
                            + oc.toLowerCase() + "/" + dc.toLowerCase() + "/"
                            + start.format(YYMMDD) + "/" + end.format(YYMMDD) + "/?adults=" + pax, true));
        } else {
            flights.add(new Link("구글 항공권", "https://www.google.com/travel/flights", false));
            flights.add(new Link("스카이스캐너", "https://www.skyscanner.co.kr/", false));
        }

        // ---------- 숙소 ----------
        List<Link> hotels = new ArrayList<>();
        // 부킹닷컴: 목적지(자유 텍스트)·날짜·인원 모두 반영 — 가장 안정적
        hotels.add(new Link("부킹닷컴",
                "https://www.booking.com/searchresults.ko.html?ss=" + enc(destination)
                        + "&checkin=" + ci + "&checkout=" + co
                        + "&group_adults=" + pax + "&no_rooms=1&group_children=0", true));

        // 아고다: /ko-kr/search 에 날짜·숙박일수·인원·목적지를 실어 필터 적용
        String agoda = "https://www.agoda.com/ko-kr/search?checkIn=" + ci + "&checkOut=" + co
                + "&los=" + nights + "&rooms=1&adults=" + pax + "&children=0&currency=KRW"
                + "&textToSearch=" + enc(destination);
        String cityId = agodaCityId(destination);
        if (cityId != null) {
            agoda += "&city=" + cityId;   // cityId가 있어야 도시가 확실히 잡힘
        }
        hotels.add(new Link("아고다", agoda, true));

        // 구글 호텔: 목적지+날짜 반영(인원은 구글 제약으로 기본값)
        hotels.add(new Link("구글 호텔",
                google("travel/search", destination + " 호텔 " + ci + " ~ " + co), true));

        // 야놀자·여기어때: 국내 목적지에서만
        if (isDomestic(destination)) {
            hotels.add(new Link("야놀자", "https://www.yanolja.com/", false));
            hotels.add(new Link("여기어때", "https://www.goodchoice.kr/", false));
        }

        return new BookingLinksResponse(destination, origin, flights, hotels);
    }

    /** 문자열에 포함된 첫 도시 키워드의 IATA 코드(없으면 null). */
    private static String airport(String place) {
        if (place == null) return null;
        String p = place.replace(" ", "");
        for (Map.Entry<String, String> e : AIRPORTS.entrySet()) {
            if (p.contains(e.getKey())) return e.getValue();
        }
        return null;
    }

    private static String agodaCityId(String dest) {
        if (dest == null) return null;
        String d = dest.replace(" ", "");
        for (Map.Entry<String, String> e : AGODA_CITY.entrySet()) {
            if (d.contains(e.getKey())) return e.getValue();
        }
        return null;
    }

    private static boolean isDomestic(String dest) {
        if (dest == null) return false;
        String d = dest.replace(" ", "");
        for (String k : DOMESTIC) {
            if (d.contains(k)) return true;
        }
        return false;
    }

    // ---------- 구글 항공권 tfs(protobuf) ----------
    // 스키마(fast-flights .proto): Info{ data=3 repeated FlightData, passengers=8 repeated, seat=9, trip=19 }
    //   FlightData{ date=2, from_airport=13, to_airport=14 }  Airport{ airport=2 }
    //   Seat: ECONOMY=1 / Trip: ROUND_TRIP=1 / Passenger: ADULT=1
    private static String googleFlightsUrl(String oc, String dc, LocalDate start, LocalDate end, int pax) {
        ByteArrayOutputStream info = new ByteArrayOutputStream();
        pbBytes(info, 3, leg(start.toString(), oc, dc));   // 출발편
        pbBytes(info, 3, leg(end.toString(), dc, oc));     // 귀국편 → 자동 왕복
        for (int i = 0; i < pax; i++) pbVarint(info, 8, 1); // 성인 N명
        pbVarint(info, 9, 1);   // 좌석: 이코노미
        pbVarint(info, 19, 1);  // 여정: 왕복
        String tfs = Base64.getUrlEncoder().withoutPadding().encodeToString(info.toByteArray());
        return "https://www.google.com/travel/flights?tfs=" + tfs + "&hl=ko&curr=KRW";
    }

    private static byte[] leg(String date, String from, String to) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        pbString(o, 2, date);
        pbBytes(o, 13, airportPb(from));
        pbBytes(o, 14, airportPb(to));
        return o.toByteArray();
    }

    private static byte[] airportPb(String code) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        pbString(o, 2, code);
        return o.toByteArray();
    }

    private static void pbVarintRaw(ByteArrayOutputStream o, long v) {
        while ((v & ~0x7FL) != 0) {
            o.write((int) ((v & 0x7F) | 0x80));
            v >>>= 7;
        }
        o.write((int) (v & 0x7F));
    }

    private static void pbTag(ByteArrayOutputStream o, int field, int wireType) {
        pbVarintRaw(o, ((long) field << 3) | wireType);
    }

    private static void pbVarint(ByteArrayOutputStream o, int field, long value) {
        pbTag(o, field, 0);
        pbVarintRaw(o, value);
    }

    private static void pbBytes(ByteArrayOutputStream o, int field, byte[] data) {
        pbTag(o, field, 2);
        pbVarintRaw(o, data.length);
        o.writeBytes(data);
    }

    private static void pbString(ByteArrayOutputStream o, int field, String s) {
        pbBytes(o, field, s.getBytes(StandardCharsets.UTF_8));
    }

    private static String google(String path, String query) {
        return "https://www.google.com/" + path + "?q=" + enc(query);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
