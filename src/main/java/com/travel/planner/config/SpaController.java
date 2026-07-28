package com.travel.planner.config;

import com.travel.planner.trip.entity.Trip;
import com.travel.planner.trip.repository.TripRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.HtmlUtils;

/**
 * 공유 링크(/share/{token}) 서빙.
 *
 * <p>정적 index.html 을 그대로 내려도 React 는 뜨지만, 카카오톡·페이스북 등 SNS 크롤러는
 * JS 를 실행하지 않으므로 미리보기 카드에 앱 기본 문구만 나온다. 그래서 여기서 index.html 을 읽어
 * 여행 제목·기간을 담은 OG 메타로 <b>치환</b>해 내려준다 — 링크를 받은 사람이 무슨 여행인지 바로 알 수 있다.
 *
 * <p>토큰이 유효하지 않으면 기본 메타 그대로 내려보내고, 실제 "없는 일정" 안내는 React 가 처리한다.
 */
@Controller
@RequiredArgsConstructor
public class SpaController {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    /** index.html 의 이 자리표시자를 여행별 OG 메타로 바꾼다(프론트 빌드 산출물에 포함). */
    private static final String PLACEHOLDER = "<!--OG-->";

    private final TripRepository tripRepository;

    @GetMapping("/share/{token}")
    @ResponseBody
    public org.springframework.http.ResponseEntity<String> share(@PathVariable String token,
                                                                 HttpServletRequest request) throws IOException {
        String html = new ClassPathResource("static/index.html")
                .getContentAsString(StandardCharsets.UTF_8);

        String meta = tripRepository.findByShareToken(token)
                .map(trip -> ogMeta(trip, absoluteUrl(request)))
                .orElse("");

        return org.springframework.http.ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html.replace(PLACEHOLDER, meta));
    }

    /** 프록시(Caddy) 뒤에서도 올바른 공개 URL 이 나오도록 요청 URL 을 그대로 사용. */
    private static String absoluteUrl(HttpServletRequest request) {
        return request.getRequestURL().toString();
    }

    private static String ogMeta(Trip trip, String url) {
        String title = HtmlUtils.htmlEscape(trip.getTitle()) + " · 여행 일정";
        String period = trip.getStartDate() == null || trip.getEndDate() == null ? ""
                : DATE.format(trip.getStartDate()) + " ~ " + DATE.format(trip.getEndDate()) + " · ";
        String description = HtmlUtils.htmlEscape(period + trip.getHeadcount() + "명 일정을 확인해 보세요.");
        String image = url.replaceFirst("/share/.*$", "") + "/icons/icon-512.png";

        return """
                <meta property="og:type" content="article" />
                <meta property="og:title" content="%s" />
                <meta property="og:description" content="%s" />
                <meta property="og:url" content="%s" />
                <meta property="og:image" content="%s" />
                <meta name="twitter:card" content="summary_large_image" />
                <meta name="twitter:title" content="%s" />
                <meta name="twitter:description" content="%s" />
                <meta name="twitter:image" content="%s" />
                """.formatted(title, description, HtmlUtils.htmlEscape(url), image, title, description, image);
    }
}
