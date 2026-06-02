package com.travel.planner.account.dto;

import com.travel.planner.account.entity.AuthProvider;
import com.travel.planner.account.entity.User;
import java.time.LocalDateTime;

/**
 * 사용자 응답 (password_hash 등 민감정보 제외).
 */
public record UserResponse(
        Long id,
        String email,
        String nickname,
        AuthProvider provider,
        LocalDateTime createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getNickname(),
                user.getProvider(), user.getCreatedAt());
    }
}
