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
    }
}
