package com.travel.planner.discovery;

import java.math.BigDecimal;

/**
 * 탐색으로 찾은 장소 한 곳. 화면에 그대로 뿌릴 수 있는 형태
 * (이름 · 대표사진 · 주소 · 좌표 · 기준점 거리 · 평점/리뷰수).
 *
 * <p>소스(Google Places / OSM+위키)에 상관없이 같은 모양이라 화면은 출처를 몰라도 된다.
 *
 * @param providerId  소스별 원본 id (중복 제거·상세조회용)
 * @param photoUrl    대표 사진 URL. Google 경로는 키 노출을 피하려고 우리 서버 프록시 주소가 들어간다
 * @param rating      평점(0~5). 없으면 null
 * @param reviewCount 리뷰 수 — 인기도 정렬의 근거. 없으면 0
 * @param distanceKm  기준 좌표로부터의 거리(km). 기준이 없으면 null
 */
public record DiscoveredPlace(
        String providerId,
        String name,
        PlaceCategory category,
        String subtype,
        BigDecimal latitude,
        BigDecimal longitude,
        String address,
        String photoUrl,
        Double rating,
        long reviewCount,
        Double distanceKm) {

    public DiscoveredPlace withDistance(Double km) {
        return new DiscoveredPlace(providerId, name, category, subtype, latitude, longitude,
                address, photoUrl, rating, reviewCount, km);
    }

    public DiscoveredPlace withPhoto(String url) {
        return new DiscoveredPlace(providerId, name, category, subtype, latitude, longitude,
                address, url, rating, reviewCount, distanceKm);
    }
}
