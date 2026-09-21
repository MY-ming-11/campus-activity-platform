const WEEKDAYS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

function parse(value) {
  if (!value) return null
  // 后端返回的是 "2026-10-15 19:00:00"，换成 ISO 写法再解析，
  // 否则 Safari 上会解析失败
  const date = new Date(String(value).replace(' ', 'T'))
  return Number.isNaN(date.getTime()) ? null : date
}

function pad(number) {
  return String(number).padStart(2, '0')
}

function time(date) {
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** 10月15日 周三 19:00 —— 列表卡片上用，突出"什么时候" */
export function formatWhen(value) {
  const date = parse(value)
  if (!date) return '时间待定'
  return `${date.getMonth() + 1}月${date.getDate()}日 ${WEEKDAYS[date.getDay()]} ${time(date)}`
}

/** 2026-10-15 19:00 —— 管理端表格用，需要完整年份 */
export function formatDateTime(value) {
  const date = parse(value)
  if (!date) return '—'
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${time(date)}`
}

/** 同一天只写一次日期：10月15日 周三 19:00 — 21:00 */
export function formatRange(start, end) {
  const from = parse(start)
  const to = parse(end)
  if (!from) return '时间待定'
  if (!to) return formatWhen(start)

  const head = `${from.getMonth() + 1}月${from.getDate()}日 ${WEEKDAYS[from.getDay()]} ${time(from)}`
  const tail =
    from.toDateString() === to.toDateString()
      ? time(to)
      : `${to.getMonth() + 1}月${to.getDate()}日 ${time(to)}`
  return `${head} — ${tail}`
}
