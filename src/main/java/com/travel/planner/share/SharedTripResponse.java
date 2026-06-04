package com.travel.planner.share;

import com.travel.planner.booking.dto.BookingResponse;
import com.travel.planner.currency.CurrencyService.CurrencyInfo;
import com.travel.planner.planning.dto.PlanResponse;
import java.time.LocalDate;
import java.util.List;

/**
 * 읽기 전용 공유 뷰 응답. 로그인 없이 노출되므로 소유자 식별정보(userId 등)는 담지 않는다.
 */
public record SharedTripResponse(
        String title,
        LocalDate startDate,
        LocalDate endDate,
        int headcount,
        String concept,
        PlanResponse plan,
        List<BookingResponse> bookings,
        CurrencyInfo currency
) {
}
