package com.travel.planner.feedback.controller;

import com.travel.planner.account.security.CurrentUser;
import com.travel.planner.feedback.dto.ActualUpsertRequest;
import com.travel.planner.feedback.dto.FeedbackUpsertRequest;
import com.travel.planner.feedback.dto.ReviewReportResponse;
import com.travel.planner.feedback.dto.ReviewResponse;
import com.travel.planner.feedback.dto.ReviewResponse.FeedbackView;
import com.travel.planner.feedback.service.ReviewReportService;
import com.travel.planner.feedback.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final ReviewReportService reviewReportService;
    private final CurrentUser currentUser;

    /** 여행 복기 집계(계획vs실제·예산·위치·회고). */
    @GetMapping("/api/trips/{tripId}/review")
    public ReviewResponse getReview(@PathVariable Long tripId, Authentication auth) {
        return reviewService.getReview(tripId, currentUser.requireId(auth));
    }

    /** AI 회고 리포트 생성(요청 시점 데이터로 생성, 미저장). lang=ko/en/ja/zh. */
    @PostMapping("/api/trips/{tripId}/review/report")
    public ReviewReportResponse report(@PathVariable Long tripId,
                                       @RequestParam(defaultValue = "ko") String lang,
                                       Authentication auth) {
        return reviewReportService.generate(tripId, currentUser.requireId(auth), lang);
    }

    /** 계획 항목의 실제 결과(방문/비용/만족도) 저장. */
    @PutMapping("/api/plan-items/{itemId}/actual")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void upsertActual(@PathVariable Long itemId,
                             @Valid @RequestBody ActualUpsertRequest request,
                             Authentication auth) {
        reviewService.upsertActual(itemId, request, currentUser.requireId(auth));
    }

    /** 여행 회고 저장. */
    @PutMapping("/api/trips/{tripId}/feedback")
    public FeedbackView upsertFeedback(@PathVariable Long tripId,
                                       @Valid @RequestBody FeedbackUpsertRequest request,
                                       Authentication auth) {
        return reviewService.upsertFeedback(tripId, request, currentUser.requireId(auth));
    }
}
