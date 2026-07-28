package com.travel.planner.discovery;

/**
 * 장소 탐색 카테고리. 각 값은 OSM(Overpass) 태그 필터로 번역된다.
 * 태그 목록은 여기 한 곳에서만 관리한다 — 화면·서비스는 이 enum 만 안다.
 */
public enum PlaceCategory {

    /** 관광: 명소·박물관·전망대·유적 등 */
    SIGHT("""
            node["tourism"~"^(attraction|museum|viewpoint|theme_park|zoo|aquarium|gallery|artwork)$"]["name"](around:%d,%s,%s);
            way["tourism"~"^(attraction|museum|theme_park|zoo|aquarium)$"]["name"](around:%d,%s,%s);
            node["historic"]["name"](around:%d,%s,%s);
            """),

    /** 식당·카페 */
    FOOD("""
            node["amenity"~"^(restaurant|cafe|fast_food|bakery|bar|pub)$"]["name"](around:%d,%s,%s);
            way["amenity"~"^(restaurant|cafe)$"]["name"](around:%d,%s,%s);
            """),

    /** 쇼핑 */
    SHOPPING("""
            node["shop"~"^(mall|department_store|supermarket|clothes|gift|convenience|books|electronics)$"]["name"](around:%d,%s,%s);
            way["shop"~"^(mall|department_store|supermarket)$"]["name"](around:%d,%s,%s);
            node["amenity"="marketplace"]["name"](around:%d,%s,%s);
            """);

    private final String template;

    PlaceCategory(String template) {
        this.template = template;
    }

    /** 반경(m)과 중심 좌표를 끼운 Overpass 절(clause). 템플릿의 (반경,위도,경도) 반복 수만큼 인자를 채운다. */
    String toOverpassClause(int radiusMeters, String lat, String lon) {
        int slots = template.split("\\(around:", -1).length - 1;
        Object[] args = new Object[slots * 3];
        for (int i = 0; i < slots; i++) {
            args[i * 3] = radiusMeters;
            args[i * 3 + 1] = lat;
            args[i * 3 + 2] = lon;
        }
        return template.formatted(args);
    }
}
