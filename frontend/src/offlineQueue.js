// 오프라인 큐 (설계 4.B): 네트워크 단절 시 기록을 로컬에 쌓고, 복귀하면 동기화.
// 각 항목은 client_uuid 멱등키를 포함하므로 재전송이 중복을 만들지 않는다.
// v1은 localStorage 사용(단순). 대용량/사진 동기화 시 IndexedDB로 승격 권장.

const KEY = 'stp_offline_queue'

function load() {
  try { return JSON.parse(localStorage.getItem(KEY) || '[]') } catch { return [] }
}
function save(q) { localStorage.setItem(KEY, JSON.stringify(q)) }

export function enqueue(item) {
  const q = load()
  q.push(item)        // { path, body }
  save(q)
  return q.length
}

export function pendingCount() { return load().length }

/**
 * 큐를 비운다. send(path, body)가 성공한 항목만 제거하고 실패분은 남긴다.
 * @returns {Promise<number>} 동기화 성공 건수
 */
export async function flush(send) {
  const q = load()
  if (q.length === 0) return 0
  const remaining = []
  let synced = 0
  for (const item of q) {
    try {
      await send(item.path, item.body)
      synced++
    } catch {
      remaining.push(item) // 실패 → 다음 기회에 재시도
    }
  }
  save(remaining)
  return synced
}
