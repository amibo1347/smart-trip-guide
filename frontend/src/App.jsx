import { useEffect, useState } from 'react'
import { api, socialLoginUrl } from './api.js'
import TripDetail from './TripDetail.jsx'

export default function App() {
  const [health, setHealth] = useState('확인 중...')
  const [user, setUser] = useState(null)
  const [trips, setTrips] = useState([])
  const [error, setError] = useState('')
  const [selectedTrip, setSelectedTrip] = useState(null)

  // 헬스체크
  useEffect(() => {
    api.health()
      .then((d) => setHealth(`${d.status} (${d.service})`))
      .catch(() => setHealth('백엔드 연결 실패 — 8082 기동 확인'))
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
        <h1>🧳 스마트 여행 플래너</h1>
        <span className={`badge ${health.startsWith('UP') ? 'ok' : 'bad'}`}>API: {health}</span>
      </header>

      {error && <div className="error" onClick={() => setError('')}>⚠ {error} (클릭하여 닫기)</div>}

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
  return (
    <section className="card">
      <div className="badge-row">
        <p className="who">
          <b>{user.nickname}</b> ({user.email}) · <span className="prov">{user.provider}</span>
        </p>
        <button className="small" onClick={onLogout}>로그아웃</button>
      </div>
    </section>
  )
}

function AuthSection({ onLoggedIn, onError }) {
  const [mode, setMode] = useState('login') // 'login' | 'signup'

  return (
    <section className="card">
      <h2>{mode === 'login' ? '로그인' : '회원가입'}</h2>

      {mode === 'login'
        ? <LoginForm onLoggedIn={onLoggedIn} onError={onError} />
        : <SignupForm onLoggedIn={onLoggedIn} onError={onError} />}

      <p className="switch">
        {mode === 'login'
          ? <>계정이 없으신가요? <button className="link" onClick={() => setMode('signup')}>회원가입</button></>
          : <>이미 계정이 있으신가요? <button className="link" onClick={() => setMode('login')}>로그인</button></>}
      </p>

      <div className="divider"><span>소셜 계정으로 로그인</span></div>
      <div className="social">
        <a className="sbtn google" href={socialLoginUrl('google')}><span>G</span> Google로 계속하기</a>
        <a className="sbtn kakao" href={socialLoginUrl('kakao')}><span>K</span> 카카오로 계속하기</a>
      </div>
    </section>
  )
}

function LoginForm({ onLoggedIn, onError }) {
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
      <input type="email" placeholder="이메일 (아이디)" value={form.email}
             onChange={(e) => setForm({ ...form, email: e.target.value })} required />
      <input type="password" placeholder="비밀번호" value={form.password}
             onChange={(e) => setForm({ ...form, password: e.target.value })} required />
      <button disabled={busy}>{busy ? '로그인 중...' : '로그인'}</button>
    </form>
  )
}

function SignupForm({ onLoggedIn, onError }) {
  const empty = { email: '', password: '', password2: '', nickname: '' }
  const [form, setForm] = useState(empty)
  const [busy, setBusy] = useState(false)

  async function submit(e) {
    e.preventDefault()
    if (form.password !== form.password2) {
      onError('비밀번호가 일치하지 않습니다.')
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
      <input type="email" placeholder="이메일 (아이디)" value={form.email}
             onChange={(e) => setForm({ ...form, email: e.target.value })} required />
      <input type="password" placeholder="비밀번호 (8자 이상)" value={form.password}
             onChange={(e) => setForm({ ...form, password: e.target.value })} required />
      <input type="password" placeholder="비밀번호 재입력" value={form.password2}
             onChange={(e) => setForm({ ...form, password2: e.target.value })} required />
      <input placeholder="닉네임" value={form.nickname}
             onChange={(e) => setForm({ ...form, nickname: e.target.value })} required />
      <button disabled={busy}>{busy ? '가입 중...' : '가입하기'}</button>
    </form>
  )
}

function TripSection({ trips, onCreated, onOpen, onError }) {
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

  return (
    <>
      <section className="card">
        <h2>② 여행 만들기</h2>
        <form onSubmit={submit}>
          <input placeholder="여행 제목" value={form.title}
                 onChange={(e) => setForm({ ...form, title: e.target.value })} required />
          <div className="row">
            <label>시작 <input type="date" value={form.startDate}
                   onChange={(e) => setForm({ ...form, startDate: e.target.value })} required /></label>
            <label>종료 <input type="date" value={form.endDate}
                   onChange={(e) => setForm({ ...form, endDate: e.target.value })} required /></label>
          </div>
          <div className="row">
            <label>인원 <input type="number" min="1" value={form.headcount}
                   onChange={(e) => setForm({ ...form, headcount: e.target.value })} required /></label>
            <label>예산 <input type="number" min="0" placeholder="원" value={form.budgetLimit}
                   onChange={(e) => setForm({ ...form, budgetLimit: e.target.value })} /></label>
          </div>
          <input placeholder="컨셉 (휴양/액티비티/맛집...)" value={form.concept}
                 onChange={(e) => setForm({ ...form, concept: e.target.value })} />
          <button disabled={busy}>{busy ? '저장 중...' : '여행 추가'}</button>
        </form>
      </section>

      <section className="card">
        <h2>③ 내 여행 ({trips.length})</h2>
        {trips.length === 0 && <p className="muted">아직 여행이 없습니다. 위에서 추가해 보세요.</p>}
        <ul className="trips">
          {trips.map((t) => (
            <li key={t.id} className="clickable" onClick={() => onOpen(t)}
                title="클릭하여 일정 편집">
              <div className="trip-head">
                <b>{t.title}</b>
                <span className={`status ${t.status}`}>{t.status}</span>
              </div>
              <div className="trip-meta">
                📅 {t.startDate} ~ {t.endDate} · 👥 {t.headcount}명
                {t.budgetLimit != null && <> · 💰 {Number(t.budgetLimit).toLocaleString()}원</>}
                {t.concept && <> · 🏷 {t.concept}</>}
              </div>
              <div className="trip-cta">일정 편집 →</div>
            </li>
          ))}
        </ul>
      </section>
    </>
  )
}
