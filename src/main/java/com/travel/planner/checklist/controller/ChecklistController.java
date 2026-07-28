package com.travel.planner.checklist.controller;

import com.travel.planner.account.security.CurrentUser;
import com.travel.planner.checklist.dto.ChecklistDtos.ChecklistCreateRequest;
import com.travel.planner.checklist.dto.ChecklistDtos.ChecklistItemResponse;
import com.travel.planner.checklist.dto.ChecklistDtos.ChecklistUpdateRequest;
import com.travel.planner.checklist.service.ChecklistService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 준비물 체크리스트 API. 모두 여행 소유자 전용(공유 링크 쪽은 /api/shared 에서 읽기 전용 제공). */
@RestController
@RequiredArgsConstructor
public class ChecklistController {

    private final ChecklistService checklistService;
    private final CurrentUser currentUser;

    @GetMapping("/api/trips/{tripId}/checklist")
    public List<ChecklistItemResponse> list(@PathVariable Long tripId, Authentication auth) {
        return checklistService.list(tripId, currentUser.requireId(auth));
    }

    @PostMapping("/api/trips/{tripId}/checklist")
    @ResponseStatus(HttpStatus.CREATED)
    public ChecklistItemResponse add(@PathVariable Long tripId,
                                     @Valid @RequestBody ChecklistCreateRequest request,
                                     Authentication auth) {
        return checklistService.add(tripId, request, currentUser.requireId(auth));
    }

    /** 기본 준비물 세트 채우기(이미 있는 이름은 건너뜀). */
    @PostMapping("/api/trips/{tripId}/checklist/preset")
    public List<ChecklistItemResponse> preset(@PathVariable Long tripId,
                                              @RequestParam(defaultValue = "false") boolean overseas,
                                              Authentication auth) {
        return checklistService.applyPreset(tripId, overseas, currentUser.requireId(auth));
    }

    /** 체크 토글·이름/담당자 수정(보낸 필드만 반영). */
    @PatchMapping("/api/checklist-items/{itemId}")
    public ChecklistItemResponse update(@PathVariable Long itemId,
                                        @Valid @RequestBody ChecklistUpdateRequest request,
                                        Authentication auth) {
        return checklistService.update(itemId, request, currentUser.requireId(auth));
    }

    @DeleteMapping("/api/checklist-items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long itemId, Authentication auth) {
        checklistService.delete(itemId, currentUser.requireId(auth));
    }
}
