import { useEffect, useState } from 'react'
import { api, socialLoginUrl } from './api.js'
import Itinerary from './Itinerary.jsx'

export default function App() {
  const [health, setHealth] = useState('확인 중...')
  const [user, setUser] = useState(null)
  const [social, setSocial] = useState(false) // 소셜 세션으로 로그인됐는지
  const [trips, setTrips] = useState([])
  const [error, setError] = useState('')
  const [selectedTrip, setSelectedTrip] = useState(null)

  // 헬스체크
  useEffect(() => {
    api.health()
      .then((d) => setHealth(`${d.status} (${d.service})`))
      .catch(() => setHealth('백엔드 연결 실패 — 8082 기동 확인'))
  }, [])

  // 소셜 로그인 세션 확인 (있으면 자동 로그인 상태로)
  useEffect(() => {
    api.me()
      .then((s) => { if (s.authenticated) { setUser(s.user); setSocial(true); refreshTrips(s.user.id) } })
      .catch(() => {})
  }, [])

  async function logout() {
    try { await api.logout() } catch { /* noop */ }
    setUser(null); setSocial(false); setTrips([]); setSelectedTrip(null)
  }

  async function refreshTrips(userId) {
    try {
      setTrips(await api.listTrips(userId))
    } catch (e) {
      setError(e.message)
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
        <Itinerary trip={selectedTrip} onBack={() => setSelectedTrip(null)} onError={setError} />
      ) : (
        <>
          <UserSection
            user={user}
            social={social}
            onCreated={(u) => { setUser(u); setError(''); refreshTrips(u.id) }}
            onLogout={logout}
            onError={setError}
          />

          {user && (
            <TripSection
              user={user}
              trips={trips}
              onCreated={() => refreshTrips(user.id)}
              onOpen={setSelectedTrip}
              onError={setError}
            />
          )}
        </>
      )}
    </div>
  )
}

function UserSection({ user, social, onCreated, onLogout, onError }) {
  const [form, setForm] = useState({ email: '', password: '', nickname: '' })
  const [busy, setBusy] = useState(false)

  async function submit(e) {
    e.preventDefault()
    setBusy(true)
    try {
      onCreated(await api.createUser(form))
    } catch (err) {
      onError(err.message)
    } finally {
      setBusy(false)
    }
  }

  if (user) {
    return (
      <section className="card">
        <h2>① 사용자</h2>
        <p className="who">
          <b>{user.nickname}</b> ({user.email}) · <span className="prov">{user.provider}</span>
        </p>
        {social && <button className="small" onClick={onLogout}>로그아웃</button>}
      </section>
    )
  }

  return (
    <section className="card">
      <h2>① 로그인 / 회원가입</h2>

      <div className="social">
        <a className="sbtn google" href={socialLoginUrl('google')}>
          <span>G</span> Google로 계속하기
        </a>
        <a className="sbtn kakao" href={socialLoginUrl('kakao')}>
          <span>K</span> 카카오로 계속하기
        </a>
      </div>
      <p className="muted small-text">소셜 로그인은 서버에 자격증명이 설정된 경우 동작합니다.</p>

      <div className="divider"><span>또는 이메일로 가입</span></div>

      <form onSubmit={submit}>
        <input placeholder="이메일" type="email" value={form.email}
               onChange={(e) => setForm({ ...form, email: e.target.value })} required />
        <input placeholder="비밀번호 (8자 이상)" type="password" value={form.password}
               onChange={(e) => setForm({ ...form, password: e.target.value })} required />
        <input placeholder="닉네임" value={form.nickname}
               onChange={(e) => setForm({ ...form, nickname: e.target.value })} required />
        <button disabled={busy}>{busy ? '처리 중...' : '가입하기'}</button>
      </form>
    </section>
  )
}

function TripSection({ user, trips, onCreated, onOpen, onError }) {
  const empty = { title: '', startDate: '', endDate: '', headcount: 1, budgetLimit: '', concept: '' }
  const [form, setForm] = useState(empty)
  const [busy, setBusy] = useState(false)

  async function submit(e) {
    e.preventDefault()
    setBusy(true)
    try {
      await api.createTrip({
        userId: user.id,
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
