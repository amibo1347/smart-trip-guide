package com.travel.planner.planning.entity;

import com.travel.planner.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 장소 마스터 (설계 2.2 Place). 외부 지도 API 결과 캐시 또는 수동입력.
 */
@Entity
@Table(name = "places")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Place extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlaceProvider provider;

    @Column(name = "provider_place_id", length = 100)
    private String providerPlaceId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 50)
    private String category;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    /** 좌표가 비어 있을 때 지오코딩을 시도했는지. 실패한 장소를 매번 다시 변환하지 않도록 한다. */
    @Column(name = "geocode_attempted", nullable = false)
    private boolean geocodeAttempted;

    @Column(length = 255)
    private String address;

    @Builder
    private Place(PlaceProvider provider, String providerPlaceId, String name,
                  String category, BigDecimal latitude, BigDecimal longitude, String address) {
        this.provider = provider == null ? PlaceProvider.MANUAL : provider;
        this.providerPlaceId = providerPlaceId;
        this.name = name;
        this.category = category;
        this.latitude = latitude;
        this.longitude = longitude;
        this.address = address;
        // 좌표를 갖고 생성되면 이미 확정된 위치이므로 재지오코딩 대상에서 제외.
        this.geocodeAttempted = latitude != null && longitude != null;
    }

    public boolean hasCoords() {
        return latitude != null && longitude != null;
    }

    /** 지오코딩 결과 좌표를 채운다(시도 완료로 표시). */
    public void applyGeocode(BigDecimal lat, BigDecimal lng) {
        this.latitude = lat;
        this.longitude = lng;
        this.geocodeAttempted = true;
    }

    /** 지오코딩을 시도했으나 좌표를 얻지 못한 경우(재시도 방지용 표시). */
    public void markGeocodeAttempted() {
        this.geocodeAttempted = true;
    }

    /** 사용자가 지도에서 직접 지정한 좌표. 자동 지오코딩이 덮어쓰지 않도록 시도 완료로 표시한다. */
    public void applyManualCoords(BigDecimal lat, BigDecimal lng) {
        this.latitude = lat;
        this.longitude = lng;
        this.geocodeAttempted = true;
        this.provider = PlaceProvider.MANUAL;
    }

    /** 이름/주소 갱신(값이 있을 때만). */
    public void updateInfo(String name, String address) {
        if (name != null && !name.isBlank()) {
            this.name = name.trim();
        }
        if (address != null) {
            this.address = address.isBlank() ? null : address.trim();
        }
    }

    /** 좌표를 비우고 재지오코딩 대상으로 되돌린다(= '자동으로 다시 찾기'). */
    public void resetToAuto() {
        this.latitude = null;
        this.longitude = null;
        this.geocodeAttempted = false;
    }
}
