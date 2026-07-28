package com.travel.planner.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 마이페이지 프로필/비밀번호 관련 요청 DTO 묶음. */
public final class ProfileDtos {

    private ProfileDtos() {
    }

    /** 프로필 수정(보낸 필드만 반영). avatar 는 "preset:xxx" 프리셋 선택. */
    public record ProfileUpdateRequest(
            @Size(max = 50) String nickname,
            @Size(max = 50) String defaultOrigin,
            @Size(max = 500) String avatar) {
    }

    /** 비밀번호 변경(로컬 계정). */
    public record PasswordChangeRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 64) String newPassword) {
    }
}
