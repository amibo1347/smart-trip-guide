package com.travel.planner.checklist.service;

import com.travel.planner.checklist.dto.ChecklistDtos.ChecklistCreateRequest;
import com.travel.planner.checklist.dto.ChecklistDtos.ChecklistItemResponse;
import com.travel.planner.checklist.dto.ChecklistDtos.ChecklistUpdateRequest;
import com.travel.planner.checklist.entity.ChecklistCategory;
import com.travel.planner.checklist.entity.ChecklistItem;
import com.travel.planner.checklist.repository.ChecklistItemRepository;
import com.travel.planner.common.exception.NotFoundException;
import com.travel.planner.trip.service.TripService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 준비물 체크리스트. 여행 소유자만 편집하며, 공유 링크에서는 읽기 전용으로 노출된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChecklistService {

    private final ChecklistItemRepository repository;
    private final TripService tripService;

    /**
     * 기본 준비물 세트. 빈 리스트에서 시작하는 부담을 없애기 위한 한 번 클릭용.
     * 해외 전용 항목(여권·어댑터 등)은 overseas=true 일 때만 들어간다.
     */
    private static final Map<ChecklistCategory, List<String>> PRESET = Map.of(
            ChecklistCategory.DOCUMENT, List.of("신분증", "항공권/e-티켓", "숙소 예약확인서", "현금·카드"),
            ChecklistCategory.CLOTHES, List.of("상의", "하의", "속옷", "잠옷", "겉옷", "편한 신발"),
            ChecklistCategory.ELECTRONICS, List.of("휴대폰 충전기", "보조배터리", "이어폰"),
            ChecklistCategory.TOILETRIES, List.of("칫솔·치약", "클렌징", "선크림", "기초 화장품"),
            ChecklistCategory.MEDICINE, List.of("소화제", "진통제", "밴드"),
            ChecklistCategory.ETC, List.of("우산", "에코백")
    );

    private static final Map<ChecklistCategory, List<String>> PRESET_OVERSEAS = Map.of(
            ChecklistCategory.DOCUMENT, List.of("여권", "비자", "여행자보험 증서"),
            ChecklistCategory.ELECTRONICS, List.of("멀티 어댑터", "유심·로밍"),
            ChecklistCategory.MEDICINE, List.of("멀미약", "지사제")
    );

    public List<ChecklistItemResponse> list(Long tripId, Long userId) {
        tripService.getOwnedTrip(tripId, userId);
        return listByTrip(tripId);
    }

    /** 공유(읽기 전용) 화면용 — 소유권 검사 없이 조회. 호출측이 토큰으로 이미 인가를 마친 경우에만 사용. */
    public List<ChecklistItemResponse> listByTrip(Long tripId) {
        return repository.findByTripIdOrderByCategoryAscSortOrderAscIdAsc(tripId).stream()
                .map(ChecklistItemResponse::from).toList();
    }

    @Transactional
    public ChecklistItemResponse add(Long tripId, ChecklistCreateRequest req, Long userId) {
        tripService.getOwnedTrip(tripId, userId);
        ChecklistItem item = ChecklistItem.builder()
                .tripId(tripId)
                .title(req.title().trim())
                .category(req.category())
                .assignee(req.assignee())
                .sortOrder(nextSortOrder(tripId))
                .build();
        return ChecklistItemResponse.from(repository.save(item));
    }

    @Transactional
    public ChecklistItemResponse update(Long itemId, ChecklistUpdateRequest req, Long userId) {
        ChecklistItem item = getOwned(itemId, userId);
        item.update(req.title(), req.category(), req.assignee(), req.checked());
        return ChecklistItemResponse.from(item);
    }

    @Transactional
    public void delete(Long itemId, Long userId) {
        repository.delete(getOwned(itemId, userId));
    }

    /**
     * 기본 세트 채우기. 이미 있는 이름은 건너뛰므로 여러 번 눌러도 중복되지 않는다.
     *
     * @param overseas 해외 여행이면 여권·어댑터 등 해외 전용 항목까지 추가
     */
    @Transactional
    public List<ChecklistItemResponse> applyPreset(Long tripId, boolean overseas, Long userId) {
        tripService.getOwnedTrip(tripId, userId);

        Set<String> existing = repository.findByTripIdOrderByCategoryAscSortOrderAscIdAsc(tripId).stream()
                .map(i -> i.getTitle().trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        int order = nextSortOrder(tripId);
        for (ChecklistCategory category : ChecklistCategory.values()) {
            for (String title : presetTitles(category, overseas)) {
                if (!existing.add(title.toLowerCase(Locale.ROOT))) continue;
                repository.save(ChecklistItem.builder()
                        .tripId(tripId).title(title).category(category).sortOrder(order++).build());
            }
        }
        return listByTrip(tripId);
    }

    private static List<String> presetTitles(ChecklistCategory category, boolean overseas) {
        List<String> base = PRESET.getOrDefault(category, List.of());
        if (!overseas) return base;
        List<String> extra = PRESET_OVERSEAS.getOrDefault(category, List.of());
        // 해외 전용 항목(여권 등)을 앞에 둬 먼저 챙기도록.
        return java.util.stream.Stream.concat(extra.stream(), base.stream()).toList();
    }

    private int nextSortOrder(Long tripId) {
        return (int) repository.countByTripId(tripId);
    }

    private ChecklistItem getOwned(Long itemId, Long userId) {
        ChecklistItem item = repository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("준비물 항목을 찾을 수 없습니다: " + itemId));
        tripService.getOwnedTrip(item.getTripId(), userId); // 소유권 검사
        return item;
    }
}
