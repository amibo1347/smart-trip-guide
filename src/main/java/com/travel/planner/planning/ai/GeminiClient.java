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
    @SuppressWarnings("unchecked")
    public String generateJson(String prompt, Map<String, Object> responseSchema) {
        if (!isAvailable()) {
            throw new AiException("AI 키가 설정되지 않았습니다. .env 의 GEMINI_API_KEY 를 설정한 뒤 다시 시도하세요.");
        }

        Map<String, Object> genCfg = new HashMap<>();
        genCfg.put("temperature", 0.8);
        genCfg.put("responseMimeType", "application/json");
        if (responseSchema != null) {
            genCfg.put("responseSchema", responseSchema);
        }
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
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
            throw new AiException("Gemini 호출 실패 (HTTP " + code + "). 잠시 후 다시 시도하거나 키/모델을 확인하세요.", lastError);
        }

        // candidates[0].content.parts[0].text
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) resp.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new AiException("Gemini 응답이 비어 있습니다.");
        }
        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        if (content == null) {
            throw new AiException("Gemini 응답에 content가 없습니다(안전필터 등).");
        }
        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
        StringBuilder sb = new StringBuilder();
        if (parts != null) {
            for (Map<String, Object> p : parts) {
                Object t = p.get("text");
                if (t != null) {
                    sb.append(t);
                }
            }
        }
        if (sb.length() == 0) {
            throw new AiException("Gemini가 일정 JSON을 반환하지 않았습니다.");
        }
        return sb.toString();
    }
}
