// 예산 표의 키-값 한 줄 (이전엔 Itinerary·Review 에 중복).
export default function Row({ k, v, strong, cls }) {
  return (
    <div className="brow">
      <span>{k}</span>
      <b className={cls}>{strong ? <b>{v}</b> : v}</b>
    </div>
  )
}
