package com.travel.planner.translation.controller;

import com.travel.planner.translation.dto.TranslationDtos.TextTranslateRequest;
import com.travel.planner.translation.dto.TranslationDtos.TranslationResponse;
import com.travel.planner.translation.service.TranslationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 번역 도우미 API(공개 — 사용자 데이터 없음, 비로그인/공유뷰에서도 사용).
 * 텍스트/이미지 두 경로. 음성은 클라이언트(Web Speech API)에서 텍스트로 바꿔 텍스트 경로로 보낸다.
 */
@RestController
@RequiredArgsConstructor
public class TranslationController {

    private final TranslationService translationService;

    /** 텍스트 번역. */
    @PostMapping("/api/translate/text")
    public TranslationResponse translateText(@RequestBody TextTranslateRequest req) {
        return translationService.translateText(req.text(), req.target());
    }

    /** 이미지 번역(OCR 후 번역). multipart: image=이미지 파트, target=언어코드. */
    @PostMapping("/api/translate/image")
    public TranslationResponse translateImage(@RequestParam("image") MultipartFile image,
                                              @RequestParam("target") String target) {
        return translationService.translateImage(image, target);
    }
}
