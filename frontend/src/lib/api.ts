// src/lib/api.ts

export interface SessionData {
  username?: string;
  name?: string;
  email: string;
  role: string;
  permissions?: string[];
  token?: string;
}

// Địa chỉ URL gốc của Spring Boot Backend (mặc định cổng 8080).
// Khi triển khai có thể đặt VITE_API_URL trong file .env của frontend.
const RAW_API_BASE_URL =
  (import.meta as unknown as { env?: Record<string, string | undefined> }).env?.VITE_API_URL ||
  'http://localhost:8080/api';

// Chuẩn hóa URL, loại bỏ dấu / thừa ở cuối.
export const API_BASE_URL: string = RAW_API_BASE_URL.trim().replace(/\/+$/, '');
// ==========================================
// PHIÊN ĐĂNG NHẬP
// ==========================================

// Lưu phiên đăng nhập vào LocalStorage hoặc SessionStorage
export function saveSession(session: SessionData, remember: boolean = false) {
  const storage = remember ? localStorage : sessionStorage;
  storage.setItem('pbl4_session', JSON.stringify(session));
}

export function getSession(): SessionData | null {
  const data = localStorage.getItem('pbl4_session') || sessionStorage.getItem('pbl4_session');
  if (!data) return null;
  try {
    return JSON.parse(data);
  } catch {
    return null;
  }
}

export function clearSession() {
  localStorage.removeItem('pbl4_session');
  sessionStorage.removeItem('pbl4_session');
}

// Kiểm tra phiên hiện tại có quyền (permission) nào đó hay không.
export function hasPermission(session: SessionData | null, permission: string): boolean {
  return !!session?.permissions?.includes(permission);
}

// ==========================================
// HÀM DÙNG CHUNG
// ==========================================

// Đọc thông báo lỗi từ response của backend.
// Backend trả lỗi ở 2 dạng: JSON {"message": "..."} hoặc chuỗi văn bản thuần
// (ví dụ AuthController.login). Cần xử lý cả hai, nếu không thông báo cụ thể
// như "Email chưa được xác thực" sẽ bị thay bằng câu lỗi chung.
async function readErrorMessage(response: Response, fallback: string): Promise<string> {
  const raw = await response.text().catch(() => '');
  if (!raw.trim()) return fallback;

  try {
    const data = JSON.parse(raw);
    if (typeof data === 'string' && data) return data;
    return data?.message || data?.error || fallback;
  } catch {
    // Không phải JSON: bỏ qua nếu là trang HTML lỗi, ngược lại dùng nguyên văn
    return raw.trim().startsWith('<') ? fallback : raw.trim();
  }
}

// Lỗi API có kèm mã HTTP để giao diện phân biệt 401 / 403 / 404...
export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

// Sự kiện phát ra khi token hết hạn / không hợp lệ (401).
// Ở App hoặc AuthContext có thể lắng nghe để chuyển về trang đăng nhập:
//   window.addEventListener('pbl4:unauthorized', () => navigate('/login'));
export const UNAUTHORIZED_EVENT = 'pbl4:unauthorized';

// Gọi API có kèm JWT. Trả về JSON đã parse, hoặc chuỗi thuần nếu backend
// trả văn bản (ví dụ approve/reject/delete trả ResponseEntity<String>),
// hoặc undefined nếu body rỗng.
async function request<T>(path: string, fallbackError: string, init: RequestInit = {}): Promise<T> {
  const session = getSession();

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...init,
      headers: {
        'Content-Type': 'application/json',
        ...(session?.token ? { Authorization: `Bearer ${session.token}` } : {}),
        ...(init.headers || {}),
      },
    });
  } catch {
    // fetch ném lỗi khi không kết nối được (backend tắt, sai CORS...)
    throw new ApiError(0, 'Không kết nối được tới máy chủ. Vui lòng kiểm tra backend đang chạy.');
  }

  if (response.status === 401) {
    clearSession();
    window.dispatchEvent(new Event(UNAUTHORIZED_EVENT));
  }

  if (!response.ok) {
    throw new ApiError(response.status, await readErrorMessage(response, fallbackError));
  }

  const text = await response.text();
  if (!text) return undefined as T;
  try {
    return JSON.parse(text) as T;
  } catch {
    return text as unknown as T;
  }
}

// LocalDateTime của backend có thể về dạng chuỗi ISO hoặc mảng [y,M,d,h,m,s,nano].
// Hàm này chuyển cả hai về Date.
export type BackendDateTime = string | number[] | null | undefined;

export function toDate(value: BackendDateTime): Date | null {
  if (value == null) return null;
  if (Array.isArray(value)) {
    const [y, mo, d, h = 0, mi = 0, s = 0] = value;
    return new Date(y, mo - 1, d, h, mi, s);
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

// Định dạng Date -> "YYYY-MM-DDTHH:mm:ss" (giờ địa phương, không có múi giờ)
// để khớp với LocalDateTime của backend.
function toLocalDateTimeParam(date: Date): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return (
    `${date.getFullYear()}-${p(date.getMonth() + 1)}-${p(date.getDate())}` +
    `T${p(date.getHours())}:${p(date.getMinutes())}:${p(date.getSeconds())}`
  );
}

// ==========================================
// 1. ĐĂNG NHẬP
// ==========================================

// Hỗ trợ truyền identifier: có thể là username hoặc email
export async function login(identifier: string, password: string): Promise<SessionData> {
  const response = await fetch(`${API_BASE_URL}/auth/login`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    // Backend nhận cả username lẫn email trong trường "username"
    body: JSON.stringify({ username: identifier, password }),
  });

  if (!response.ok) {
    throw new Error(await readErrorMessage(response, 'Đăng nhập thất bại! Vui lòng kiểm tra lại tên đăng nhập hoặc mật khẩu.'));
  }

  const data = await response.json();

  return {
    username: data.username || identifier,
    name: data.name || data.username || identifier.split('@')[0],
    email: data.email || (identifier.includes('@') ? identifier : `${identifier}@pbl4.local`),
    role: data.role || 'MONITOR',
    // Backend luôn trả permissions (CLIENT_VIEW, ALERT_VIEW, MONITORING_VIEW...).
    // Không tự bịa quyền giả khi thiếu, tránh hiển thị chức năng mà backend sẽ trả 403.
    permissions: data.permissions || [],
    token: data.token || data.accessToken || data.jwt || '',
  };
}

// Đồng bộ lại role + permissions mới nhất (GET /api/auth/me).
// Gọi khi tải lại trang phòng trường hợp Admin vừa đổi quyền.
export async function fetchCurrentUser(): Promise<{
  username: string;
  email: string;
  role: string;
  permissions: string[];
}> {
  return request('/auth/me', 'Không thể tải thông tin người dùng.');
}

// Tạo username từ phần trước dấu @ của email.
// Backend chỉ cho phép chữ, số, dấu chấm, gạch dưới, gạch ngang (3-50 ký tự).
function buildUsername(email: string): string {
  const local = email.split('@')[0].replace(/[^A-Za-z0-9._-]/g, '_');
  return local.padEnd(3, '_').slice(0, 50);
}

// ==========================================
// 2. ĐĂNG KÝ TÀI KHOẢN (mặc định phân quyền MONITOR ở backend)
// ==========================================

// Lưu ý: backend không có trường "name" và luôn tự gán role MONITOR,
// nên chỉ gửi username, email, password. Tham số _name giữ lại để không phải sửa nơi gọi.
export async function register(_name: string, email: string, password: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/accounts/register`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      username: buildUsername(email),
      email,
      password,
    }),
  });

  if (!response.ok) {
    throw new Error(await readErrorMessage(response, 'Đăng ký tài khoản thất bại! Email hoặc tài khoản có thể đã tồn tại.'));
  }
}

// ==========================================
// 3. LUỒNG QUÊN MẬT KHẨU (PASSWORD RESET)
// ==========================================

// Bước 1: Gửi yêu cầu nhận mã OTP qua email (POST /api/auth/forgot-password)
export async function forgotPassword(email: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/auth/forgot-password`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ email }),
  });

  if (!response.ok) {
    throw new Error(await readErrorMessage(response, 'Không thể gửi yêu cầu khôi phục mật khẩu. Vui lòng kiểm tra lại email.'));
  }
}

// Bước 2: Xác thực mã OTP và đặt mật khẩu mới (POST /api/auth/reset-password)
export async function resetPassword(email: string, code: string, newPassword: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/auth/reset-password`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ email, code, newPassword }),
  });

  if (!response.ok) {
    throw new Error(await readErrorMessage(response, 'Đặt lại mật khẩu thất bại. Mã OTP không chính xác hoặc đã hết hạn.'));
  }
}

// ==========================================
// 4. XÁC THỰC EMAIL SAU ĐĂNG KÝ (OTP)
// ==========================================

// Xác thực OTP để kích hoạt tài khoản (POST /api/verification/email)
export async function verifyEmail(email: string, code: string): Promise<void> {
  const params = new URLSearchParams({ email: email.trim(), code: code.trim() });

  const response = await fetch(`${API_BASE_URL}/verification/email?${params.toString()}`, {
    method: 'POST',
  });

  if (!response.ok) {
    throw new Error(
      await readErrorMessage(
        response,
        'Xác thực email thất bại. Mã OTP không chính xác hoặc đã hết hạn.'
      )
    );
  }
}

// Gửi lại OTP xác thực email (POST /api/verification/email/send)
export async function resendVerification(email: string): Promise<void> {
  const params = new URLSearchParams({ email: email.trim() });

  const response = await fetch(`${API_BASE_URL}/verification/email/send?${params.toString()}`, {
    method: 'POST',
  });

  if (!response.ok) {
    throw new Error(
      await readErrorMessage(
        response,
        'Không thể gửi lại mã xác thực. Vui lòng thử lại sau.'
      )
    );
  }
}

// ==========================================
// 5. KIỂU DỮ LIỆU TỪ BACKEND
// ==========================================

export type ClientStatus = 'PENDING_APPROVAL' | 'ONLINE' | 'OFFLINE' | 'UNKNOWN' | 'DISABLED';
export type AgentStatus = 'PENDING_APPROVAL' | 'ONLINE' | 'OFFLINE' | 'DISABLED' | 'REJECTED';
export type AlertSeverity = 'INFO' | 'WARNING' | 'CRITICAL';
export type AlertStatus = 'ACTIVE' | 'ACKNOWLEDGED' | 'RESOLVED';
export type ResourceType = 'CPU' | 'MEMORY' | 'DISK' | 'NETWORK' | 'PROCESS';

// GET /api/clients
export interface ClientInfo {
  clientId: string;
  hostname: string;
  ipAddress: string;
  osName: string;
  osVersion: string;
  kernelVersion: string;
  status: ClientStatus;
  lastSeenAt: BackendDateTime;
  bootTime: BackendDateTime;
  createdAt: BackendDateTime;
  updatedAt: BackendDateTime;
  managementNote?: string | null;
}

// GET /api/clients/{clientId}/hardware
// cpu / memory là null nếu máy chưa có dữ liệu (Agent phiên bản cũ chưa gửi phần cứng).
export interface ClientHardware {
  clientId: string;
  cpu: {
    modelName: string | null;
    manufacturer: string | null;
    coreCount: number | null;
    threadCount: number | null;
    frequencyMhz: number | null;
  } | null;
  memory: {
    totalBytes: number | null;
  } | null;
}

// GET /api/client-agents
// Entity ClientAgent có thể trả kèm đối tượng "client" lồng bên trong,
// hoặc trả thẳng "clientId" tùy cấu hình Jackson, nên khai báo cả hai.
export interface ClientAgentInfo {
  agentId: string;
  client?: { clientId: string } | null;
  clientId?: string;
  version: string | null;
  status: AgentStatus;
  registeredAt: BackendDateTime;
  lastConnectedAt: BackendDateTime;
  lastHeartbeatAt: BackendDateTime;
  disconnectedAt: BackendDateTime;
}

// GET /api/alerts
export interface AlertInfo {
  alertId: number;
  clientId: string;
  ruleId: number;
  ruleName: string;
  resourceType: ResourceType;
  currentValue: number;
  thresholdValue: number;
  severity: AlertSeverity;
  status: AlertStatus;
  message: string;
  createdAt: BackendDateTime;
  acknowledgedAt: BackendDateTime;
  resolvedAt: BackendDateTime;
}

export interface DiskMetric {
  diskId: number;
  deviceName: string;
  mountPoint: string;
  fileSystem: string | null;
  totalBytes: number;
  usedBytes: number;
  freeBytes: number;
  usagePercent: number;
}

export interface NetworkMetric {
  interfaceId: number;
  interfaceName: string;
  ipAddress: string | null;
  macAddress: string | null;
  description: string | null;
  status: string;
  receivedBytes: number;
  transmittedBytes: number;
  downloadSpeed: number | null;
  uploadSpeed: number | null;
}

export interface ProcessMetric {
  pid: number;
  name: string;
  cpuUsage: number;
  memoryUsage: number;
  status: string;
}

// GET /api/monitoring/latest/{clientId}
export interface LatestMetrics {
  snapshotId: number;
  clientId: string;
  collectedAt: BackendDateTime;
  receivedAt: BackendDateTime;
  cpu: { usagePercent: number; loadAverage1m: number | null } | null;
  memory: {
    totalBytes: number;
    usedBytes: number;
    availableBytes: number;
    usagePercent: number;
  } | null;
  disks: DiskMetric[];
  networks: NetworkMetric[];
  processes: ProcessMetric[];
}

// GET /api/monitoring/history-chart/{clientId}
export interface HistoryPoint {
  snapshotId: number;
  collectedAt: BackendDateTime;
  cpuUsagePercent: number | null;
  memoryUsagePercent: number | null;
  diskUsagePercent: number | null;
  networkDownloadSpeed: number | null;
  networkUploadSpeed: number | null;
}

// ==========================================
// 6. API LẤY DỮ LIỆU THẬT
// ==========================================

// Danh sách máy (cần quyền CLIENT_VIEW)
export function getClients(): Promise<ClientInfo[]> {
  return request('/clients', 'Không thể tải danh sách máy.');
}

// Thông tin phần cứng tĩnh của 1 máy: CPU, RAM (cần quyền CLIENT_VIEW).
// Trả về null nếu không tìm thấy máy (backend trả 404).
export async function getClientHardware(clientId: string): Promise<ClientHardware | null> {
  try {
    return await request<ClientHardware>(
      `/clients/${encodeURIComponent(clientId)}/hardware`,
      'Không thể tải thông tin phần cứng.'
    );
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null;
    throw error;
  }
}

// Danh sách Agent (cần quyền AGENT_VIEW)
export function getClientAgents(): Promise<ClientAgentInfo[]> {
  return request('/client-agents', 'Không thể tải danh sách Agent.');
}

// Toàn bộ cảnh báo (cần quyền ALERT_VIEW)
export function getAlerts(): Promise<AlertInfo[]> {
  return request('/alerts', 'Không thể tải danh sách cảnh báo.');
}

// Số liệu mới nhất của 1 máy (cần quyền MONITORING_VIEW).
// Trả về null nếu máy chưa có dữ liệu nào (backend trả 404).
export async function getLatestMetrics(clientId: string): Promise<LatestMetrics | null> {
  try {
    return await request<LatestMetrics>(
      `/monitoring/latest/${encodeURIComponent(clientId)}`,
      'Không thể tải số liệu giám sát.'
    );
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null;
    throw error;
  }
}

// Lịch sử CPU/RAM/Disk/Network để vẽ biểu đồ (cần quyền HISTORY_VIEW).
// minutes: lấy bao nhiêu phút gần nhất (mặc định 30).
export function getMetricHistory(clientId: string, minutes: number = 30): Promise<HistoryPoint[]> {
  const end = new Date();
  const start = new Date(end.getTime() - minutes * 60_000);
  const params = new URLSearchParams({
    startTime: toLocalDateTimeParam(start),
    endTime: toLocalDateTimeParam(end),
  });

  return request(
    `/monitoring/history-chart/${encodeURIComponent(clientId)}?${params.toString()}`,
    'Không thể tải lịch sử giám sát.'
  );
}

// Xác nhận đã xem một cảnh báo (cần quyền ALERT_MANAGE)
export function acknowledgeAlert(alertId: number): Promise<AlertInfo> {
  return request(`/alerts/${alertId}/acknowledge`, 'Không thể xác nhận cảnh báo.', {
    method: 'PUT',
  });
}

// Cảnh báo mới nhất (mới nhất lên đầu), server sắp xếp và giới hạn số lượng (GET /api/alerts/recent).
export async function getRecentAlerts(limit: number = 10): Promise<AlertInfo[]> {
  return request(
    `/alerts/recent?limit=${encodeURIComponent(String(limit))}`,
    'Không thể tải cảnh báo mới nhất.'
  );
}

// Cảnh báo của MỘT máy, mới nhất lên đầu, giới hạn `limit` (GET /api/alerts/client/{clientId}).
export function getAlertsByClient(clientId: string, limit: number = 100): Promise<AlertInfo[]> {
  return request(
    `/alerts/client/${encodeURIComponent(clientId)}?limit=${encodeURIComponent(String(limit))}`,
    'Không thể tải cảnh báo của máy.'
  );
}

// Số lượng cảnh báo theo trạng thái / mức độ, không phải tải danh sách (GET /api/alerts/summary).
export interface AlertSummary {
  total: number;
  active: number;
  acknowledged: number;
  resolved: number;
  // Chưa xử lý xong (ACTIVE + ACKNOWLEDGED)
  open: number;
  critical: number;
  warning: number;
}

export function getAlertSummary(): Promise<AlertSummary> {
  return request('/alerts/summary', 'Không thể tải thống kê cảnh báo.');
}

// Một máy theo ID (GET /api/clients/{clientId}), thay cho việc tải cả danh sách rồi tìm.
export function getClient(clientId: string): Promise<ClientInfo> {
  return request(`/clients/${encodeURIComponent(clientId)}`, 'Không thể tải thông tin máy.');
}

// Agent của một máy (GET /api/client-agents/client/{clientId}).
export function getClientAgentByClientId(clientId: string): Promise<ClientAgentInfo> {
  return request(
    `/client-agents/client/${encodeURIComponent(clientId)}`,
    'Không thể tải thông tin Agent.'
  );
}

// ==========================================
// 6a. DANH SÁCH CÓ PHÂN TRANG
// ==========================================

// Kết quả phân trang của server. `page` bắt đầu từ 0, `size` tối đa 100.
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

function buildQuery(params: Record<string, string | number | null | undefined>): string {
  const parts: string[] = [];
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') continue;
    parts.push(`${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`);
  }
  return parts.length > 0 ? `?${parts.join('&')}` : '';
}

// GET /api/clients/page  (lọc trạng thái, tìm theo hostname / IP / clientId)
export function getClientsPage(options: {
  page?: number;
  size?: number;
  status?: ClientStatus;
  q?: string;
} = {}): Promise<PageResponse<ClientInfo>> {
  return request(
    `/clients/page${buildQuery(options)}`,
    'Không thể tải danh sách máy.'
  );
}

// GET /api/client-agents/page
export function getClientAgentsPage(options: {
  page?: number;
  size?: number;
  status?: AgentStatus;
} = {}): Promise<PageResponse<ClientAgentInfo>> {
  return request(
    `/client-agents/page${buildQuery(options)}`,
    'Không thể tải danh sách Agent.'
  );
}

// GET /api/alerts/page  (lọc trạng thái, mức độ, máy)
export function getAlertsPage(options: {
  page?: number;
  size?: number;
  status?: AlertStatus;
  severity?: AlertSeverity;
  clientId?: string;
} = {}): Promise<PageResponse<AlertInfo>> {
  return request(
    `/alerts/page${buildQuery(options)}`,
    'Không thể tải danh sách cảnh báo.'
  );
}

// ==========================================
// 6b. THAO TÁC QUẢN LÝ MÁY / AGENT
// ==========================================

// Duyệt Agent đang chờ (cần quyền AGENT_MANAGE)
export function approveAgent(agentId: string): Promise<string> {
  return request(
    `/client-agents/${encodeURIComponent(agentId)}/approve`,
    'Không thể duyệt Agent.',
    { method: 'POST' }
  );
}

// Từ chối Agent đang chờ (cần quyền AGENT_MANAGE)
export function rejectAgent(agentId: string): Promise<string> {
  return request(
    `/client-agents/${encodeURIComponent(agentId)}/reject`,
    'Không thể từ chối Agent.',
    { method: 'POST' }
  );
}

// Thu hồi Agent đã duyệt: Credential bị vô hiệu vĩnh viễn, Agent chuyển
// sang DISABLED và phải đăng ký lại (cần quyền AGENT_MANAGE)
export function revokeAgent(agentId: string): Promise<string> {
  return request(
    `/client-agents/${encodeURIComponent(agentId)}/revoke`,
    'Không thể thu hồi Agent.',
    { method: 'POST' }
  );
}

// Xóa Agent cùng máy (Client) liên kết và toàn bộ dữ liệu liên quan:
// lịch sử giám sát, phần cứng, cảnh báo, lệnh từ xa, phiên chia sẻ màn hình.
// Áp dụng cho Agent ở mọi trạng thái, KHÔNG thể hoàn tác (cần quyền AGENT_MANAGE).
// DELETE /api/client-agents/{agentId}
export function deleteAgent(agentId: string): Promise<string> {
  return request(
    `/client-agents/${encodeURIComponent(agentId)}`,
    'Không thể xóa Agent.',
    { method: 'DELETE' }
  );
}

// Xóa máy tính khỏi hệ thống (cần quyền CLIENT_MANAGE).
// Máy còn Agent liên kết sẽ bị từ chối: hãy dùng deleteAgent để xóa Agent cùng máy.
export function deleteClient(clientId: string): Promise<string> {
  return request(
    `/clients/${encodeURIComponent(clientId)}`,
    'Không thể xóa máy tính.',
    { method: 'DELETE' }
  );
}

// ==========================================
// 7. DỮ LIỆU TỔNG HỢP CHO DASHBOARD
// ==========================================

export interface DashboardStats {
  totalClients: number;
  onlineClients: number;
  offlineClients: number;
  pendingClients: number;
  // Tỷ lệ máy đang hoạt động (0-100)
  onlineRate: number;
  // Cảnh báo chưa được giải quyết (ACTIVE + ACKNOWLEDGED)
  openAlerts: number;
  activeAlerts: number;
  criticalAlerts: number;
  warningAlerts: number;
  // Trung bình tải của các máy đang ONLINE (null nếu chưa có dữ liệu)
  avgCpu: number | null;
  avgMemory: number | null;
  avgDisk: number | null;
}

export interface DashboardSnapshot {
  stats: DashboardStats;
  clients: ClientInfo[];
  // Cảnh báo mới nhất lên đầu (đã giới hạn số lượng)
  recentAlerts: AlertInfo[];
  // Số liệu mới nhất theo clientId (chỉ gồm các máy ONLINE có dữ liệu)
  latestByClient: Record<string, LatestMetrics>;
  updatedAt: Date;
  // Phần nào bị lỗi/không đủ quyền (để giao diện hiển thị cảnh báo thay vì trống im lặng)
  errors: { clients?: string; alerts?: string; metrics?: string };
}

function describeError(reason: unknown): string {
  if (reason instanceof ApiError && reason.status === 403) {
    return 'Tài khoản không có quyền xem dữ liệu này.';
  }
  return reason instanceof Error ? reason.message : 'Đã xảy ra lỗi không xác định.';
}

// Lấy toàn bộ dữ liệu Dashboard trong MỘT request (GET /api/dashboard/summary).
// Server tự gom danh sách máy, thống kê cảnh báo, cảnh báo mới nhất và số liệu
// CPU/RAM/ổ đĩa của các máy ONLINE, nên số request không còn tăng theo số máy.
// Phần nào thiếu quyền (ALERT_VIEW, MONITORING_VIEW) sẽ nằm trong `errors`.
interface DashboardSummaryDto {
  stats: DashboardStats;
  clients: ClientInfo[];
  recentAlerts: AlertInfo[];
  latestByClient: Record<string, LatestMetrics> | null;
  errors: { clients?: string; alerts?: string; metrics?: string } | null;
}

export async function getDashboardSnapshot(recentAlertLimit: number = 10): Promise<DashboardSnapshot> {
  const dto = await request<DashboardSummaryDto>(
    `/dashboard/summary?recentAlerts=${encodeURIComponent(String(recentAlertLimit))}`,
    'Không thể tải dữ liệu Dashboard.'
  );

  return {
    stats: dto.stats,
    clients: dto.clients ?? [],
    recentAlerts: dto.recentAlerts ?? [],
    latestByClient: dto.latestByClient ?? {},
    updatedAt: new Date(),
    errors: dto.errors ?? {},
  };
}

// Giữ lại hàm cũ để code Dashboard hiện tại vẫn chạy được.
export async function getDashboardStats(): Promise<DashboardStats> {
  return (await getDashboardSnapshot()).stats;
}

// ==========================================
// 7b. DỮ LIỆU CHO TRANG QUẢN LÝ MÁY TÍNH
// ==========================================

// Trạng thái hiển thị của một máy trên giao diện
export type ComputerStatus = 'online' | 'warning' | 'offline' | 'pending' | 'disabled';

export interface ComputerRow {
  clientId: string;
  hostname: string;
  ipAddress: string;
  os: string;
  status: ComputerStatus;
  rawStatus: ClientStatus;
  bootTime: Date | null;
  lastSeenAt: Date | null;
  agentId: string | null;
  agentVersion: string | null;
  agentStatus: AgentStatus | null;
  // Số cảnh báo chưa xử lý xong (ACTIVE + ACKNOWLEDGED) của máy này
  openAlerts: number;
  managementNote: string | null;
}

export interface ComputersStats {
  total: number;
  online: number;
  // Máy có cảnh báo hoặc mất kết nối
  needAttention: number;
  pending: number;
  // Tỷ lệ máy đang hoạt động (0-100)
  onlineRate: number;
}

export interface ComputersSnapshot {
  computers: ComputerRow[];
  stats: ComputersStats;
  updatedAt: Date;
  // Phần nào bị lỗi/không đủ quyền
  errors: { clients?: string; agents?: string; alerts?: string };
}

function resolveComputerStatus(raw: ClientStatus, openAlerts: number): ComputerStatus {
  switch (raw) {
    case 'ONLINE':
      return openAlerts > 0 ? 'warning' : 'online';
    case 'PENDING_APPROVAL':
      return 'pending';
    case 'DISABLED':
      return 'disabled';
    default:
      // OFFLINE, UNKNOWN
      return 'offline';
  }
}

// Lấy danh sách máy kèm thông tin Agent và số cảnh báo, ghép theo clientId.
// Danh sách máy là bắt buộc; Agent và cảnh báo lỗi (thiếu quyền...) thì vẫn hiển thị
// được danh sách máy, chỉ thiếu phần tương ứng.
export async function getComputersSnapshot(): Promise<ComputersSnapshot> {
  const [clientsResult, agentsResult, alertsResult] = await Promise.allSettled([
    getClients(),
    getClientAgents(),
    getAlerts(),
  ]);

  // Không có danh sách máy thì không có gì để hiển thị
  if (clientsResult.status === 'rejected') {
    throw clientsResult.reason;
  }

  const errors: ComputersSnapshot['errors'] = {};
  const clients = clientsResult.value;

  const agents = agentsResult.status === 'fulfilled' ? agentsResult.value : [];
  if (agentsResult.status === 'rejected') errors.agents = describeError(agentsResult.reason);

  const alerts = alertsResult.status === 'fulfilled' ? alertsResult.value : [];
  if (alertsResult.status === 'rejected') errors.alerts = describeError(alertsResult.reason);

  // clientId -> Agent (nếu một máy có nhiều Agent thì ưu tiên Agent ONLINE, rồi mới nhất)
  const agentByClient = new Map<string, ClientAgentInfo>();
  const agentRank = (a: ClientAgentInfo) =>
    (a.status === 'ONLINE' ? 2 : 0) + ((toDate(a.registeredAt)?.getTime() ?? 0) / 1e15);
  for (const agent of agents) {
    const cid = agent.client?.clientId ?? agent.clientId;
    if (!cid) continue;
    const existing = agentByClient.get(cid);
    if (!existing || agentRank(agent) > agentRank(existing)) {
      agentByClient.set(cid, agent);
    }
  }

  // clientId -> số cảnh báo chưa xử lý xong
  const openAlertsByClient = new Map<string, number>();
  for (const alert of alerts) {
    if (alert.status === 'RESOLVED') continue;
    openAlertsByClient.set(alert.clientId, (openAlertsByClient.get(alert.clientId) ?? 0) + 1);
  }

  const computers: ComputerRow[] = clients.map((c) => {
    const agent = agentByClient.get(c.clientId);
    const openAlerts = openAlertsByClient.get(c.clientId) ?? 0;
    const osText = [c.osName, c.osVersion].filter(Boolean).join(' ');

    return {
      clientId: c.clientId,
      hostname: c.hostname || c.clientId,
      ipAddress: c.ipAddress || '—',
      os: osText || '—',
      status: resolveComputerStatus(c.status, openAlerts),
      rawStatus: c.status,
      bootTime: toDate(c.bootTime),
      lastSeenAt: toDate(c.lastSeenAt),
      agentId: agent?.agentId ?? null,
      agentVersion: agent?.version ?? null,
      agentStatus: agent?.status ?? null,
      openAlerts,
      managementNote: c.managementNote ?? null,
    };
  });

  const total = computers.length;
  const online = computers.filter((c) => c.status === 'online' || c.status === 'warning').length;

  const stats: ComputersStats = {
    total,
    online: computers.filter((c) => c.status === 'online').length,
    needAttention: computers.filter((c) => c.status === 'warning' || c.status === 'offline').length,
    pending: computers.filter((c) => c.status === 'pending').length,
    onlineRate: total === 0 ? 0 : Math.round((online / total) * 1000) / 10,
  };

  return { computers, stats, updatedAt: new Date(), errors };
}

// ==========================================
// 8. TỰ ĐỘNG CẬP NHẬT (POLLING)
// ==========================================

// Chạy `task` ngay lập tức rồi lặp lại mỗi `intervalMs`.
// - Không chạy chồng: lần trước chưa xong thì bỏ qua lần này.
// - Tạm dừng khi tab bị ẩn, quay lại tab thì tải ngay.
// Trả về hàm dừng để gọi khi component unmount.
export function startPolling(task: () => Promise<void>, intervalMs: number): () => void {
  let stopped = false;
  let running = false;

  const tick = async () => {
    if (stopped || running || document.hidden) return;
    running = true;
    try {
      await task();
    } finally {
      running = false;
    }
  };

  const onVisibilityChange = () => {
    if (!document.hidden) void tick();
  };

  void tick();
  const timer = window.setInterval(() => void tick(), intervalMs);
  document.addEventListener('visibilitychange', onVisibilityChange);

  return () => {
    stopped = true;
    window.clearInterval(timer);
    document.removeEventListener('visibilitychange', onVisibilityChange);
  };
}

// Đăng ký nhận dữ liệu Dashboard, tự làm mới mỗi `intervalMs` (mặc định 5 giây).
// Trả về hàm hủy đăng ký.
//
// Ví dụ trong React:
//   useEffect(() => {
//     return subscribeDashboard(
//       (snapshot) => setData(snapshot),
//       (err) => setError(err.message),
//     );
//   }, []);
export function subscribeDashboard(
  onData: (snapshot: DashboardSnapshot) => void,
  onError?: (error: Error) => void,
  intervalMs: number = 5000,
  recentAlertLimit: number = 10
): () => void {
  return startPolling(async () => {
    try {
      onData(await getDashboardSnapshot(recentAlertLimit));
    } catch (error) {
      onError?.(error instanceof Error ? error : new Error(String(error)));
    }
  }, intervalMs);
}

// Đăng ký nhận danh sách máy tính, tự làm mới (mặc định 10 giây).
export function subscribeComputers(
  onData: (snapshot: ComputersSnapshot) => void,
  onError?: (error: Error) => void,
  intervalMs: number = 10_000
): () => void {
  return startPolling(async () => {
    try {
      onData(await getComputersSnapshot());
    } catch (error) {
      onError?.(error instanceof Error ? error : new Error(String(error)));
    }
  }, intervalMs);
}

// Đăng ký nhận biểu đồ lịch sử của một máy, tự làm mới (mặc định 10 giây).
export function subscribeMetricHistory(
  clientId: string,
  onData: (points: HistoryPoint[]) => void,
  onError?: (error: Error) => void,
  minutes: number = 30,
  intervalMs: number = 10_000
): () => void {
  return startPolling(async () => {
    try {
      onData(await getMetricHistory(clientId, minutes));
    } catch (error) {
      onError?.(error instanceof Error ? error : new Error(String(error)));
    }
  }, intervalMs);
}

// ==========================================
// 9. API CHIA SẺ MÀN HÌNH TỪ XA
// ==========================================

/**
 * Trạng thái phiên chia sẻ màn hình.
 * Khớp với ScreenSharingSessionStatus trong Backend.
 */
export type ScreenSharingStatus =
  | 'REQUESTED'
  | 'STARTING'
  | 'ACTIVE'
  | 'STOPPING'
  | 'ENDED'
  | 'FAILED';

/**
 * Dữ liệu phiên chia sẻ màn hình từ Backend.
 *
 * Backend DTO: ScreenSharingSessionResponse
 */
export interface ScreenSharingSession {
  sessionId: number;
  sessionCode: string;

  clientId: string;
  agentId: string;

  status: ScreenSharingStatus;

  commandId: number | null;
  message: string | null;
}

/**
 * POST /api/screen-sharing/sessions
 *
 * Tạo yêu cầu chia sẻ màn hình cho một máy tính.
 *
 * Backend yêu cầu quyền REMOTE_COMMAND.
 *
 * Request body:
 * {
 *   "clientId": "..."
 * }
 */
export async function startScreenSharing(
  clientId: string
): Promise<ScreenSharingSession> {
  return request<ScreenSharingSession>(
    '/screen-sharing/sessions',
    'Không thể bắt đầu phiên chia sẻ màn hình.',
    {
      method: 'POST',
      body: JSON.stringify({
        clientId,
      }),
    }
  );
}

/**
 * POST /api/screen-sharing/sessions/{sessionCode}/stop
 *
 * Yêu cầu dừng phiên chia sẻ màn hình.
 *
 * Backend yêu cầu quyền REMOTE_COMMAND.
 */
export async function stopScreenSharing(
  sessionCode: string
): Promise<ScreenSharingSession> {
  return request<ScreenSharingSession>(
    `/screen-sharing/sessions/${encodeURIComponent(sessionCode)}/stop`,
    'Không thể dừng phiên chia sẻ màn hình.',
    {
      method: 'POST',
    }
  );
}

/**
 * GET /api/screen-sharing/sessions/current
 *
 * Lấy phiên chia sẻ màn hình hiện tại của Admin đang đăng nhập.
 *
 * Backend trả về:
 * - 200: Có phiên hiện tại.
 * - 204: Không có phiên hiện tại.
 */
export async function getCurrentScreenSharingSession():
  Promise<ScreenSharingSession | null> {
  const session = await request<ScreenSharingSession | null>(
    '/screen-sharing/sessions/current',
    'Không thể tải phiên chia sẻ màn hình hiện tại.'
  );

  return session ?? null;
}

/**
 * GET /api/screen-sharing/sessions/history
 *
 * Lấy lịch sử các phiên chia sẻ màn hình.
 */
export async function getScreenSharingSessionHistory():
  Promise<ScreenSharingSession[]> {
  return request<ScreenSharingSession[]>(
    '/screen-sharing/sessions/history',
    'Không thể tải lịch sử chia sẻ màn hình.'
  );
}

// ==========================================
// 10. WEBSOCKET NHẬN HÌNH ẢNH MÀN HÌNH
// ==========================================

/**
 * Tạo địa chỉ WebSocket kết nối đến Backend.
 *
 * Backend:
 * /ws/screen
 *
 * API_BASE_URL mặc định:
 * http://localhost:8080/api
 *
 * Kết quả:
 * ws://localhost:8080/ws/screen
 *
 * Nếu frontend sử dụng HTTPS:
 * wss://...
 */
export function getScreenStreamUrl(): string {
  const backendUrl = new URL(API_BASE_URL);

  const protocol = backendUrl.protocol === 'https:' ? 'wss:' : 'ws:';

  // Bỏ /api (và dấu / thừa) ở cuối path, rồi ghép chuỗi thủ công
  const basePath = backendUrl.pathname
    .replace(/\/api\/?$/, '')
    .replace(/\/+$/, '');

  return `${protocol}//${backendUrl.host}${basePath}/ws/screen`;
}

// ==========================================
// 11. REMOTE COMMANDS - QUẢN TRỊ MÁY TỪ XA
// Khớp với RemoteCommandController trong Backend N1
// ==========================================

export type RemoteCommandType =
  | 'RESTART'
  | 'SHUTDOWN'
  | 'KILL_PROCESS';

export type RemoteCommandStatus =
  | 'PENDING'
  | 'SENT'
  | 'RUNNING'
  | 'COMPLETED'
  | 'FAILED'
  | 'TIMEOUT';

export interface RemoteCommandInfo {
  commandId: number;
  clientId: string;
  commandType: RemoteCommandType;
  status: RemoteCommandStatus;
  parameters: string | null;
  result: string | null;
  errorMessage: string | null;
  requestedBy: string | null;
  createdAt: BackendDateTime;
  sentAt: BackendDateTime;
  startedAt: BackendDateTime;
  completedAt: BackendDateTime;
}

export interface CreateRemoteCommandResponse extends RemoteCommandInfo {
  message?: string;
}

/**
 * GET /api/remote/commands
 * Lấy lịch sử lệnh có phân trang và bộ lọc.
 */
export function getRemoteCommands(options: {
  page?: number;
  size?: number;
  clientId?: string;
  status?: RemoteCommandStatus;
  commandType?: RemoteCommandType;
} = {}): Promise<PageResponse<RemoteCommandInfo>> {
  return request(
    `/remote/commands${buildQuery(options)}`,
    'Không thể tải lịch sử lệnh quản trị.'
  );
}

/**
 * GET /api/remote/commands/{commandId}
 * Lấy chi tiết một lệnh.
 */
export function getRemoteCommand(
  commandId: number
): Promise<RemoteCommandInfo> {
  return request(
    `/remote/commands/${encodeURIComponent(String(commandId))}`,
    'Không thể tải chi tiết lệnh.'
  );
}

/**
 * POST /api/remote/commands
 * Gửi lệnh quản trị đến Client Agent.
 *
 * RESTART / SHUTDOWN: không cần parameters.
 * KILL_PROCESS: bắt buộc parameters.pid là số nguyên > 0.
 */
export function createRemoteCommand(
  clientId: string,
  commandType: RemoteCommandType,
  parameters?: { pid: number }
): Promise<CreateRemoteCommandResponse> {
  return request(
    '/remote/commands',
    'Không thể gửi lệnh quản trị.',
    {
      method: 'POST',
      body: JSON.stringify({
        clientId,
        commandType,
        ...(parameters ? { parameters } : {}),
      }),
    }
  );
}

// ==========================================
// 12. QUẢN LÝ TÀI KHOẢN (ADMIN)
// Khớp với AccountController trong Backend
// ==========================================

export type AccountStatus = 'PENDING' | 'ACTIVE' | 'INACTIVE' | 'LOCKED';
export type AccountRole = 'ADMIN' | 'MONITOR';

export interface AccountInfo {
  accountId: number;
  username: string;
  email: string;
  role: AccountRole;
  status: AccountStatus;
  emailVerified: boolean;
  createAt: BackendDateTime;
  updateAt: BackendDateTime;
  lastLoginAt: BackendDateTime;
}

// Body của POST /api/accounts/admin
export interface CreateAccountPayload {
  username: string;
  email: string;
  password: string;
  roleName: AccountRole;
}

// Body của PUT /api/accounts/{id}
// password bỏ trống (hoặc không gửi) thì giữ nguyên mật khẩu cũ.
export interface UpdateAccountPayload {
  username: string;
  email: string;
  roleName: AccountRole;
  password?: string;
}

// GET /api/accounts — quyền ACCOUNT_VIEW
export function getAccounts(): Promise<AccountInfo[]> {
  return request('/accounts', 'Không thể tải danh sách tài khoản.');
}

// POST /api/accounts/admin — chỉ ADMIN
// Tài khoản mới được kích hoạt ngay (ACTIVE, email đã xác thực), không cần OTP.
export function createAccount(payload: CreateAccountPayload): Promise<AccountInfo> {
  return request('/accounts/admin', 'Không thể tạo tài khoản.', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

// PUT /api/accounts/{id} — chỉ ADMIN
// Đổi email: email mới phải chưa ai dùng.
// Backend không cho ADMIN tự cập nhật chính mình.
export function updateAccount(accountId: number, payload: UpdateAccountPayload): Promise<AccountInfo> {
  const body: UpdateAccountPayload = { ...payload };
  if (!body.password) delete body.password;

  return request(`/accounts/${encodeURIComponent(String(accountId))}`, 'Không thể cập nhật tài khoản.', {
    method: 'PUT',
    body: JSON.stringify(body),
  });
}

// PATCH /api/accounts/{id}/role — chỉ ADMIN
export function updateAccountRole(accountId: number, roleName: AccountRole): Promise<AccountInfo> {
  return request(`/accounts/${encodeURIComponent(String(accountId))}/role`, 'Không thể đổi vai trò tài khoản.', {
    method: 'PATCH',
    body: JSON.stringify({ roleName }),
  });
}

// PATCH /api/accounts/{id}/lock — chỉ ADMIN
export function lockAccount(accountId: number): Promise<AccountInfo> {
  return request(`/accounts/${encodeURIComponent(String(accountId))}/lock`, 'Không thể khóa tài khoản.', {
    method: 'PATCH',
  });
}

// PATCH /api/accounts/{id}/unlock — chỉ ADMIN
export function unlockAccount(accountId: number): Promise<AccountInfo> {
  return request(`/accounts/${encodeURIComponent(String(accountId))}/unlock`, 'Không thể mở khóa tài khoản.', {
    method: 'PATCH',
  });
}

// DELETE /api/accounts/{id} — chỉ ADMIN
export function deleteAccount(accountId: number): Promise<{ message: string }> {
  return request(`/accounts/${encodeURIComponent(String(accountId))}`, 'Không thể xóa tài khoản.', {
    method: 'DELETE',
  });
}

// PATCH /api/accounts/me/password — người dùng đang đăng nhập
export function changeMyPassword(currentPassword: string, newPassword: string): Promise<{ message: string }> {
  return request('/accounts/me/password', 'Không thể đổi mật khẩu.', {
    method: 'PATCH',
    body: JSON.stringify({ currentPassword, newPassword }),
  });
}

// ==========================================
// 13. CẤU HÌNH HỆ THỐNG
// Khớp với SystemConfigController / SystemConfigService trong Backend.
// DÁN ĐOẠN NÀY VÀO CUỐI src/lib/api.ts (không cần import thêm gì).
// ==========================================

// GET /api/system-config  ->  SystemConfigResponse
// updatedBy / updatedAt là null khi cấu hình chưa từng được lưu
// (server đang dùng giá trị mặc định, chưa có dòng nào trong system_configs).
export interface SystemConfigInfo {
  collectionIntervalSeconds: number;
  heartbeatIntervalSeconds: number;
  heartbeatTimeoutSeconds: number;
  updatedBy: string | null;
  updatedAt: BackendDateTime;
}

// Body của PUT /api/system-config  ->  UpdateSystemConfigRequest
export interface UpdateSystemConfigPayload {
  collectionIntervalSeconds: number;
  heartbeatIntervalSeconds: number;
  heartbeatTimeoutSeconds: number;
}

// Phạm vi cho phép (giây). PHẢI trùng với SystemConfigService:
//   MIN_SECONDS = 1
//   MAX_COLLECTION_INTERVAL_SECONDS = 3600
//   MAX_HEARTBEAT_INTERVAL_SECONDS  = 600
//   MAX_HEARTBEAT_TIMEOUT_SECONDS   = 3600
// Ngoài ra server yêu cầu heartbeatTimeoutSeconds > heartbeatIntervalSeconds.
export const SYSTEM_CONFIG_LIMITS: Record<
  keyof UpdateSystemConfigPayload,
  { min: number; max: number }
> = {
  collectionIntervalSeconds: { min: 1, max: 3600 },
  heartbeatIntervalSeconds: { min: 1, max: 600 },
  heartbeatTimeoutSeconds: { min: 1, max: 3600 },
};

// Giá trị mặc định mà server dùng khi chưa có dòng cấu hình
// (DEFAULT_*_SECONDS trong SystemConfigService).
export const SYSTEM_CONFIG_DEFAULTS: UpdateSystemConfigPayload = {
  collectionIntervalSeconds: 10,
  heartbeatIntervalSeconds: 5,
  heartbeatTimeoutSeconds: 15,
};

// GET /api/system-config — cần quyền SYSTEM_CONFIG (không có quyền -> 403).
export function getSystemConfig(): Promise<SystemConfigInfo> {
  return request('/system-config', 'Không thể tải cấu hình hệ thống.');
}

// PUT /api/system-config — cần quyền SYSTEM_CONFIG.
// Dữ liệu sai trả 400 kèm {"message": "..."}; readErrorMessage đã đọc được
// nên thông báo cụ thể của server (ví dụ "Heartbeat Timeout phải lớn hơn
// Chu kỳ Heartbeat.") sẽ hiện nguyên văn trên giao diện.
export function updateSystemConfig(payload: UpdateSystemConfigPayload): Promise<SystemConfigInfo> {
  return request('/system-config', 'Không thể lưu cấu hình hệ thống.', {
    method: 'PUT',
    body: JSON.stringify(payload),
  });
}