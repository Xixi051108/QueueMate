const dateTimeFormatter = new Intl.DateTimeFormat('zh-CN', {
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
})

export function formatMoney(value) {
  const amount = Number(value || 0)
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: 'CNY',
    minimumFractionDigits: 2,
  }).format(amount)
}

export function formatDateTime(value) {
  if (!value) return '—'
  return dateTimeFormatter.format(new Date(value))
}

export function isoDate(offsetDays = 0) {
  const date = new Date()
  date.setDate(date.getDate() + offsetDays)
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 10)
}

const labels = {
  ACTIVE: '营业中',
  INACTIVE: '已停用',
  OPEN: '可预约',
  CLOSED: '已关闭',
  BOOKED: '已预约',
  CANCELLED: '已取消',
  FULFILLED: '已完成',
  NO_SHOW: '未到店',
  WAITING: '等待中',
  CALLED: '已叫号',
  COMPLETED: '已完成',
  MISSED: '已过号',
  AVAILABLE: '可使用',
  REDEEMED: '已核销',
  VOID: '已作废',
  EXPIRED: '已过期',
  NOT_REQUIRED: '无需支付',
  PAID: '已支付',
  REFUNDED: '已退款',
  FROZEN: '已冻结',
  RECHARGE: '充值',
  PAYMENT: '预约支付',
  REFUND: '预约退款',
  ADJUSTMENT: '余额调整',
  TEA_SHOP: '奶茶店',
  STUDY_ROOM: '自习室',
  BADMINTON_COURT: '羽毛球场',
  USER: '普通用户',
  MERCHANT: '商家',
  ADMIN: '管理员',
  SUCCESS: '成功',
  FAILED: '失败',
  UNPAID: '未支付',
}

export function labelOf(value) {
  return labels[value] || value || '未知'
}

export function statusType(value) {
  if (['ACTIVE', 'OPEN', 'FULFILLED', 'COMPLETED', 'REDEEMED', 'PAID', 'SUCCESS'].includes(value)) return 'success'
  if (['WAITING', 'CALLED', 'BOOKED', 'AVAILABLE'].includes(value)) return 'primary'
  if (['NO_SHOW', 'MISSED', 'EXPIRED'].includes(value)) return 'warning'
  if (['FROZEN', 'FAILED'].includes(value)) return 'danger'
  return 'info'
}

export function isBalanceIncrease(transaction) {
  return Number(transaction?.balanceAfter || 0) >= Number(transaction?.balanceBefore || 0)
}

export function formatBalanceChange(transaction) {
  return `${isBalanceIncrease(transaction) ? '+' : '-'}${formatMoney(Math.abs(Number(transaction?.amount || 0)))}`
}
