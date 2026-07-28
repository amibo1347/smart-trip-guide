import { useEffect, useState } from 'react'
import { api, socialLoginUrl, setUnauthorizedHandler } from './api.js'
import TripDetail from './TripDetail.jsx'
import SharedView from './SharedView.jsx'
import FloatingTranslate from './Translate.jsx'
import PlaceSearchInput from './PlaceSearchInput.jsx'
import RecommendDestinations from './RecommendDestinations.jsx'
import MyPage from './MyPage.jsx'
import { avatarSrc } from './utils/avatars.js'
import { LANGS, useI18n } from './i18n/index.jsx'

/** /share/{token} 경로면 로그인 없이 읽기 전용 공유 뷰를 보여준다(라우터 미사용 → 경로 직접 판별). */
function getShareToken() {
  const m = window.location.pathname.match(/^\/share\/([A-Za-z0-9]+)/)
  return m ? m[1] : null
}

export default function App() {
  const shareToken = getShareToken()
  return (
    <>
      {shareToken ? <><SharedView token={shareToken} /><FloatingTranslate /></> : <MainApp />}
    </>
  )
}

/** 헤더의 UI 언어 선택(한/영/일/중). */
function LangSwitcher() {
  const { lang, setLang } = useI18n()
  return (
    <select className="lang-switch" value={lang} onChange={(e) => setLang(e.target.value)} aria-label="Language">
      {LANGS.map((l) => <option key={l.code} value={l.code}>{l.label}</option>)}
    </select>
  )
}

function MainApp() {
  const { t } = useI18n()
  const [user, setUser] = useState(null)
  const [trips, setTrips] = useState([])
  const [error, setError] = useState('')
  const [selectedTrip, setSelectedTrip] = useState(null)
  const [myPage, setMyPage] = useState(false)

  // 401(세션 만료/미인증) → 로그인 화면으로 전환
  useEffect(() => {
    setUnauthorizedHandler(() => { setUser(null); setTrips([]); setSelectedTrip(null); setMyPage(false) })
  }, [])

  // 세션 로그인 상태 확인 (로컬/소셜 공통)
  useEffect(() => {
    api.me()
      .then((s) => { if (s.authenticated) { setUser(s.user); refreshTrips() } })
      .catch(() => {})
  }, [])

  function onLoggedIn(u) {
    setUser(u); setError(''); refreshTrips()
  }

  async function logout() {
    try { await api.logout() } catch { /* noop */ }
    setUser(null); setTrips([]); setSelectedTrip(null); setMyPage(false)
  }

  async function refreshTrips() {
    try {
      setTrips(await api.listTrips())
    } catch (e) {
      if (e.status === 401) { setUser(null); setTrips([]); setSelectedTrip(null) } // 세션 만료 → 로그인 화면
      else setError(e.message)
    }
  }

  return (
    <div className="app">
      <header>
        <button className="brand" onClick={() => { setMyPage(false); setSelectedTrip(null) }}>
          <span className="brand-logo">🧳</span>
          <span className="brand-name">{t('스마트 여행 플래너')}</span>
        </button>
        <div className="header-actions">
          <LangSwitcher />
          {user && (
            <button className="mypage-btn" onClick={() => { setMyPage(true); setSelectedTrip(null) }}
                    title={t('마이페이지')}>
              <img src={avatarSrc(user.avatar)} alt={t('마이페이지')} />
            </button>
          )}
        </div>
      </header>

      {error && <div className="error" onClick={() => setError('')}>⚠ {error} {t('(클릭하여 닫기)')}</div>}

      {myPage && user ? (
        <MyPage user={user} onUser={setUser}
                onOpenTrip={(tripId) => { const tr = trips.find((x) => x.id === tripId); if (tr) { setSelectedTrip(tr); setMyPage(false) } }}
                onLogout={logout} onClose={() => setMyPage(false)} onError={setError} />
      ) : selectedTrip ? (
        <TripDetail trip={selectedTrip} defaultOrigin={user?.defaultOrigin}
                    onBack={() => setSelectedTrip(null)} onError={setError} />
      ) : (
        <>
          {user
            ? <UserBadge user={user} onLogout={logout} />
            : <AuthSection onLoggedIn={onLoggedIn} />}

          {user && (
            <TripSection
              trips={trips}
              onCreated={refreshTrips}
              onOpen={setSelectedTrip}
              onError={setError}
            />
          )}
        </>
      )}
      {/* 번역 도우미 FAB — 마이페이지에서는 숨긴다 */}
      {!(myPage && user) && <FloatingTranslate />}
    </div>
  )
}

function UserBadge({ user, onLogout }) {
  const { t } = useI18n()
  return (
    <section className="card">
      <div className="badge-row">
        <p className="who">
          <b>{user.nickname}</b>{t('님, 오늘도 좋은 여행 되세요 ✈️')}
        </p>
        <button className="small" onClick={onLogout}>{t('로그아웃')}</button>
      </div>
    </section>
  )
}

// 로그인·가입 실패는 폼 안에서 직접 알린다(상단 배너로 올리지 않음) — onError 를 받지 않는 이유.
function AuthSection({ onLoggedIn }) {
  const { t } = useI18n()
  const [mode, setMode] = useState('login') // 'login' | 'signup'

  return (
    <section className="card">
      <h2>{mode === 'login' ? t('로그인') : t('회원가입')}</h2>

      {mode === 'login'
        ? <LoginForm onLoggedIn={onLoggedIn} />
        : <SignupForm onLoggedIn={onLoggedIn} />}

      <p className="switch">
        {mode === 'login'
          ? <>{t('계정이 없으신가요?')} <button className="link" onClick={() => setMode('signup')}>{t('회원가입')}</button></>
          : <>{t('이미 계정이 있으신가요?')} <button className="link" onClick={() => setMode('login')}>{t('로그인')}</button></>}
      </p>

      <div className="divider"><span>{t('소셜 계정으로 로그인')}</span></div>
      <div className="social">
        <a className="sbtn google" href={socialLoginUrl('google')}><span>G</span> {t('Google로 계속하기')}</a>
        <a className="sbtn kakao" href={socialLoginUrl('kakao')}><span>K</span> {t('카카오로 계속하기')}</a>
      </div>
    </section>
  )
}

function LoginForm({ onLoggedIn }) {
  const { t } = useI18n()
  const [form, setForm] = useState({ email: '', password: '' })
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState('')

  // 입력을 고치는 순간 이전 실패 문구는 지운다 — 다시 시도 중인데 빨간 글씨가 남아 있으면 헷갈린다.
  function change(patch) {
    setForm((f) => ({ ...f, ...patch }))
    setErr('')
  }

  async function submit(e) {
    e.preventDefault()
    setBusy(true)
    setErr('')
    try {
      const s = await api.login(form)
      onLoggedIn(s.user)
    } catch (e2) {
      // 상단 배너가 아니라 폼 안에서만 알린다. 비밀번호 오타는 흔한 일이라 페이지 전체로 경고할 일이 아니다.
      setErr(e2.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit}>
      <input type="email" placeholder={t('이메일 (아이디)')} value={form.email}
             onChange={(e) => change({ email: e.target.value })} required />
      <input type="password" placeholder={t('비밀번호')} value={form.password}
             onChange={(e) => change({ password: e.target.value })} required />
      {/* 실패 문구 자리는 입력칸과 버튼 사이 — 눈은 이미 여기 있고, 버튼을 다시 누르기 직전에 읽힌다 */}
      {err && <p className="form-error" role="alert">{t(err)}</p>}
      <button disabled={busy}>{busy ? t('로그인 중...') : t('로그인')}</button>
    </form>
  )
}

function SignupForm({ onLoggedIn }) {
  const { t } = useI18n()
  const empty = { email: '', password: '', password2: '', nickname: '' }
  const [form, setForm] = useState(empty)
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState('')

  function change(patch) {
    setForm((f) => ({ ...f, ...patch }))
    setErr('')
  }

  async function submit(e) {
    e.preventDefault()
    if (form.password !== form.password2) {
      setErr(t('비밀번호가 일치하지 않습니다.'))
      return
    }
    setBusy(true)
    setErr('')
    try {
      await api.createUser({ email: form.email, password: form.password, nickname: form.nickname })
      // 가입 직후 자동 로그인(세션 발급)
      const s = await api.login({ email: form.email, password: form.password })
      onLoggedIn(s.user)
    } catch (e2) {
      // 로그인과 같은 자리·같은 톤으로 — 한 카드 안에서 알림 방식이 달라지면 어색하다.
      setErr(e2.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit}>
      <input type="email" placeholder={t('이메일 (아이디)')} value={form.email}
             onChange={(e) => change({ email: e.target.value })} required />
      <input type="password" placeholder={t('비밀번호 (8자 이상)')} value={form.password}
             onChange={(e) => change({ password: e.target.value })} required />
      <input type="password" placeholder={t('비밀번호 재입력')} value={form.password2}
             onChange={(e) => change({ password2: e.target.value })} required />
      <input placeholder={t('닉네임')} value={form.nickname}
             onChange={(e) => change({ nickname: e.target.value })} required />
      {err && <p className="form-error" role="alert">{t(err)}</p>}
      <button disabled={busy}>{busy ? t('가입 중...') : t('가입하기')}</button>
    </form>
  )
}

function TripSection({ trips, onCreated, onOpen, onError }) {
  const { t, lang } = useI18n()
  const empty = { title: '', destinationName: '', destinationLat: null, destinationLng: null, destinationPhoto: null,
                  startDate: '', endDate: '', headcount: 1, budgetLimit: '', concept: '' }
  const [form, setForm] = useState(empty)
  const [busy, setBusy] = useState(false)

  // 목적지를 고르면 좌표·대표사진을 담고, 제목이 비어 있으면 목적지 이름으로 채운다(수정 가능).
  function pickDestination(p) {
    setForm((f) => ({
      ...f,
      destinationName: p.name,
      destinationLat: p.latitude,
      destinationLng: p.longitude,
      destinationPhoto: p.photoUrl || null,
      title: f.title.trim() ? f.title : `${p.name} 여행`,
    }))
  }

  // 추천 여행지 칩 → 그 이름으로 검색해 최상위 결과를 목적지로.
  async function selectReco(name) {
    setBusy(true)
    try {
      const hits = await api.searchPlacesRich(name, lang)
      if (hits?.[0]) pickDestination(hits[0])
      else pickDestination({ name, latitude: null, longitude: null })
    } catch (e) { onError(e.message) } finally { setBusy(false) }
  }

  async function submit(e) {
    e.preventDefault()
    setBusy(true)
    try {
      await api.createTrip({
        title: form.title,
        destinationName: form.destinationName || null,
        destinationLat: form.destinationLat,
        destinationLng: form.destinationLng,
        destinationPhoto: form.destinationPhoto,
        startDate: form.startDate,
        endDate: form.endDate,
        headcount: Number(form.headcount),
        budgetLimit: form.budgetLimit === '' ? null : Number(form.budgetLimit),
        concept: form.concept || null,
      })
      setForm(empty)
      onCreated()
    } catch (err) {
      onError(err.message)
    } finally {
      setBusy(false)
    }
  }

  async function remove(e, trip) {
    e.stopPropagation() // 카드 클릭(열기)과 분리
    if (!window.confirm(t("'{title}' 여행을 삭제할까요?\n일정·예약·기록이 목록에서 사라집니다.", { title: trip.title }))) return
    try { await api.deleteTrip(trip.id); onCreated() } catch (err) { onError(err.message) }
  }

  return (
    <>
      <section className="card">
        <h2>✏️ {t('여행 만들기')}</h2>
        <form onSubmit={submit}>
          <label className="field-label">{t('어디로 떠나요?')}</label>
          <PlaceSearchInput placeholder={t('목적지 검색 (예: 오사카, 부산 해운대)')}
                            onPick={pickDestination} onError={onError} />
          <RecommendDestinations onSelect={selectReco} busy={busy} />
          {form.destinationName && (
            <p className="picked-dest">📍 {form.destinationName} <span className="muted small-text">{t('선택됨')}</span></p>
          )}
          <input placeholder={t('여행 제목')} value={form.title}
                 onChange={(e) => setForm({ ...form, title: e.target.value })} required />
          <div className="row">
            <label>{t('시작')} <input type="date" lang={lang} value={form.startDate}
                   onChange={(e) => setForm({ ...form, startDate: e.target.value })} required /></label>
            <label>{t('종료')} <input type="date" lang={lang} value={form.endDate}
                   onChange={(e) => setForm({ ...form, endDate: e.target.value })} required /></label>
          </div>
          <div className="row">
            <label>{t('인원')} <input type="number" min="1" value={form.headcount}
                   onChange={(e) => setForm({ ...form, headcount: e.target.value })} required /></label>
            <label>{t('예산')} <input type="number" min="0" placeholder={t('원')} value={form.budgetLimit}
                   onChange={(e) => setForm({ ...form, budgetLimit: e.target.value })} /></label>
          </div>
          <input placeholder={t('컨셉 (휴양/액티비티/맛집...)')} value={form.concept}
                 onChange={(e) => setForm({ ...form, concept: e.target.value })} />
          <button disabled={busy}>{busy ? t('저장 중...') : t('여행 추가')}</button>
        </form>
      </section>

      <TripLists trips={trips} onOpen={onOpen} onRemove={remove} />
    </>
  )
}

/** 오늘(로컬) yyyy-mm-dd. */
function todayStr() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
/** 두 yyyy-mm-dd 사이 일수(a→b). */
function daysBetween(a, b) {
  return Math.round((Date.parse(b + 'T00:00:00') - Date.parse(a + 'T00:00:00')) / 86400000)
}
/** 여행 단계 판정: 다가옴/여행중/지남 + D-day 라벨. */
function tripPhase(trip) {
  const today = todayStr()
  if (trip.endDate < today) return { phase: 'past', dday: daysBetween(trip.endDate, today) }
  if (trip.startDate > today) return { phase: 'upcoming', dday: daysBetween(today, trip.startDate) }
  return { phase: 'ongoing', dayNo: daysBetween(trip.startDate, today) + 1,
           total: daysBetween(trip.startDate, trip.endDate) + 1 }
}

/** 여행 목록: 다가오는 여행(진행 중 포함) + 지난 여행으로 분리. */
function TripLists({ trips, onOpen, onRemove }) {
  const { t } = useI18n()
  const [showPast, setShowPast] = useState(false)

  const withPhase = trips.map((trip) => ({ trip, ...tripPhase(trip) }))
  // 다가오는·진행중: 시작일 가까운 순. 지난: 최근 순.
  const active = withPhase.filter((x) => x.phase !== 'past')
    .sort((a, b) => a.trip.startDate.localeCompare(b.trip.startDate))
  const past = withPhase.filter((x) => x.phase === 'past')
    .sort((a, b) => b.trip.startDate.localeCompare(a.trip.startDate))

  return (
    <>
      <section className="card">
        <h2>🧭 {t('다가오는 여행')} ({active.length})</h2>
        {active.length === 0 && <p className="muted">{t('예정된 여행이 없어요. 위에서 새 여행을 만들어 보세요.')}</p>}
        <ul className="trips">
          {active.map(({ trip, phase, dday, dayNo, total }) => (
            <TripCard key={trip.id} trip={trip} phase={phase} dday={dday} dayNo={dayNo} total={total}
                      onOpen={onOpen} onRemove={onRemove} t={t} />
          ))}
        </ul>
      </section>

      {past.length > 0 && (
        <section className="card">
          <h2 className="collapse-head" onClick={() => setShowPast((v) => !v)}>
            🗂 {t('지난 여행')} ({past.length}) <span className="chev">{showPast ? '▾' : '▸'}</span>
          </h2>
          {showPast && (
            <ul className="trips">
              {past.map(({ trip, dday }) => (
                <TripCard key={trip.id} trip={trip} phase="past" dday={dday}
                          onOpen={onOpen} onRemove={onRemove} t={t} />
              ))}
            </ul>
          )}
        </section>
      )}
    </>
  )
}

function TripCard({ trip, phase, dday, dayNo, total, onOpen, onRemove, t }) {
  const badge = phase === 'ongoing'
    ? { cls: 'ongoing', text: `🧳 ${t('여행 중')} ${dayNo}/${total}` }
    : phase === 'past'
      ? { cls: 'past', text: `${dday}${t('일 전')}` }
      : dday0(dday)
  function dday0(d) {
    if (d === 0) return { cls: 'today', text: `🔥 ${t('D-DAY')}` }
    if (d <= 7) return { cls: 'soon', text: `D-${d}` }
    return { cls: 'upcoming', text: `D-${d}` }
  }
  return (
    <li className={`trip-card clickable ${phase}${trip.destinationPhoto ? ' has-photo' : ''}`}
        onClick={() => onOpen(trip)} title={t('클릭하여 일정 편집')}>
      {trip.destinationPhoto && (
        <div className="trip-bg" style={{ backgroundImage: `url("${trip.destinationPhoto}")` }} aria-hidden="true" />
      )}
      <div className="trip-top">
        <span className={`dday ${badge.cls}`}>{badge.text}</span>
        {trip.shareToken && <span className="trip-share" title={t('공유 중')}>🔗</span>}
        <button className="del" onClick={(e) => onRemove(e, trip)} title={t('여행 삭제')}>✕</button>
      </div>
      <b className="trip-title">{trip.title}</b>
      <div className="trip-meta">
        {trip.destinationName && <>📍 {trip.destinationName} · </>}
        📅 {trip.startDate} ~ {trip.endDate} · 👥 {trip.headcount}{t('명')}
        {trip.budgetLimit != null && <> · 💰 {Number(trip.budgetLimit).toLocaleString()}{t('원')}</>}
        {trip.concept && <> · 🏷 {trip.concept}</>}
      </div>
      <div className="trip-cta">{t('일정 편집 →')}</div>
    </li>
  )
}
