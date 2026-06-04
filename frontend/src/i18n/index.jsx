import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { translations } from './translations.js'

/**
 * 지원 UI 언어. code 는 백엔드 번역 대상 코드와 동일(ko/en/ja/zh).
 * - label: 각 언어 고유 표기(헤더 선택용 — 어느 언어에서나 자기 언어를 찾도록).
 * - key: 현재 UI 언어로 번역해 보여줄 이름 키(번역 도우미의 언어 목록용). t(key) 로 표시.
 */
export const LANGS = [
  { code: 'ko', label: '한국어', key: '한국어' },
  { code: 'en', label: 'English', key: '영어' },
  { code: 'ja', label: '日本語', key: '일본어' },
  { code: 'zh', label: '中文', key: '중국어' },
]

const STORAGE_KEY = 'ui-lang'
const I18nContext = createContext(null)

/**
 * 앱 전체 UI 다국어. 한국어 원문을 키로 사용 — ko 는 키 그대로, 그 외 언어는 translations 에서 찾고,
 * 누락 키는 한국어로 폴백한다(점진적 번역 보강이 안전). {var} 보간 지원.
 */
export function I18nProvider({ children }) {
  const [lang, setLangState] = useState(() => localStorage.getItem(STORAGE_KEY) || 'ko')

  useEffect(() => { document.documentElement.lang = lang }, [lang])

  const setLang = useCallback((l) => {
    setLangState(l)
    localStorage.setItem(STORAGE_KEY, l)
  }, [])

  const t = useCallback((ko, vars) => {
    let s = lang === 'ko' ? ko : (translations[lang]?.[ko] ?? ko)
    if (vars) for (const k of Object.keys(vars)) s = s.replaceAll(`{${k}}`, String(vars[k]))
    return s
  }, [lang])

  return <I18nContext.Provider value={{ lang, setLang, t }}>{children}</I18nContext.Provider>
}

export function useI18n() {
  const ctx = useContext(I18nContext)
  if (!ctx) throw new Error('useI18n must be used within I18nProvider')
  return ctx
}
