package com.travel.planner.translation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.planner.common.exception.AiException;
import com.travel.planner.common.util.Texts;
import com.travel.planner.planning.ai.GeminiClient;
import com.travel.planner.translation.dto.TranslationDtos.TranslationResponse;
import java.util.Base64;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 다국어 번역 서비스(설계: 여행 중 현장 번역 도우미).
 * 엔진은 기존 Gemini 키 재사용 — 텍스트 번역 + 이미지 OCR 번역 모두 한 모델로 처리(추가 키 불필요).
 * 음성 인식은 브라우저 Web Speech API 로 클라이언트에서 텍스트화한 뒤 텍스트 번역 경로로 들어온다.
 */
@Service
@RequiredArgsConstructor
public class TranslationService {

    /** 지원 언어코드 → 모델 프롬프트용 언어명. */
    private static final Map<String, String> LANG_NAMES = Map.of(
            "ko", "Korean (한국어)",
            "en", "English",
            "ja", "Japanese (日本語)",
            "zh", "Simplified Chinese (简体中文)");

    private static final int MAX_TEXT = 5000;

    private final GeminiClient gemini;
    private final ObjectMapper objectMapper;

    /** 텍스트 번역: 원본 언어는 자동 감지, 대상 언어로만 출력. */
    public TranslationResponse translateText(String text, String target) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("번역할 텍스트가 비어 있습니다.");
        }
        if (text.length() > MAX_TEXT) {
            throw new IllegalArgumentException("번역 텍스트가 너무 깁니다(최대 " + MAX_TEXT + "자).");
        }
        String langName = langName(target);
        String prompt = "Translate the text below into " + langName + ". "
                + "Auto-detect the source language. If it is already in " + langName + ", return it unchanged. "
                + "Output ONLY the translation — no quotes, no notes, no explanations.\n\n"
                + "Text:\n\"\"\"\n" + text + "\n\"\"\"";
        String out = gemini.generateText(prompt).trim();
        return new TranslationResponse(null, stripQuotes(out));
    }

    /** 이미지 번역: 이미지에서 글자를 추출(OCR)한 뒤 대상 언어로 번역. 원문/번역을 함께 반환. */
    public TranslationResponse translateImage(MultipartFile image, String target) {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("이미지가 비어 있습니다.");
        }
        String mime = image.getContentType();
        if (mime == null || !mime.startsWith("image/")) {
            throw new IllegalArgumentException("이미지 파일만 번역할 수 있습니다.");
        }
        String langName = langName(target);
        String base64;
        try {
            base64 = Base64.getEncoder().encodeToString(image.getBytes());
        } catch (Exception e) {
            throw new IllegalArgumentException("이미지를 읽을 수 없습니다.");
        }
        String prompt = "You are a translation assistant. "
                + "1) Extract ALL readable text from the image (keep line breaks). "
                + "2) Translate the extracted text into " + langName + ". "
                + "Respond with ONLY a raw JSON object of the form "
                + "{\"original\":\"<extracted text>\",\"translated\":\"<translation>\"}. "
                + "No markdown, no code fences. If there is no text, use empty strings.";
        String raw = gemini.generateTextFromImage(prompt, base64, mime);
        return parseImageResult(raw);
    }

    /** 모델이 돌려준 (가능하면 JSON) 응답에서 원문/번역을 뽑는다. JSON 파싱 실패 시 전체를 번역문으로 폴백. */
    private TranslationResponse parseImageResult(String raw) {
        String cleaned = stripCodeFence(raw).trim();
        try {
            JsonNode node = objectMapper.readTree(cleaned);
            String original = textOf(node.get("original"));
            String translated = textOf(node.get("translated"));
            if (translated != null && !translated.isBlank()) {
                return new TranslationResponse(Texts.trimToNull(original), translated.trim());
            }
        } catch (Exception ignored) {
            // JSON 이 아니면 아래에서 전체를 번역문으로 처리
        }
        if (cleaned.isBlank()) {
            throw new AiException("이미지에서 번역할 텍스트를 찾지 못했습니다.");
        }
        return new TranslationResponse(null, cleaned);
    }

    private static String langName(String target) {
        String name = LANG_NAMES.get(target);
        if (name == null) {
            throw new IllegalArgumentException("지원하지 않는 언어입니다: " + target);
        }
        return name;
    }

    private static String textOf(JsonNode n) {
        return (n == null || n.isNull()) ? null : n.asText();
    }

    /** ```json ... ``` 같은 코드펜스 제거. */
    private static String stripCodeFence(String s) {
        String t = s.trim();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            if (nl >= 0) {
                t = t.substring(nl + 1);
            }
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3);
            }
        }
        return t;
    }

    /** 앞뒤 따옴표 한 겹 제거(모델이 가끔 결과를 따옴표로 감싸는 경우). */
    private static String stripQuotes(String s) {
        if (s.length() >= 2
                && (s.startsWith("\"") && s.endsWith("\"") || s.startsWith("'") && s.endsWith("'"))) {
            return s.substring(1, s.length() - 1).trim();
        }
        return s;
    }
}
