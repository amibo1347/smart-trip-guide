package com.travel.planner.booking.service;

import com.travel.planner.booking.dto.LinkPreviewResponse;
import java.net.InetAddress;
import java.net.URI;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

/**
 * 사용자가 붙여넣은 예약 페이지 URL의 Open Graph 메타정보(제목/이미지/설명)를 가져온다.
 * (카카오톡 링크 미리보기와 동일 원리.) 가격은 사이트별로 불확실해 추출하지 않음.
 */
@Service
public class LinkPreviewService {

    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    public LinkPreviewResponse fetch(String rawUrl) {
        URI uri = parseAndValidate(rawUrl);
        try {
            Document doc = Jsoup.connect(uri.toString())
                    .userAgent(UA)
                    .timeout(7000)
                    .followRedirects(true)
                    .get();

            String title = firstNonBlank(meta(doc, "og:title"), doc.title());
            String image = meta(doc, "og:image");
            String desc = firstNonBlank(meta(doc, "og:description"), meta(doc, "description"));
            String site = meta(doc, "og:site_name");

            return new LinkPreviewResponse(uri.toString(), title, image, desc, site);
        } catch (Exception e) {
            throw new IllegalArgumentException("링크 정보를 가져오지 못했습니다. URL을 확인하거나, 제목/가격을 직접 입력해 저장하세요.");
        }
    }

    private URI parseAndValidate(String rawUrl) {
        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("올바른 URL이 아닙니다.");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null) {
            throw new IllegalArgumentException("http/https URL만 지원합니다.");
        }
        // SSRF 방지: 내부망/로컬 주소 차단
        try {
            InetAddress addr = InetAddress.getByName(uri.getHost());
            if (addr.isLoopbackAddress() || addr.isSiteLocalAddress()
                    || addr.isLinkLocalAddress() || addr.isAnyLocalAddress()) {
                throw new IllegalArgumentException("내부 주소는 허용되지 않습니다.");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("호스트를 확인할 수 없습니다.");
        }
        return uri;
    }

    private static String meta(Document doc, String key) {
        Element el = doc.selectFirst("meta[property=" + key + "]");
        if (el == null) {
            el = doc.selectFirst("meta[name=" + key + "]");
        }
        return el == null ? null : el.attr("content");
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return (b != null && !b.isBlank()) ? b : null;
    }
}
