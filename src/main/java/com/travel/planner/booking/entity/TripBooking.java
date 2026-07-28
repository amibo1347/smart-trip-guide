package com.travel.planner.booking.entity;

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
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 확정 예약 — 외부 사이트에서 고른 항공/숙소를 링크로 가져와 저장.
 */
@Entity
@Table(name = "trip_bookings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripBooking extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BookingType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "booking_url", length = 1000)
    private String bookingUrl;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    /** 예약 확인증 파일(항공권 e-티켓·숙소 바우처). 이미지 또는 PDF. null 이면 미첨부. */
    @Column(name = "ticket_url", length = 500)
    private String ticketUrl;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(length = 300)
    private String memo;

    @Builder
    private TripBooking(Long tripId, BookingType type, String title, BigDecimal price,
                        String bookingUrl, String imageUrl, LocalDate startDate, LocalDate endDate, String memo) {
        this.tripId = tripId;
        this.type = type;
        this.title = title;
        this.price = price;
        this.bookingUrl = bookingUrl;
        this.imageUrl = imageUrl;
        this.startDate = startDate;
        this.endDate = endDate;
        this.memo = memo;
    }

    /** 예약 확인증 첨부/교체. */
    public void attachTicket(String url) {
        this.ticketUrl = url;
    }

    /** 예약 확인증 제거. */
    public void removeTicket() {
        this.ticketUrl = null;
    }
}
