import { useEffect, useState } from 'react'
import { api } from './api.js'
import VisitedMap from './VisitedMap.jsx'
import { useI18n } from './i18n/index.jsx'
import { avatarSrc, AVATAR_PRESETS, DEFAULT_AVATAR } from './utils/avatars.js'

const PROVIDER_LABEL = { LOCAL: '이메일', GOOGLE: 'Google', KAKAO: '카카오' }

/**
 * 마이페이지 — 프로필(아바타) + 내가 다녀온 곳(누적 지도) + 여행 기록(모음집 진입) + 로그아웃/탈퇴.
 * 여행 요약은 여기 나열하지 않고, '여행 기록'을 눌러 별도 모음집 화면에서 카드로 본다.
 */
export default function MyPage({ user, onUser, onOpenTrip, onLogout, onClose, onError }) {
  const { t } = useI18n()
  const [ov, setOv] = useState(null)
  const [view, setView] = useState('main') // 'main' | 'recap'

  useEffect(() => {
    let alive = true
    api.myOverview().then((r) => { if (alive) setOv(r) }).catch((e) => onError(e.message))
    return () => { alive = false }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  if (view === 'recap') {
    return <TripRecap trips={ov?.trips || []} onOpen={onOpenTrip} onBack={() => setView('main')} />
  }

  return (
    <div>
      <button className="link back" onClick={onClose}>← {t('내 여행으로')}</button>
      <ProfileCard user={user} onUser={onUser} onError={onError} />

      <section className="card">
        <h2>🗺 {t('내가 다녀온 곳')} {ov?.places?.length ? `(${ov.places.length})` : ''}</h2>
        {ov?.places?.length > 0
          ? <VisitedMap places={ov.places} />
          : <p className="muted small-text">{t('장소를 담은 여행이 쌓이면 여기에 발자취가 그려져요.')}</p>}
      </section>

      {/* 여행 기록 모음집 진입 (나열은 안쪽에서) */}
      <button className="card recap-entry" onClick={() => setView('recap')}>
        <div className="recap-entry-l">
          <b>🧳 {t('여행 기록')}</b>
          <span className="muted small-text">{t('다녀온 여행을 카드로 모아봐요')}</span>
        </div>
        <span className="recap-count">{ov?.trips?.length ?? 0}</span>
        <span className="recap-arrow">›</span>
      </button>

      <DangerCard onLogout={onLogout} onError={onError} />
    </div>
  )
}

/* ── 여행 기록 모음집 — 세로 카드 그리드 (상단 사진 · 하단 설명) ── */
function TripRecap({ trips, onOpen, onBack }) {
  const { t } = useI18n()
  return (
    <div>
      <button className="link back" onClick={onBack}>← {t('마이페이지')}</button>
      <section className="card">
        <h2>🧳 {t('여행 기록')} ({trips.length})</h2>
        {trips.length === 0 && <p className="muted small-text">{t('아직 여행이 없어요.')}</p>}
        <div className="recap-grid">
          {trips.map((tr) => <RecapCard key={tr.tripId} tr={tr} onOpen={onOpen} t={t} />)}
        </div>
      </section>
    </div>
  )
}

function RecapCard({ tr, onOpen, t }) {
  const phase = tr.phase === 'PAST' ? { cls: 'past', txt: t('다녀옴') }
    : tr.phase === 'ONGOING' ? { cls: 'ongoing', txt: t('여행 중') }
      : { cls: 'upcoming', txt: t('예정') }
  return (
    <button className="recap-card" onClick={() => onOpen(tr.tripId)}>
      <div className="recap-cover">
        {tr.coverPhotoUrl
          ? <img src={tr.coverPhotoUrl} alt="" loading="lazy" />
          : <span className="recap-ph">🧳</span>}
        <span className={`recap-phase ${phase.cls}`}>{phase.txt}</span>
      </div>
      <div className="recap-info">
        <b className="recap-title">{tr.title}</b>
        <span className="recap-dest">{tr.destinationName ? `📍 ${tr.destinationName}` : ''}</span>
        <span className="recap-dates">{tr.startDate} ~ {tr.endDate}</span>
        <div className="recap-chips">
          <span>🗓 {tr.dayCount}{t('일')}</span>
          {tr.placeCount > 0 && <span>📍 {tr.placeCount}{t('곳')}</span>}
          {tr.photoCount > 0 && <span>📷 {tr.photoCount}{t('장')}</span>}
        </div>
      </div>
    </button>
  )
}

/* ── 프로필: 아바타(클릭해 변경) + 닉네임 + 로그인 방식 + 비밀번호 변경 ── */
function ProfileCard({ user, onUser, onError }) {
  const { t } = useI18n()
  const [editing, setEditing] = useState(false)
  const [pwOpen, setPwOpen] = useState(false)
  const [picker, setPicker] = useState(false)
  const [nickname, setNickname] = useState(user.nickname)
  const [busy, setBusy] = useState(false)

  async function saveNick(e) {
    e.preventDefault()
    setBusy(true)
    try { onUser(await api.updateProfile({ nickname })); setEditing(false) }
    catch (err) { onError(err.message) } finally { setBusy(false) }
  }

  return (
    <section className="card mypage-profile">
      <button className="mp-avatar-btn" onClick={() => setPicker(true)} title={t('프로필 사진 변경')}>
        <img className="mp-avatar" src={avatarSrc(user.avatar)} alt="" />
        <span className="mp-avatar-edit">📷</span>
      </button>

      {editing ? (
        <form className="mp-nick-edit" onSubmit={saveNick}>
          <input value={nickname} maxLength={50} onChange={(e) => setNickname(e.target.value)} autoFocus />
          <button className="small" disabled={busy}>{t('저장')}</button>
          <button type="button" className="small ghost" onClick={() => { setNickname(user.nickname); setEditing(false) }}>{t('취소')}</button>
        </form>
      ) : (
        <div className="mp-id">
          <b>{user.nickname}</b>
          <button className="edit" onClick={() => setEditing(true)} title={t('닉네임 수정')}>✎</button>
        </div>
      )}
      <p className="mp-meta">
        <span>✉️ {user.email}</span>
        <span className="mp-provider">{t(PROVIDER_LABEL[user.provider] || user.provider)} {t('로그인')}</span>
      </p>
      {user.provider === 'LOCAL' && (
        pwOpen
          ? <PasswordForm onDone={() => setPwOpen(false)} onError={onError} />
          : <button className="small ghost" onClick={() => setPwOpen(true)}>🔑 {t('비밀번호 변경')}</button>
      )}

      {picker && <AvatarPicker current={user.avatar} onUser={onUser} onClose={() => setPicker(false)} onError={onError} />}
    </section>
  )
}

/** 아바타 선택: 기본 + 프리셋 그리드 + 직접 올리기. */
function AvatarPicker({ current, onUser, onClose, onError }) {
  const { t } = useI18n()
  const [busy, setBusy] = useState(false)

  async function pick(avatar) {
    setBusy(true)
    try { onUser(await api.updateProfile({ avatar })); onClose() }
    catch (e) { onError(e.message) } finally { setBusy(false) }
  }
  async function uploadFile(e) {
    const file = e.target.files?.[0]; e.target.value = ''
    if (!file) return
    setBusy(true)
    try { onUser(await api.uploadAvatar(file)); onClose() }
    catch (err) { onError(err.message) } finally { setBusy(false) }
  }

  const options = [{ id: '', src: DEFAULT_AVATAR }, ...AVATAR_PRESETS]
  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
        <div className="tr-head">
          <h3>📷 {t('프로필 사진')}</h3>
          <button className="x" onClick={onClose} aria-label={t('닫기')}>✕</button>
        </div>
        <div className="avatar-grid">
          {options.map((o) => (
            <button key={o.id || 'default'} className={`avatar-opt${(current || '') === o.id ? ' on' : ''}`}
                    onClick={() => pick(o.id)} disabled={busy}>
              <img src={o.src} alt="" />
            </button>
          ))}
        </div>
        <label className="ticket-upload" style={{ marginTop: 4 }}>
          {busy ? t('올리는 중...') : `📎 ${t('내 사진 올리기')}`}
          <input type="file" accept="image/*" hidden onChange={uploadFile} disabled={busy} />
        </label>
      </div>
    </div>
  )
}

function PasswordForm({ onDone, onError }) {
  const { t } = useI18n()
  const [cur, setCur] = useState('')
  const [nw, setNw] = useState('')
  const [busy, setBusy] = useState(false)
  async function submit(e) {
    e.preventDefault()
    setBusy(true)
    try { await api.changePassword({ currentPassword: cur, newPassword: nw }); alert(t('비밀번호가 변경되었습니다.')); onDone() }
    catch (err) { onError(err.message) } finally { setBusy(false) }
  }
  return (
    <form className="mp-pw" onSubmit={submit}>
      <input type="password" placeholder={t('현재 비밀번호')} value={cur} onChange={(e) => setCur(e.target.value)} required />
      <input type="password" placeholder={t('새 비밀번호 (8자 이상)')} value={nw} onChange={(e) => setNw(e.target.value)} required />
      <div className="row">
        <button type="button" className="small ghost" style={{ flex: 1 }} onClick={onDone}>{t('취소')}</button>
        <button style={{ flex: 2 }} disabled={busy}>{busy ? t('변경 중...') : t('비밀번호 변경')}</button>
      </div>
    </form>
  )
}

/* ── 로그아웃 / 회원 탈퇴 ── */
function DangerCard({ onLogout, onError }) {
  const { t } = useI18n()
  async function del() {
    if (!window.confirm(t('정말 탈퇴할까요?\n모든 여행·일정·기록이 영구 삭제되며 되돌릴 수 없습니다.'))) return
    if (!window.confirm(t('마지막 확인이에요. 탈퇴를 진행할까요?'))) return
    try { await api.deleteAccount(); onLogout() } catch (e) { onError(e.message) }
  }
  return (
    <section className="card">
      <button className="mp-logout" onClick={onLogout}>{t('로그아웃')}</button>
      <button className="mp-delete" onClick={del}>{t('회원 탈퇴')}</button>
    </section>
  )
}
