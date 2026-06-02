package com.travel.planner.booking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.planner.booking.dto.LinkPreviewResponse;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

/**
 * 사용자가 붙여넣은 예약 페이지 URL의 메타정보(제목/이미지/설명)와,
 * 사이트가 노출한 경우에 한해 가격(메타 product:price / JSON-LD offers.price)을 가져온다.
 * 가격이 없으면 null — 지어내지 않음(환각 방지).
 */
@Service
@RequiredArgsConstructor
public class LinkPreviewService {

    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    private final ObjectMapper objectMapper;

    public LinkPreviewResponse fetch(String rawUrl) {
        URI uri = parseAndValidate(rawUrl);
        try {
            Document doc = Jsoup.connect(uri.toString())
                    .userAgent(UA).timeout(7000).followRedirects(true).get();

            String title = firstNonBlank(meta(doc, "og:title"), doc.title());
            String image = meta(doc, "og:image");
            String desc = firstNonBlank(meta(doc, "og:description"), meta(doc, "description"));
            String site = meta(doc, "og:site_name");

            BigDecimal price = extractPrice(doc);
            String currency = firstNonBlank(meta(doc, "product:price:currency"), meta(doc, "og:price:currency"));

            return new LinkPreviewResponse(uri.toString(), title, image, desc, site, price, currency);
        } catch (Exception e) {
            throw new IllegalArgumentException("링크 정보를 가져오지 못했습니다. URL을 확인하거나, 제목/가격을 직접 입력해 저장하세요.");
        }
    }

    /** 메타(product:price:amount, og:price:amount) → 없으면 JSON-LD offers.price. 없으면 null. */
    private BigDecimal extractPrice(Document doc) {
        BigDecimal metaPrice = parsePrice(firstNonBlank(meta(doc, "product:price:amount"), meta(doc, "og:price:amount")));
        if (metaPrice != null) {
            return metaPrice;
        }
        for (Element script : doc.select("script[type=application/ld+json]")) {
            try {
                JsonNode root = objectMapper.readTree(script.data());
                BigDecimal found = findPrice(root);
                if (found != null) {
                    return found;
                }
            } catch (Exception ignored) {
                // JSON-LD 파싱 실패는 무시
            }
        }
        return null;
    }

    /** JSON-LD 트리에서 offers.price / price / lowPrice 를 재귀 탐색. */
    private BigDecimal findPrice(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isArray()) {
            for (JsonNode el : node) {
                BigDecimal r = findPrice(el);
                if (r != null) {
                    return r;
                }
            }
            return null;
        }
        if (node.isObject()) {
            JsonNode offers = node.get("offers");
            if (offers != null) {
                BigDecimal r = findPrice(offers);
                if (r != null) {
                    return r;
                }
            }
            BigDecimal p = parsePrice(textOf(node.get("price")));
            if (p != null) {
                return p;
            }
            return parsePrice(textOf(node.get("lowPrice")));
        }
        return null;
    }

    private static String textOf(JsonNode n) {
        return (n == null || n.isNull()) ? null : n.asText();
    }

    private static BigDecimal parsePrice(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String cleaned = s.replaceAll("[^0-9.]", "");
        if (cleaned.isEmpty() || cleaned.equals(".")) {
            return null;
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
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
