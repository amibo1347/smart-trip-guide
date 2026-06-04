// 항목/예약 유형 라벨 (이전엔 Itinerary·Review·SharedView·Bookings 에 중복).
// 값은 [이모지, i18n키]. 이름키는 t()로 현재 언어 표시.

export const PLAN_TYPE = {
  SPOT: ['🏞', '명소'],
  MEAL: ['🍽', '식사'],
  MOVE: ['🚌', '이동'],
  STAY: ['🏨', '숙박'],
  ACTIVITY: ['🎯', '액티비티'],
}

export const BOOKING_TYPE = {
  FLIGHT: ['✈️', '항공'],
  HOTEL: ['🏨', '숙소'],
}

/** "이모지 이름" (현재 언어). 미지정 코드는 코드 그대로. */
export function typeLabel(t, map, code) {
  return map[code] ? `${map[code][0]} ${t(map[code][1])}` : code
}

/** 이모지 없이 이름만(현재 언어). */
export function typeName(t, map, code) {
  return map[code] ? t(map[code][1]) : code
}
