import { useEffect, useState } from 'react'
import { api } from './api.js'
import { useI18n } from './i18n/index.jsx'
import { CHECKLIST_CATEGORY, categoryOrder } from './constants/labels.js'

/**
 * 준비물 체크리스트. 분류별로 묶어 보여주고 담당자를 적어 여러 명이 나눠 챙길 수 있다.
 * 비어 있으면 기본 세트(국내/해외)를 한 번에 채울 수 있게 안내한다.
 */
export default function Checklist({ trip, onError }) {
  const { t } = useI18n()
  const [items, setItems] = useState(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let alive = true
    api.listChecklist(trip.id)
      .then((r) => { if (alive) setItems(r) })
      .catch((e) => onError(e.message))
    return () => { alive = false }
  }, [trip.id]) // eslint-disable-line react-hooks/exhaustive-deps

  async function toggle(item) {
    // 낙관적 갱신 — 체크는 즉각 반응해야 자연스럽다. 실패하면 되돌린다.
    setItems((list) => list.map((i) => (i.id === item.id ? { ...i, checked: !i.checked } : i)))
    try {
      await api.updateChecklistItem(item.id, { checked: !item.checked })
    } catch (e) {
      setItems((list) => list.map((i) => (i.id === item.id ? { ...i, checked: item.checked } : i)))
      onError(e.message)
    }
  }

  async function remove(item) {
    try { await api.deleteChecklistItem(item.id); setItems((l) => l.filter((i) => i.id !== item.id)) }
    catch (e) { onError(e.message) }
  }

  async function assign(item) {
    const who = window.prompt(t('담당자 이름 (비우면 해제)'), item.assignee ?? '')
    if (who === null) return
    try { setItems((l) => l.map((i) => (i.id === item.id ? { ...i, assignee: who.trim() || null } : i)))
      await api.updateChecklistItem(item.id, { assignee: who.trim() })
    } catch (e) { onError(e.message) }
  }

  async function add(title, category) {
    setBusy(true)
    try {
      const created = await api.addChecklistItem(trip.id, { title, category })
      setItems((l) => [...l, created])
    } catch (e) { onError(e.message) } finally { setBusy(false) }
  }

  async function preset(overseas) {
    setBusy(true)
    try { setItems(await api.presetChecklist(trip.id, overseas)) }
    catch (e) { onError(e.message) } finally { setBusy(false) }
  }

  if (!items) return <section className="card"><p className="muted">{t('불러오는 중...')}</p></section>

  const done = items.filter((i) => i.checked).length
  const pct = items.length === 0 ? 0 : Math.round((done / items.length) * 100)

  return (
    <section className="card">
      <h2>🎒 {t('준비물')}</h2>

      {items.length > 0 && (
        <div className="chk-progress">
          <div className="chk-ring"
               style={{ background: `conic-gradient(var(--brand) ${pct * 3.6}deg, #e9ece7 0deg)` }}>
            <span>{pct}%</span>
          </div>
          <div className="chk-sum">
            <b>{t('{done} / {total}개 챙김', { done, total: items.length })}</b>
            <p>{done === items.length
              ? t('다 챙겼어요! 즐거운 여행 되세요 ✈️')
              : t('{n}개 남았어요.', { n: items.length - done })}</p>
          </div>
        </div>
      )}

      {items.length === 0 ? (
        <div className="chk-empty">
          <p>{t('아직 준비물이 없어요. 기본 세트로 시작해 보세요.')}</p>
          <div className="chk-preset">
            <button className="small" onClick={() => preset(false)} disabled={busy}>🏠 {t('국내 여행 세트')}</button>
            <button className="small" onClick={() => preset(true)} disabled={busy}>🌏 {t('해외 여행 세트')}</button>
          </div>
        </div>
      ) : (
        categoryOrder(items).map(([category, list]) => (
          <div className="chk-cat" key={category}>
            <div className="chk-cat-head">
              <span>{CHECKLIST_CATEGORY[category]?.emoji ?? '📦'}</span>
              <span>{t(CHECKLIST_CATEGORY[category]?.label ?? '기타')}</span>
              <span className="n">{list.filter((i) => i.checked).length}/{list.length}</span>
            </div>
            <ul className="chk-list">
              {list.map((item) => (
                <li className={item.checked ? 'chk-item done' : 'chk-item'} key={item.id}>
                  <button className="chk-box" onClick={() => toggle(item)}
                          aria-pressed={item.checked} aria-label={item.title}>✓</button>
                  <span className="chk-title">{item.title}</span>
                  {item.assignee && <span className="chk-who">{item.assignee}</span>}
                  <button className="edit" onClick={() => assign(item)} title={t('담당자 지정')}>👤</button>
                  <button className="del" onClick={() => remove(item)} title={t('삭제')}>✕</button>
                </li>
              ))}
            </ul>
          </div>
        ))
      )}

      <AddForm onAdd={add} busy={busy} />
    </section>
  )
}

function AddForm({ onAdd, busy }) {
  const { t } = useI18n()
  const [title, setTitle] = useState('')
  const [category, setCategory] = useState('ETC')

  function submit(e) {
    e.preventDefault()
    const v = title.trim()
    if (!v) return
    onAdd(v, category)
    setTitle('')
  }

  return (
    <form className="chk-add" onSubmit={submit}>
      <input placeholder={t('준비물 추가 (예: 여권)')} value={title} onChange={(e) => setTitle(e.target.value)} />
      <select value={category} onChange={(e) => setCategory(e.target.value)} aria-label={t('분류')}>
        {Object.entries(CHECKLIST_CATEGORY).map(([code, c]) => (
          <option key={code} value={code}>{c.emoji} {t(c.label)}</option>
        ))}
      </select>
      <button disabled={busy || !title.trim()}>{t('추가')}</button>
    </form>
  )
}
