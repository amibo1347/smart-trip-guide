package com.travel.planner.checklist.dto;

import com.travel.planner.checklist.entity.ChecklistCategory;
import com.travel.planner.checklist.entity.ChecklistItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 준비물 체크리스트 요청/응답 DTO 묶음. */
public final class ChecklistDtos {

    private ChecklistDtos() {
    }

    /** 항목 추가. category 가 없으면 ETC 로 들어간다. */
    public record ChecklistCreateRequest(
            @NotBlank @Size(max = 120) String title,
            ChecklistCategory category,
            @Size(max = 50) String assignee) {
    }

    /** 부분 수정 — 보낸 필드만 반영(체크 토글은 checked 만 보낸다). */
    public record ChecklistUpdateRequest(
            @Size(max = 120) String title,
            ChecklistCategory category,
            @Size(max = 50) String assignee,
            Boolean checked) {
    }

    public record ChecklistItemResponse(
            Long id, String title, ChecklistCategory category, String assignee, boolean checked, int sortOrder) {

        public static ChecklistItemResponse from(ChecklistItem item) {
            return new ChecklistItemResponse(item.getId(), item.getTitle(), item.getCategory(),
                    item.getAssignee(), item.isChecked(), item.getSortOrder());
        }
    }
}
