package com.travel.planner.account.controller;

import com.travel.planner.account.dto.MyOverview;
import com.travel.planner.account.dto.ProfileDtos.PasswordChangeRequest;
import com.travel.planner.account.dto.ProfileDtos.ProfileUpdateRequest;
import com.travel.planner.account.dto.UserResponse;
import com.travel.planner.account.security.CurrentUser;
import com.travel.planner.account.service.UserService;
import com.travel.planner.account.service.UserStatsService;
import com.travel.planner.tracking.service.PhotoStorageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 마이페이지 — 프로필 수정 / 비밀번호 변경 / 여행 통계 / 회원 탈퇴.
 * 모두 현재 로그인 사용자(세션) 기준으로만 동작한다(요청의 id 를 신뢰하지 않음).
 */
@RestController
@RequiredArgsConstructor
public class MyPageController {

    private final UserService userService;
    private final UserStatsService userStatsService;
    private final PhotoStorageService photoStorage;
    private final CurrentUser currentUser;

    /** 프로필 수정(닉네임·기본 출발지·아바타 프리셋). */
    @PatchMapping("/api/users/me")
    public UserResponse updateProfile(@Valid @RequestBody ProfileUpdateRequest req, Authentication auth) {
        Long userId = currentUser.requireId(auth);
        return userService.updateProfile(userId, req.nickname(), req.defaultOrigin(), req.avatar());
    }

    /** 아바타 사진 업로드(직접 올린 이미지). */
    @PostMapping(path = "/api/users/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserResponse uploadAvatar(@RequestPart("file") MultipartFile file, Authentication auth) {
        String url = photoStorage.store(file);
        if (url == null) {
            throw new IllegalArgumentException("이미지 파일이 없습니다.");
        }
        return userService.changeAvatar(currentUser.requireId(auth), url);
    }

    /** 비밀번호 변경(로컬 계정). */
    @PatchMapping("/api/users/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody PasswordChangeRequest req, Authentication auth) {
        userService.changePassword(currentUser.requireId(auth), req.currentPassword(), req.newPassword());
    }

    /** 마이페이지 개요 — 누적 방문 지도 + 여행 요약 카드. */
    @GetMapping("/api/users/me/overview")
    public MyOverview overview(Authentication auth) {
        return userStatsService.overview(currentUser.requireId(auth));
    }

    /** 회원 탈퇴 — 여행·기록까지 삭제하고 세션을 종료한다. */
    @DeleteMapping("/api/users/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(Authentication auth, HttpServletRequest request) {
        userService.deleteAccount(currentUser.requireId(auth));
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
