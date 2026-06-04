package com.travel.planner.planning.ai;

import com.travel.planner.common.exception.AiException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Google Gemini REST 호출 (team 프로젝트 방식 참고).
 * responseSchema 로 구조화 JSON 출력을 강제한다(설계 4.C: 출력 포맷 안정화).
 * 키 발급: https://aistudio.google.com/app/apikey
 */
@Component
public class GeminiClient {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_REF =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient rest;
    private final String apiKey;
    private final String model;

    public GeminiClient(@Value("${gemini.api-key:}") String apiKey,
                        @Value("${gemini.model:gemini-2.5-flash-lite}") String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.rest = RestClient.builder().baseUrl(BASE_URL).build();
    }

    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String model() {
        return model;
    }

    /**
     * 프롬프트 + 응답 스키마로 구조화 JSON 생성. 반환 = 모델이 만든 JSON 문자열.
     */
    public String generateJson(String prompt, Map<String, Object> responseSchema) {
        Map<String, Object> genCfg = new HashMap<>();
        genCfg.put("temperature", 0.8);
        genCfg.put("responseMimeType", "application/json");
        if (responseSchema != null) {
            genCfg.put("responseSchema", responseSchema);
        }
        String text = call(List.of(textPart(prompt)), genCfg);
        if (text.isBlank()) {
            throw new AiException("Gemini가 일정 JSON을 반환하지 않았습니다.");
        }
        return text;
    }

    /** 평문(번역 등) 텍스트 생성. 번역은 보수적으로(temperature 낮게). */
    public String generateText(String prompt) {
        String text = call(List.of(textPart(prompt)), lowTempCfg());
        if (text.isBlank()) {
            throw new AiException("Gemini가 결과를 반환하지 않았습니다.");
        }
        return text;
    }

    /** 이미지 + 프롬프트(멀티모달) 평문 생성. OCR·이미지 번역에 사용. base64 = 원본 바이트의 Base64. */
    public String generateTextFromImage(String prompt, String base64, String mimeType) {
        String text = call(List.of(imagePart(base64, mimeType), textPart(prompt)), lowTempCfg());
        if (text.isBlank()) {
            throw new AiException("Gemini가 이미지에서 텍스트를 추출하지 못했습니다.");
        }
        return text;
    }

    private static Map<String, Object> lowTempCfg() {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("temperature", 0.2);
        return cfg;
    }

    private static Map<String, Object> textPart(String text) {
        Map<String, Object> m = new HashMap<>();
        m.put("text", text);
        return m;
    }

    private static Map<String, Object> imagePart(String base64, String mimeType) {
        Map<String, Object> inline = new HashMap<>();
        inline.put("mime_type", mimeType);
        inline.put("data", base64);
        Map<String, Object> m = new HashMap<>();
        m.put("inline_data", inline);
        return m;
    }

    /** 공통 호출: parts + generationConfig 로 1회 생성하고 응답 텍스트를 이어붙여 반환(빈 문자열일 수 있음). */
    @SuppressWarnings("unchecked")
    private String call(List<Map<String, Object>> parts, Map<String, Object> genCfg) {
        if (!isAvailable()) {
            throw new AiException("AI 키가 설정되지 않았습니다. .env 의 GEMINI_API_KEY 를 설정한 뒤 다시 시도하세요.");
        }
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", parts)),
                "generationConfig", genCfg);
        String url = "/models/" + model + ":generateContent?key=" + apiKey;

        // 503/504/429 일시 장애 시 최대 2회 재시도
        Map<String, Object> resp = null;
        RestClientResponseException lastError = null;
        long[] backoffMs = {0, 300, 1000};
        for (long wait : backoffMs) {
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
            try {
                resp = rest.post().uri(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(MAP_REF);
                break;
            } catch (RestClientResponseException e) {
                lastError = e;
                int status = e.getStatusCode().value();
                if (status != 503 && status != 504 && status != 429) {
                    break;
                }
            }
        }
        if (resp == null) {
            int code = lastError != null ? lastError.getStatusCode().value() : -1;
            String detail = "";
            if (lastError != null) {
                String b = lastError.getResponseBodyAsString();
                if (b != null && !b.isBlank()) {
                    detail = " — " + b.substring(0, Math.min(500, b.length()));
                }
            }
            throw new AiException("Gemini 호출 실패 (HTTP " + code + ")" + detail, lastError);
        }

        // candidates[0].content.parts[*].text
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) resp.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new AiException("Gemini 응답이 비어 있습니다.");
        }
        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        if (content == null) {
            throw new AiException("Gemini 응답에 content가 없습니다(안전필터 등).");
        }
        List<Map<String, Object>> respParts = (List<Map<String, Object>>) content.get("parts");
        StringBuilder sb = new StringBuilder();
        if (respParts != null) {
            for (Map<String, Object> p : respParts) {
                Object t = p.get("text");
                if (t != null) {
                    sb.append(t);
                }
            }
        }
        return sb.toString();
    }
}
