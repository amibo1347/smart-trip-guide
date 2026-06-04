package com.travel.planner.booking.service;

import java.net.InetAddress;
import java.net.URI;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

/**
 * 붙여넣은 예약 페이지 URL에서 '이름(제목)'만 가져온다. (사진/가격 미리보기는 하지 않음)
 * OG/트위터 title → 못 읽으면 URL 슬러그에서 복원(부킹/아고다처럼 봇 차단·SPA여도 이름은 채워줌).
 */
@Service
public class LinkTitleService {

    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    public String fetchTitle(String rawUrl) {
        URI uri = parseAndValidate(rawUrl);
        try {
            Document doc = Jsoup.connect(uri.toString())
                    .userAgent(UA)
                    .header("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.8")
                    .referrer("https://www.google.com/")
                    .timeout(8000)
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .get();
            String title = firstNonBlank(meta(doc, "og:title"), meta(doc, "twitter:title"));
            if (title == null) {
                title = titleFromUrl(uri);
            }
            if (title == null) {
                title = blankToNull(doc.title());
            }
            if (title == null) {
                throw new IllegalArgumentException("이름을 가져오지 못했습니다. 직접 입력해 주세요.");
            }
            return title;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            String title = titleFromUrl(uri);
            if (title != null) {
                return title;
            }
            throw new IllegalArgumentException("이름을 가져오지 못했습니다. 직접 입력해 주세요.");
        }
    }

    /** URL 경로 슬러그에서 숙소/대상 이름 복원. 예) agoda.com/ko-kr/welina-hotel-umeda/hotel/.. → "Welina Hotel Umeda" */
    private static String titleFromUrl(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isBlank()) {
            return null;
        }
        String host = uri.getHost() == null ? "" : uri.getHost();
        String[] seg = path.split("/");
        String slug = null;
        if (host.contains("agoda.")) {
            for (int i = 1; i < seg.length; i++) {
                if (seg[i].equals("hotel") && !seg[i - 1].isBlank()) {
                    slug = seg[i - 1];
                    break;
                }
            }
        }
        if (slug == null) {
            for (int i = seg.length - 1; i >= 0; i--) {
                if (!seg[i].isBlank()) {
                    slug = seg[i];
                    break;
                }
            }
        }
        if (slug == null || slug.isBlank()) {
            return null;
        }
        slug = slug.replaceAll("\\.[a-z]{2}([-_][a-z]{2})?\\.html?$", "").replaceAll("\\.html?$", "");
        slug = slug.replace('-', ' ').replace('_', ' ').replaceAll("\\s+", " ").trim();
        if (slug.length() < 2) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String w : slug.split(" ")) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
            }
        }
        return sb.toString().trim();
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
        try { // SSRF 방지: 내부망/로컬 차단
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
        Element el = doc.selectFirst("meta[property=\"" + key + "\"]");
        if (el == null) {
            el = doc.selectFirst("meta[name=\"" + key + "\"]");
        }
        return el == null ? null : blankToNull(el.attr("content"));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return (b != null && !b.isBlank()) ? b : null;
    }
}
