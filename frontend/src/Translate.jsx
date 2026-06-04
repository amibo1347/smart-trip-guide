import { useEffect, useRef, useState } from 'react'
import { api } from './api.js'
import { LANGS, useI18n } from './i18n/index.jsx'
import { copyToClipboard } from './utils/clipboard.js'

const SpeechRecognition = typeof window !== 'undefined'
  && (window.SpeechRecognition || window.webkitSpeechRecognition)

// Web Speech API 인식용 BCP-47 로케일(언어코드 → 기본 로케일).
const SPEECH_LOCALE = { ko: 'ko-KR', en: 'en-US', ja: 'ja-JP', zh: 'zh-CN' }

/** 화면 우측에 떠 있는 번역 버튼 + 모달. 어느 화면에서나 접근 가능(전역). */
export default function FloatingTranslate() {
  const { t } = useI18n()
  const [open, setOpen] = useState(false)
  return (
    <>
      <button className="fab-translate" onClick={() => setOpen(true)} title={t('번역')} aria-label={t('번역')}>
        🌐
      </button>
      {open && <TranslateModal onClose={() => setOpen(false)} />}
    </>
  )
}

function TranslateModal({ onClose }) {
  const { t, lang } = useI18n()
  const [mode, setMode] = useState('text')          // 'text' | 'image' | 'voice'
  const [target, setTarget] = useState(lang)         // 기본: 내 UI 언어로 번역
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [source, setSource] = useState('')           // 인식/추출된 원문
  const [result, setResult] = useState('')           // 번역 결과

  function resetOutput() { setError(''); setResult(''); setSource('') }

  async function runText(text) {
    if (!text.trim()) return
    resetOutput(); setBusy(true)
    try {
      const r = await api.translateText(text, target)
      setResult(r.translatedText)
    } catch (e) { setError(e.message) } finally { setBusy(false) }
  }

  async function runImage(file) {
    if (!file) return
    resetOutput(); setBusy(true)
    try {
      const r = await api.translateImage(file, target)
      if (r.sourceText) setSource(r.sourceText)
      setResult(r.translatedText)
    } catch (e) { setError(e.message) } finally { setBusy(false) }
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal tr-modal" onClick={(e) => e.stopPropagation()}>
        <div className="tr-head">
          <h3>🌐 {t('번역 도우미')}</h3>
          <button className="x" onClick={onClose} aria-label={t('닫기')}>✕</button>
        </div>

        {/* 대상 언어 */}
        <label className="tr-target">{t('번역할 언어')}
          <select value={target} onChange={(e) => setTarget(e.target.value)}>
            {LANGS.map((l) => <option key={l.code} value={l.code}>{t(l.key)}</option>)}
          </select>
        </label>

        {/* 입력 모드 */}
        <div className="tr-tabs">
          {[['text', `⌨ ${t('텍스트')}`], ['image', `🖼 ${t('이미지')}`], ['voice', `🎙 ${t('음성')}`]].map(([m, label]) => (
            <button key={m} className={`tr-tab ${mode === m ? 'active' : ''}`}
                    onClick={() => { setMode(m); resetOutput() }}>{label}</button>
          ))}
        </div>

        {mode === 'text' && <TextMode busy={busy} onTranslate={runText} t={t} />}
        {mode === 'image' && <ImageMode busy={busy} onTranslate={runImage} t={t} />}
        {mode === 'voice' && <VoiceMode busy={busy} target={target} onTranslate={runText} t={t} />}

        {error && <p className="tr-error">⚠ {error}</p>}
        {(source || result) && (
          <div className="tr-output">
            {source && (
              <div className="tr-block">
                <span className="tr-label">{t('인식된 원문')}</span>
                <p className="tr-text">{source}</p>
              </div>
            )}
            {result && (
              <div className="tr-block">
                <span className="tr-label">{t('번역 결과')}</span>
                <p className="tr-text strong">{result}</p>
                <CopyButton text={result} t={t} />
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

function TextMode({ busy, onTranslate, t }) {
  const [text, setText] = useState('')
  return (
    <div className="tr-pane">
      <textarea rows={4} placeholder={t('번역할 내용을 입력하세요')} value={text}
                onChange={(e) => setText(e.target.value)} />
      <button disabled={busy || !text.trim()} onClick={() => onTranslate(text)}>
        {busy ? t('번역 중...') : t('번역하기')}
      </button>
    </div>
  )
}

function ImageMode({ busy, onTranslate, t }) {
  const [preview, setPreview] = useState(null)
  useEffect(() => () => { if (preview) URL.revokeObjectURL(preview) }, [preview])
  function pick(e) {
    const f = e.target.files?.[0] || null
    setPreview(f ? URL.createObjectURL(f) : null)
    if (f) onTranslate(f)
  }
  return (
    <div className="tr-pane">
      <label className="tr-filepick">
        📷 {t('사진 촬영·선택')}
        <input type="file" accept="image/*" capture="environment" hidden disabled={busy} onChange={pick} />
      </label>
      {preview && <img className="tr-preview" src={preview} alt="" />}
      {busy && <p className="muted small-text">{t('번역 중...')}</p>}
    </div>
  )
}

function VoiceMode({ busy, target, onTranslate, t }) {
  const { lang } = useI18n()
  const [speechLang, setSpeechLang] = useState(lang) // 말하는 언어(인식 정확도용)
  const [listening, setListening] = useState(false)
  const [transcript, setTranscript] = useState('')
  const recRef = useRef(null)

  useEffect(() => () => { try { recRef.current?.abort() } catch { /* noop */ } }, [])

  if (!SpeechRecognition) {
    return <p className="muted small-text tr-pane">{t('이 브라우저는 음성 인식을 지원하지 않아요. 텍스트나 이미지를 사용해 주세요.')}</p>
  }

  function start() {
    setTranscript('')
    const rec = new SpeechRecognition()
    rec.lang = SPEECH_LOCALE[speechLang] || 'en-US'
    rec.interimResults = true
    rec.continuous = false
    rec.onresult = (ev) => {
      let txt = ''
      for (let i = 0; i < ev.results.length; i++) txt += ev.results[i][0].transcript
      setTranscript(txt)
      if (ev.results[ev.results.length - 1].isFinal) onTranslate(txt)
    }
    rec.onerror = () => setListening(false)
    rec.onend = () => setListening(false)
    recRef.current = rec
    setListening(true)
    rec.start()
  }
  function stop() { try { recRef.current?.stop() } catch { /* noop */ } setListening(false) }

  return (
    <div className="tr-pane">
      <label className="tr-target">{t('말하는 언어')}
        <select value={speechLang} onChange={(e) => setSpeechLang(e.target.value)} disabled={listening}>
          {LANGS.map((l) => <option key={l.code} value={l.code}>{t(l.key)}</option>)}
        </select>
      </label>
      <button className={listening ? 'tr-mic on' : 'tr-mic'} disabled={busy}
              onClick={listening ? stop : start}>
        {listening ? `⏹ ${t('중지')}` : `🎙 ${t('말하기')}`}
      </button>
      {listening && <p className="muted small-text">{t('듣는 중... 말씀하세요')}</p>}
      {transcript && <p className="tr-text">{transcript}</p>}
    </div>
  )
}

function CopyButton({ text, t }) {
  const [done, setDone] = useState(false)
  async function copy() {
    if (await copyToClipboard(text)) { setDone(true); setTimeout(() => setDone(false), 1500) }
  }
  return <button className="tr-copy" onClick={copy}>{done ? `✓ ${t('복사됨')}` : `⧉ ${t('복사')}`}</button>
}
