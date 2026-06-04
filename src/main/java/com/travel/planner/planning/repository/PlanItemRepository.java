package com.travel.planner.planning.repository;

import com.travel.planner.planning.entity.PlanItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanItemRepository extends JpaRepository<PlanItem, Long> {

    /** 특정 확정 예약에서 자동 생성된 일정 항목들(예약 삭제 시 정리용). */
    List<PlanItem> findByBookingId(Long bookingId);
}
