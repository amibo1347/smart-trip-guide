package com.travel.planner.account.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 로컬(이메일/비밀번호) 로그인 요청.
 */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {
}
