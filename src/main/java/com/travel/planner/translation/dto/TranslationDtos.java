package com.travel.planner.translation.dto;

/** 번역 요청/응답 DTO 묶음. */
public final class TranslationDtos {

    private TranslationDtos() {
    }

    /** 텍스트 번역 요청: 원문 + 대상 언어코드(ko/en/ja/zh). */
    public record TextTranslateRequest(String text, String target) {
    }

    /**
     * 번역 응답.
     * - sourceText: 인식/추출된 원문(이미지 OCR 시 채워짐, 텍스트 입력은 null).
     * - translatedText: 대상 언어 번역 결과.
     */
    public record TranslationResponse(String sourceText, String translatedText) {
    }
}
