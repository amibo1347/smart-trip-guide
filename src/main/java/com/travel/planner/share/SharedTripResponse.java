package com.travel.planner.share;

import com.travel.planner.booking.dto.BookingResponse;
import com.travel.planner.checklist.dto.ChecklistDtos.ChecklistItemResponse;
import com.travel.planner.currency.CurrencyService.CurrencyInfo;
import com.travel.planner.planning.dto.PlanResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 읽기 전용 공유 뷰 응답. 로그인 없이 노출되므로 소유자 식별정보(userId 등)는 담지 않는다.
 * 준비물 체크리스트와 1인당 분담액도 함께 내려 받은 사람이 같은 화면을 볼 수 있게 한다.
 */
public record SharedTripResponse(
        String title,
        LocalDate startDate,
        LocalDate endDate,
        int headcount,
        String concept,
        PlanResponse plan,
        List<BookingResponse> bookings,
        CurrencyInfo currency,
        List<ChecklistItemResponse> checklist,
        BigDecimal plannedTotal,  // 활동비(예상) + 확정 예약 합계
        BigDecimal perPerson      // plannedTotal / headcount (인원 0 이면 null)
) {
}
