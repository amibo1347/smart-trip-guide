import { useEffect, useState } from 'react'
import { api, socialLoginUrl, setUnauthorizedHandler } from './api.js'
import TripDetail from './TripDetail.jsx'
import SharedView from './SharedView.jsx'
import FloatingTranslate from './Translate.jsx'
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
      {shareToken ? <SharedView token={shareToken} /> : <MainApp />}
      <FloatingTranslate />
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

  // 401(세션 만료/미인증) → 로그인 화면으로 전환
  useEffect(() => {
    setUnauthorizedHandler(() => { setUser(null); setTrips([]); setSelectedTrip(null) })
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
    setUser(null); setTrips([]); setSelectedTrip(null)
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
        <h1>🧳 {t('스마트 여행 플래너')}</h1>
        <LangSwitcher />
      </header>

      {error && <div className="error" onClick={() => setError('')}>⚠ {error} {t('(클릭하여 닫기)')}</div>}

      {selectedTrip ? (
        <TripDetail trip={selectedTrip} onBack={() => setSelectedTrip(null)} onError={setError} />
      ) : (
        <>
          {user
            ? <UserBadge user={user} onLogout={logout} />
            : <AuthSection onLoggedIn={onLoggedIn} onError={setError} />}

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
    </div>
  )
}

function UserBadge({ user, onLogout }) {
  const { t } = useI18n()
  return (
    <section className="card">
      <div className="badge-row">
        <p className="who">
          <b>{user.nickname}</b>
        </p>
        <button className="small" onClick={onLogout}>{t('로그아웃')}</button>
      </div>
    </section>
  )
}

function AuthSection({ onLoggedIn, onError }) {
  const { t } = useI18n()
  const [mode, setMode] = useState('login') // 'login' | 'signup'

  return (
    <section className="card">
      <h2>{mode === 'login' ? t('로그인') : t('회원가입')}</h2>

      {mode === 'login'
        ? <LoginForm onLoggedIn={onLoggedIn} onError={onError} />
        : <SignupForm onLoggedIn={onLoggedIn} onError={onError} />}

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

function LoginForm({ onLoggedIn, onError }) {
  const { t } = useI18n()
  const [form, setForm] = useState({ email: '', password: '' })
  const [busy, setBusy] = useState(false)

  async function submit(e) {
    e.preventDefault()
    setBusy(true)
    try {
      const s = await api.login(form)
      onLoggedIn(s.user)
    } catch (err) {
      onError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit}>
      <input type="email" placeholder={t('이메일 (아이디)')} value={form.email}
             onChange={(e) => setForm({ ...form, email: e.target.value })} required />
      <input type="password" placeholder={t('비밀번호')} value={form.password}
             onChange={(e) => setForm({ ...form, password: e.target.value })} required />
      <button disabled={busy}>{busy ? t('로그인 중...') : t('로그인')}</button>
    </form>
  )
}

function SignupForm({ onLoggedIn, onError }) {
  const { t } = useI18n()
  const empty = { email: '', password: '', password2: '', nickname: '' }
  const [form, setForm] = useState(empty)
  const [busy, setBusy] = useState(false)

  async function submit(e) {
    e.preventDefault()
    if (form.password !== form.password2) {
      onError(t('비밀번호가 일치하지 않습니다.'))
      return
    }
    setBusy(true)
    try {
      await api.createUser({ email: form.email, password: form.password, nickname: form.nickname })
      // 가입 직후 자동 로그인(세션 발급)
      const s = await api.login({ email: form.email, password: form.password })
      onLoggedIn(s.user)
    } catch (err) {
      onError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit}>
      <input type="email" placeholder={t('이메일 (아이디)')} value={form.email}
             onChange={(e) => setForm({ ...form, email: e.target.value })} required />
      <input type="password" placeholder={t('비밀번호 (8자 이상)')} value={form.password}
             onChange={(e) => setForm({ ...form, password: e.target.value })} required />
      <input type="password" placeholder={t('비밀번호 재입력')} value={form.password2}
             onChange={(e) => setForm({ ...form, password2: e.target.value })} required />
      <input placeholder={t('닉네임')} value={form.nickname}
             onChange={(e) => setForm({ ...form, nickname: e.target.value })} required />
      <button disabled={busy}>{busy ? t('가입 중...') : t('가입하기')}</button>
    </form>
  )
}

function TripSection({ trips, onCreated, onOpen, onError }) {
  const { t, lang } = useI18n()
  const empty = { title: '', startDate: '', endDate: '', headcount: 1, budgetLimit: '', concept: '' }
  const [form, setForm] = useState(empty)
  const [busy, setBusy] = useState(false)

  async function submit(e) {
    e.preventDefault()
    setBusy(true)
    try {
      await api.createTrip({
        title: form.title,
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
        <h2>② {t('여행 만들기')}</h2>
        <form onSubmit={submit}>
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

      <section className="card">
        <h2>③ {t('내 여행')} ({trips.length})</h2>
        {trips.length === 0 && <p className="muted">{t('아직 여행이 없습니다. 위에서 추가해 보세요.')}</p>}
        <ul className="trips">
          {trips.map((trip) => (
            <li key={trip.id} className="clickable" onClick={() => onOpen(trip)}
                title={t('클릭하여 일정 편집')}>
              <div className="trip-head">
                <b>{trip.title}</b>
                <span className={`status ${trip.status}`}>{trip.status}</span>
                <button className="del" onClick={(e) => remove(e, trip)} title={t('여행 삭제')}>✕</button>
              </div>
              <div className="trip-meta">
                📅 {trip.startDate} ~ {trip.endDate} · 👥 {trip.headcount}{t('명')}
                {trip.budgetLimit != null && <> · 💰 {Number(trip.budgetLimit).toLocaleString()}{t('원')}</>}
                {trip.concept && <> · 🏷 {trip.concept}</>}
              </div>
              <div className="trip-cta">{t('일정 편집 →')}</div>
            </li>
          ))}
        </ul>
      </section>
    </>
  )
}
