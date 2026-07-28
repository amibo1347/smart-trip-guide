package com.travel.planner.account.service;

import com.travel.planner.account.dto.MyOverview;
import com.travel.planner.account.dto.MyOverview.TripSummary;
import com.travel.planner.account.dto.MyOverview.VisitedPlace;
import com.travel.planner.planning.entity.Place;
import com.travel.planner.planning.entity.Plan;
import com.travel.planner.planning.entity.PlanDay;
import com.travel.planner.planning.entity.PlanItem;
import com.travel.planner.planning.repository.PlanRepository;
import com.travel.planner.tracking.entity.TripMoment;
import com.travel.planner.tracking.repository.TripMomentRepository;
import com.travel.planner.trip.entity.Trip;
import com.travel.planner.trip.repository.TripRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마이페이지 개요 — 여러 여행에 흩어진 데이터를 모아 '누적 방문 지도'와 '여행 요약 카드'를 만든다.
 * 새로 수집하는 게 아니라 기존 데이터(여행·일정·기록) 집계.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserStatsService {

    private final TripRepository tripRepository;
    private final PlanRepository planRepository;
    private final TripMomentRepository momentRepository;

    public MyOverview overview(Long userId) {
        List<Trip> trips = tripRepository.findByUserIdOrderByStartDateDesc(userId);
        LocalDate today = LocalDate.now();

        List<VisitedPlace> places = new ArrayList<>();
        List<TripSummary> cards = new ArrayList<>();

        for (Trip trip : trips) {
            // 이 여행의 방문 장소(좌표 있는 것) — 누적 지도 + placeCount
            int placeCount = 0;
            Plan plan = planRepository.findTopByTripIdOrderByVersionDesc(trip.getId()).orElse(null);
            if (plan != null) {
                for (PlanDay day : plan.getDays()) {
                    for (PlanItem item : day.getItems()) {
                        Place p = item.getPlace();
                        if (p != null && p.getLatitude() != null && p.getLongitude() != null) {
                            places.add(new VisitedPlace(p.getName(), p.getLatitude(), p.getLongitude(), trip.getTitle()));
                            placeCount++;
                        }
                    }
                }
            }

            // 기록(moment): 사진 수 + 실제 지출
            int photoCount = 0;
            String momentPhoto = null;
            for (TripMoment m : momentRepository.findByTripIdOrderByRecordedAtDesc(trip.getId())) {
                if (m.getPhotoUrl() != null) {
                    photoCount++;
                    if (momentPhoto == null) {
                        momentPhoto = m.getPhotoUrl();
                    }
                }
            }
            // 표지: 목적지 대표 사진 우선, 없으면 내가 찍은 기록 사진
            String cover = trip.getDestinationPhoto() != null ? trip.getDestinationPhoto() : momentPhoto;
            BigDecimal spent = momentRepository.sumAmountByTripId(trip.getId());

            long dayCount = (trip.getStartDate() != null && trip.getEndDate() != null)
                    ? ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()) + 1 : 0;

            cards.add(new TripSummary(
                    trip.getId(), trip.getTitle(), trip.getDestinationName(),
                    trip.getStartDate(), trip.getEndDate(), phaseOf(trip, today),
                    dayCount, placeCount, photoCount,
                    spent == null ? BigDecimal.ZERO : spent, cover));
        }

        return new MyOverview(places, cards);
    }

    private static String phaseOf(Trip trip, LocalDate today) {
        if (trip.getEndDate() != null && trip.getEndDate().isBefore(today)) {
            return "PAST";
        }
        if (trip.getStartDate() != null && trip.getStartDate().isAfter(today)) {
            return "UPCOMING";
        }
        return "ONGOING";
    }
}
