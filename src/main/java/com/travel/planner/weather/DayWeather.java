package com.travel.planner.weather;

import java.time.LocalDate;

/**
 * 하루치 날씨. code 는 WMO weather code(프런트에서 이모지·설명으로 변환).
 *
 * @param code        WMO 날씨 코드(0=맑음, 3=흐림, 61=비, 71=눈, 95=뇌우 …)
 * @param tempMax     최고기온(℃)
 * @param tempMin     최저기온(℃)
 * @param precipProb  강수확률(%) — 없으면 null
 */
public record DayWeather(
        LocalDate date,
        int code,
        Double tempMax,
        Double tempMin,
        Integer precipProb) {
}
