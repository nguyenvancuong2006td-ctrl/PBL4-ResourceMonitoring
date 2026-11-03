import { useCallback, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  Activity,
  Bell,
  ChevronLeft,
  ChevronRight,
  Clock,
  Cpu,
  Download,
  Eye,
  Filter,
  HardDrive,
  History,
  LayoutDashboard,
  Loader2,
  Monitor,
  RefreshCw,
  Search,
  ShieldAlert,
  Terminal,
  WifiOff,
} from 'lucide-react';
import {
  Area,
  AreaChart,
  Brush,
  CartesianGrid,
  Line,
  LineChart,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import {
  API_BASE_URL,
  ApiError,
  UNAUTHORIZED_EVENT,
  getAlertsByClient,
  getClients,
  getMetricHistory,
  getSession,
  hasPermission,
  startPolling,
  toDate,
} from '../../lib/api';
import type {
  AlertInfo,
  AlertSeverity,
  AlertStatus,
  BackendDateTime,
  ClientInfo,
  HistoryPoint,
  ResourceType,
} from '../../lib/api';
import { useTheme } from '../../context/ThemeContext';

// ============================================================
// HẰNG SỐ
// ============================================================

const PAGE_SIZE = 8;
const MAX_CHART_POINTS = 200;
const MAX_ALERT_MARKERS = 30;

// Khoảng thời gian xem lại. Mỗi mẫu đều được server đọc từ nhiều bảng,
// nên chỉ cho chọn tối đa 24 giờ để trang tải nhanh.
const RANGES = [
  { key: '30m', label: '30 phút', minutes: 30 },
  { key: '1h', label: '1 giờ', minutes: 60 },
  { key: '6h', label: '6 giờ', minutes: 360 },
  { key: '24h', label: '24 giờ', minutes: 1440 },
] as const;

type RangeKey = (typeof RANGES)[number]['key'];
type MetricKey = 'cpu' | 'ram' | 'disk';

const METRICS: { key: MetricKey; label: string; color: string }[] = [
  { key: 'cpu', label: 'CPU', color: '#22d3ee' },
  { key: 'ram', label: 'RAM', color: '#a78bfa' },
  { key: 'disk', label: 'Disk', color: '#fbbf24' },
];

// Các mức tải dùng cho thanh phân bố thời gian
const LEVELS = [
  { label: 'Thấp', hint: '< 50%', color: '#22d3ee', max: 50 },
  { label: 'Vừa', hint: '50–75%', color: '#3b82f6', max: 75 },
  { label: 'Cao', hint: '75–90%', color: '#f59e0b', max: 90 },
  { label: 'Nguy hiểm', hint: '≥ 90%', color: '#ef4444', max: Infinity },
];

const SEVERITY_STYLE: Record<AlertSeverity, { label: string; color: string; className: string }> = {
  CRITICAL: { label: 'Nghiêm trọng', color: '#ef4444', className: 'bg-red-500/15 text-red-400 border-red-500/40' },
  WARNING: { label: 'Cảnh báo', color: '#f59e0b', className: 'bg-amber-500/15 text-amber-400 border-amber-500/40' },
  INFO: { label: 'Thông tin', color: '#38bdf8', className: 'bg-sky-500/15 text-sky-400 border-sky-500/40' },
};

const ALERT_STATUS_STYLE: Record<AlertStatus, { label: string; className: string; dot: string }> = {
  ACTIVE: { label: 'Chờ xử lý', className: 'bg-amber-500/15 text-amber-400 border-amber-500/40', dot: 'bg-amber-400' },
  ACKNOWLEDGED: { label: 'Đã ghi nhận', className: 'bg-sky-500/15 text-sky-400 border-sky-500/40', dot: 'bg-sky-400' },
  RESOLVED: { label: 'Đã giải quyết', className: 'bg-emerald-500/15 text-emerald-400 border-emerald-500/40', dot: 'bg-emerald-400' },
};

const COMMAND_LABEL: Record<string, string> = {
  RESTART: 'Khởi động lại máy',
  SHUTDOWN: 'Tắt máy',
  KILL_PROCESS: 'Kết thúc tiến trình',
  START_SCREEN_SHARING: 'Bắt đầu chia sẻ màn hình',
  STOP_SCREEN_SHARING: 'Dừng chia sẻ màn hình',
};

const COMMAND_STATUS_STYLE: Record<string, { label: string; className: string; dot: string }> = {
  PENDING: { label: 'Đang chờ', className: 'bg-amber-500/15 text-amber-400 border-amber-500/40', dot: 'bg-amber-400' },
  SENT: { label: 'Đã gửi', className: 'bg-amber-500/15 text-amber-400 border-amber-500/40', dot: 'bg-amber-400' },
  RUNNING: { label: 'Đang chạy', className: 'bg-sky-500/15 text-sky-400 border-sky-500/40', dot: 'bg-sky-400' },
  COMPLETED: { label: 'Hoàn tất', className: 'bg-emerald-500/15 text-emerald-400 border-emerald-500/40', dot: 'bg-emerald-400' },
  FAILED: { label: 'Thất bại', className: 'bg-red-500/15 text-red-400 border-red-500/40', dot: 'bg-red-400' },
};

const RESOURCE_LABEL: Record<ResourceType, string> = {
  CPU: 'CPU',
  MEMORY: 'RAM',
  DISK: 'Disk',
  NETWORK: 'Mạng',
  PROCESS: 'Tiến trình',
};

const PERCENT_RESOURCES: ResourceType[] = ['CPU', 'MEMORY', 'DISK'];

// ============================================================
// KIỂU DỮ LIỆU
// ============================================================

interface Sample {
  ts: number;
  cpu: number | null;
  ram: number | null;
  disk: number | null;
  down: number | null;
  up: number | null;
}

type SampleKey = 'cpu' | 'ram' | 'disk' | 'down' | 'up';

interface Stat {
  avg: number | null;
  min: number | null;
  max: number | null;
  p95: number | null;
  peakAt: number | null;
}

interface Gap {
  from: number;
  to: number;
  ongoing: boolean;
}

// Dữ liệu trả về của GET /api/remote/commands/client/{clientId}
interface CommandInfo {
  commandId: number;
  clientId: string | null;
  commandType: string | null;
  status: string | null;
  parameters: string | null;
  result: string | null;
  errorMessage: string | null;
  sentAt: BackendDateTime;
  startedAt: BackendDateTime;
  completedAt: BackendDateTime;
}

interface Bundle {
  key: string;
  samples: Sample[];
  alerts: AlertInfo[];
  commands: CommandInfo[];
  warnings: string[];
  fetchedAt: Date;
}

interface EventRow {
  key: string;
  kind: 'alert' | 'command';
  time: Date;
  title: string;
  detail: string;
  durationMs: number | null;
  open: boolean;
  statusLabel: string;
  statusClass: string;
  statusDot: string;
  accent: string;
}

// ============================================================
// HÀM TIỆN ÍCH
// ============================================================

const pad = (n: number) => String(n).padStart(2, '0');

// "20:45:20 01/11"
function formatDateTime(date: Date | null): string {
  if (!date) return '—';
  return `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())} ${pad(date.getDate())}/${pad(date.getMonth() + 1)}`;
}

// "20:45:20 01/11/2026"
function formatFull(date: Date): string {
  return `${formatDateTime(date)}/${date.getFullYear()}`;
}

function formatClock(ts: number | null): string {
  if (ts === null) return '—';
  const d = new Date(ts);
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

function formatDuration(ms: number): string {
  const totalSeconds = Math.max(0, Math.round(ms / 1000));
  if (totalSeconds < 1) return '< 1 giây';
  if (totalSeconds < 60) return `${totalSeconds} giây`;
  const minutes = Math.floor(totalSeconds / 60);
  if (minutes < 60) return `${minutes} phút ${pad(totalSeconds % 60)} giây`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} giờ ${minutes % 60} phút`;
  return `${Math.floor(hours / 24)} ngày ${hours % 24} giờ`;
}

function fmtPct(value: number | null | undefined): string {
  return typeof value === 'number' ? `${value.toFixed(1)}%` : '--';
}

function fmtBytes(value: number | null | undefined): string {
  if (typeof value !== 'number' || !Number.isFinite(value)) return '--';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let size = Math.max(0, value);
  let i = 0;
  while (size >= 1024 && i < units.length - 1) {
    size /= 1024;
    i += 1;
  }
  return `${size.toFixed(i === 0 ? 0 : 1)} ${units[i]}`;
}

// Rút gọn các số thập phân dài trong nội dung (84.4290932 -> 84.4)
function cleanText(text: string): string {
  return text.replace(/\d+\.\d{3,}/g, (m) => Number(m).toFixed(1));
}

function describeError(reason: unknown, fallback: string): string {
  if (reason instanceof ApiError && reason.status === 403) {
    return 'Tài khoản không có quyền xem lịch sử giám sát (HISTORY_VIEW).';
  }
  return reason instanceof Error ? reason.message : fallback;
}

// Nhãn trục thời gian: khoảng dài thì kèm ngày
function axisLabel(ts: number, minutes: number): string {
  const d = new Date(ts);
  const clock = `${pad(d.getHours())}:${pad(d.getMinutes())}`;
  if (minutes >= 1440) return `${pad(d.getDate())}/${pad(d.getMonth() + 1)} ${clock}`;
  if (minutes <= 60) return `${clock}:${pad(d.getSeconds())}`;
  return clock;
}

function averageOf(values: (number | null)[]): number | null {
  const nums = values.filter((v): v is number => typeof v === 'number');
  if (nums.length === 0) return null;
  return nums.reduce((a, b) => a + b, 0) / nums.length;
}

// Chuyển dữ liệu backend (mới nhất lên đầu) thành mốc thời gian tăng dần
function toSamples(points: HistoryPoint[]): Sample[] {
  const result: Sample[] = [];
  for (const p of points) {
    const date = toDate(p.collectedAt);
    if (!date) continue;
    result.push({
      ts: date.getTime(),
      cpu: p.cpuUsagePercent,
      ram: p.memoryUsagePercent,
      disk: p.diskUsagePercent,
      down: p.networkDownloadSpeed,
      up: p.networkUploadSpeed,
    });
  }
  return result.sort((a, b) => a.ts - b.ts);
}

// Gộp các mẫu thành tối đa `maxPoints` điểm bằng cách lấy trung bình,
// để biểu đồ 24 giờ vẫn mượt và nhẹ.
function downsample(samples: Sample[], maxPoints: number): Sample[] {
  if (samples.length <= maxPoints) return samples;
  const size = Math.ceil(samples.length / maxPoints);
  const result: Sample[] = [];
  for (let i = 0; i < samples.length; i += size) {
    const bucket = samples.slice(i, i + size);
    result.push({
      ts: Math.round(bucket.reduce((sum, s) => sum + s.ts, 0) / bucket.length),
      cpu: averageOf(bucket.map((s) => s.cpu)),
      ram: averageOf(bucket.map((s) => s.ram)),
      disk: averageOf(bucket.map((s) => s.disk)),
      down: averageOf(bucket.map((s) => s.down)),
      up: averageOf(bucket.map((s) => s.up)),
    });
  }
  return result;
}

// Chèn điểm rỗng vào chỗ mất dữ liệu để đường biểu đồ bị ngắt thay vì nối liền
function withGapBreaks(samples: Sample[], thresholdMs: number): Sample[] {
  const result: Sample[] = [];
  samples.forEach((sample, index) => {
    const prev = samples[index - 1];
    if (prev && sample.ts - prev.ts > thresholdMs) {
      result.push({ ts: prev.ts + 1, cpu: null, ram: null, disk: null, down: null, up: null });
    }
    result.push(sample);
  });
  return result;
}

function computeStat(samples: Sample[], key: SampleKey): Stat {
  const values: { ts: number; v: number }[] = [];
  for (const s of samples) {
    const v = s[key];
    if (typeof v === 'number') values.push({ ts: s.ts, v });
  }
  if (values.length === 0) return { avg: null, min: null, max: null, p95: null, peakAt: null };

  const sorted = values.map((x) => x.v).sort((a, b) => a - b);
  const sum = sorted.reduce((a, b) => a + b, 0);
  const peak = values.reduce((best, cur) => (cur.v > best.v ? cur : best), values[0]);

  return {
    avg: sum / sorted.length,
    min: sorted[0],
    max: sorted[sorted.length - 1],
    p95: sorted[Math.floor(0.95 * (sorted.length - 1))],
    peakAt: peak.ts,
  };
}

// Tỷ lệ thời gian máy nằm ở từng mức tải (tổng = 100%)
function distribution(samples: Sample[], key: MetricKey): number[] {
  const counts = LEVELS.map(() => 0);
  let total = 0;
  for (const s of samples) {
    const v = s[key];
    if (typeof v !== 'number') continue;
    counts[LEVELS.findIndex((l) => v < l.max)] += 1;
    total += 1;
  }
  return counts.map((c) => (total === 0 ? 0 : (c / total) * 100));
}

// Tìm các khoảng agent không gửi dữ liệu (máy tắt, mất mạng, agent dừng)
function findGaps(samples: Sample[], endMs: number): { gaps: Gap[]; threshold: number } {
  if (samples.length < 2) return { gaps: [], threshold: 60_000 };

  const diffs: number[] = [];
  for (let i = 1; i < samples.length; i++) diffs.push(samples[i].ts - samples[i - 1].ts);
  const sorted = [...diffs].sort((a, b) => a - b);
  const median = sorted[Math.floor(sorted.length / 2)];
  const threshold = Math.max(median * 4, 60_000);

  const gaps: Gap[] = [];
  for (let i = 1; i < samples.length; i++) {
    if (samples[i].ts - samples[i - 1].ts > threshold) {
      gaps.push({ from: samples[i - 1].ts, to: samples[i].ts, ongoing: false });
    }
  }
  const last = samples[samples.length - 1].ts;
  if (endMs - last > threshold) gaps.push({ from: last, to: endMs, ongoing: true });

  return { gaps, threshold };
}

// Lịch sử lệnh từ xa của một máy (cần quyền REMOTE_COMMAND)
async function fetchCommands(clientId: string): Promise<CommandInfo[]> {
  const session = getSession();
  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}/remote/commands/client/${encodeURIComponent(clientId)}`, {
      headers: {
        'Content-Type': 'application/json',
        ...(session?.token ? { Authorization: `Bearer ${session.token}` } : {}),
      },
    });
  } catch {
    throw new Error('Không kết nối được tới máy chủ khi tải lịch sử lệnh.');
  }
  if (!response.ok) {
    throw new Error(
      response.status === 403
        ? 'Tài khoản không có quyền xem lịch sử lệnh từ xa.'
        : `Không thể tải lịch sử lệnh (HTTP ${response.status}).`
    );
  }
  const data: unknown = await response.json().catch(() => []);
  return Array.isArray(data) ? (data as CommandInfo[]) : [];
}

// ============================================================
// COMPONENT PHỤ
// ============================================================

function PanelTitle({ icon, title, right }: { icon: ReactNode; title: string; right?: ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-3 mb-5">
      <div className="flex items-center gap-2.5">
        <span className="text-cyan-400">{icon}</span>
        <h3 className="text-sm font-bold tracking-wide">{title}</h3>
      </div>
      {right}
    </div>
  );
}

// ============================================================
// TRANG LỊCH SỬ GIÁM SÁT
// ============================================================

export default function HistoryPage() {
  const { isDark } = useTheme();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const session = getSession();
  const canViewCommands = hasPermission(session, 'REMOTE_COMMAND');

  const [clients, setClients] = useState<ClientInfo[]>([]);
  const [clientsLoaded, setClientsLoaded] = useState(false);
  const [clientsError, setClientsError] = useState<string | null>(null);

  const [rangeKey, setRangeKey] = useState<RangeKey>('1h');
  const [visible, setVisible] = useState<Record<MetricKey, boolean>>({ cpu: true, ram: true, disk: true });
  // 0 = không tự làm mới
  const [refreshMs, setRefreshMs] = useState(30_000);

  const [bundle, setBundle] = useState<Bundle | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [isRefreshing, setIsRefreshing] = useState(false);

  const [typeFilter, setTypeFilter] = useState<'all' | 'alert' | 'command'>('all');
  const [searchTerm, setSearchTerm] = useState('');
  const [pageState, setPageState] = useState({ page: 1, key: '' });

  const range = RANGES.find((r) => r.key === rangeKey) ?? RANGES[1];
  const minutes = range.minutes;

  // ----------------------------------------------------------
  // Danh sách máy (tự làm mới 30 giây để trạng thái luôn mới)
  // ----------------------------------------------------------
  useEffect(() => {
    return startPolling(async () => {
      try {
        setClients(await getClients());
        setClientsError(null);
      } catch (error) {
        setClientsError(error instanceof Error ? error.message : 'Không thể tải danh sách máy.');
      } finally {
        setClientsLoaded(true);
      }
    }, 30_000);
  }, []);

  // Token hết hạn (401) -> về trang đăng nhập
  useEffect(() => {
    const onUnauthorized = () => navigate('/login', { replace: true });
    window.addEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
  }, [navigate]);

  // Máy đang xem: lấy từ ?client=..., mặc định là máy online đầu tiên
  const requestedClientId = searchParams.get('client') ?? '';
  const clientId = useMemo(() => {
    if (requestedClientId && clients.some((c) => c.clientId === requestedClientId)) return requestedClientId;
    const firstOnline = clients.find((c) => c.status === 'ONLINE');
    return (firstOnline ?? clients[0])?.clientId ?? '';
  }, [requestedClientId, clients]);

  const activeClient = clients.find((c) => c.clientId === clientId);
  const dataKey = `${clientId}|${minutes}`;

  // ----------------------------------------------------------
  // Tải lịch sử + cảnh báo + lệnh của máy đang chọn
  // ----------------------------------------------------------
  const load = useCallback(async () => {
    if (!clientId) return;

    const [historyRes, alertsRes, commandsRes] = await Promise.allSettled([
      getMetricHistory(clientId, minutes),
      getAlertsByClient(clientId, 500),
      canViewCommands ? fetchCommands(clientId) : Promise.resolve<CommandInfo[]>([]),
    ]);

    if (historyRes.status === 'rejected') {
      setLoadError(describeError(historyRes.reason, 'Không thể tải lịch sử giám sát.'));
      return;
    }

    const warnings: string[] = [];
    let alerts: AlertInfo[] = [];
    if (alertsRes.status === 'fulfilled') {
      alerts = alertsRes.value;
    } else {
      warnings.push(`Cảnh báo: ${alertsRes.reason instanceof Error ? alertsRes.reason.message : 'không tải được'}`);
    }

    let commands: CommandInfo[] = [];
    if (commandsRes.status === 'fulfilled') {
      commands = commandsRes.value;
    } else {
      warnings.push(`Lệnh từ xa: ${commandsRes.reason instanceof Error ? commandsRes.reason.message : 'không tải được'}`);
    }

    setBundle({
      key: dataKey,
      samples: toSamples(historyRes.value),
      alerts,
      commands,
      warnings,
      fetchedAt: new Date(),
    });
    setLoadError(null);
  }, [clientId, minutes, canViewCommands, dataKey]);

  useEffect(() => {
    if (!clientId) return;
    // "Tắt tự động" vẫn tải một lần khi vào trang / đổi máy / đổi khoảng thời gian
    return startPolling(load, refreshMs === 0 ? 86_400_000 : refreshMs);
  }, [clientId, refreshMs, load]);

  const refreshNow = async () => {
    setIsRefreshing(true);
    await load();
    setIsRefreshing(false);
  };

  const selectClient = (id: string) => {
    setSearchParams({ client: id }, { replace: true });
  };

  // Chỉ dùng dữ liệu đúng máy + đúng khoảng đang chọn (tránh nhấp nháy khi đổi máy)
  const ready = bundle !== null && bundle.key === dataKey;
  const isLoading = !!clientId && !ready && !loadError;
  const samples = useMemo(() => (ready ? bundle.samples : []), [ready, bundle]);
  const fetchedAt = ready ? bundle.fetchedAt : null;

  // ----------------------------------------------------------
  // Dữ liệu dẫn xuất
  // ----------------------------------------------------------
  const rangeStartMs = fetchedAt ? fetchedAt.getTime() - minutes * 60_000 : 0;

  const stats = useMemo(
    () => ({
      cpu: computeStat(samples, 'cpu'),
      ram: computeStat(samples, 'ram'),
      disk: computeStat(samples, 'disk'),
      down: computeStat(samples, 'down'),
      up: computeStat(samples, 'up'),
    }),
    [samples]
  );

  const distributions = useMemo(
    () => ({
      cpu: distribution(samples, 'cpu'),
      ram: distribution(samples, 'ram'),
      disk: distribution(samples, 'disk'),
    }),
    [samples]
  );

  const gapInfo = useMemo(
    () => findGaps(samples, fetchedAt ? fetchedAt.getTime() : 0),
    [samples, fetchedAt]
  );

  const chartData = useMemo(() => {
    const reduced = downsample(samples, MAX_CHART_POINTS);
    const span = samples.length > 1 ? samples[samples.length - 1].ts - samples[0].ts : 0;
    const bucketMs = reduced.length > 1 ? span / reduced.length : 0;
    return withGapBreaks(reduced, Math.max(gapInfo.threshold, bucketMs * 3));
  }, [samples, gapInfo.threshold]);

  // Vạch đánh dấu thời điểm phát sinh cảnh báo trên biểu đồ
  const alertMarkers = useMemo(() => {
    if (!ready || samples.length === 0) return [];
    const first = samples[0].ts;
    const last = samples[samples.length - 1].ts;
    return bundle.alerts
      .map((a) => ({ ts: toDate(a.createdAt)?.getTime() ?? null, color: SEVERITY_STYLE[a.severity]?.color ?? '#38bdf8', id: a.alertId }))
      .filter((m): m is { ts: number; color: string; id: number } => m.ts !== null && m.ts >= first && m.ts <= last)
      .slice(0, MAX_ALERT_MARKERS);
  }, [ready, bundle, samples]);

  // Nhật ký sự kiện: cảnh báo + lệnh từ xa trong khoảng đang xem
  const events = useMemo<EventRow[]>(() => {
    if (!ready) return [];
    const now = bundle.fetchedAt.getTime();
    const rows: EventRow[] = [];

    for (const a of bundle.alerts) {
      const created = toDate(a.createdAt);
      if (!created) continue;
      // Giữ lại cảnh báo trong khoảng đang xem, và cả cảnh báo cũ chưa giải quyết
      if (created.getTime() < rangeStartMs && a.status === 'RESOLVED') continue;

      const resolved = toDate(a.resolvedAt);
      const severity = SEVERITY_STYLE[a.severity] ?? SEVERITY_STYLE.INFO;
      const status = ALERT_STATUS_STYLE[a.status] ?? ALERT_STATUS_STYLE.ACTIVE;
      const unit = PERCENT_RESOURCES.includes(a.resourceType) ? '%' : '';

      rows.push({
        key: `a-${a.alertId}`,
        kind: 'alert',
        time: created,
        title: a.ruleName || RESOURCE_LABEL[a.resourceType] || 'Cảnh báo',
        detail: `${severity.label} · ${RESOURCE_LABEL[a.resourceType]} ${Number(a.currentValue).toFixed(1)}${unit} / ngưỡng ${Number(a.thresholdValue).toFixed(1)}${unit}`,
        durationMs: resolved ? resolved.getTime() - created.getTime() : now - created.getTime(),
        open: !resolved,
        statusLabel: status.label,
        statusClass: status.className,
        statusDot: status.dot,
        accent: severity.color,
      });
    }

    for (const c of bundle.commands) {
      const sent = toDate(c.sentAt) ?? toDate(c.startedAt) ?? toDate(c.completedAt);
      if (!sent || sent.getTime() < rangeStartMs) continue;

      const started = toDate(c.startedAt) ?? sent;
      const completed = toDate(c.completedAt);
      const statusKey = c.status ?? '';
      const status = COMMAND_STATUS_STYLE[statusKey] ?? {
        label: statusKey || 'Không rõ',
        className: 'bg-slate-500/15 text-slate-400 border-slate-500/40',
        dot: 'bg-slate-400',
      };
      const detailRaw = c.errorMessage || c.result || c.parameters || '—';

      rows.push({
        key: `c-${c.commandId}`,
        kind: 'command',
        time: sent,
        title: COMMAND_LABEL[c.commandType ?? ''] ?? c.commandType ?? 'Lệnh từ xa',
        detail: cleanText(detailRaw),
        durationMs: completed ? completed.getTime() - started.getTime() : null,
        open: false,
        statusLabel: status.label,
        statusClass: status.className,
        statusDot: status.dot,
        accent: statusKey === 'FAILED' ? '#ef4444' : '#a78bfa',
      });
    }

    return rows.sort((a, b) => b.time.getTime() - a.time.getTime());
  }, [ready, bundle, rangeStartMs]);

  const filteredEvents = useMemo(() => {
    const keyword = searchTerm.trim().toLowerCase();
    return events.filter((e) => {
      const matchesType = typeFilter === 'all' || e.kind === typeFilter;
      const matchesSearch =
        keyword === '' || e.title.toLowerCase().includes(keyword) || e.detail.toLowerCase().includes(keyword);
      return matchesType && matchesSearch;
    });
  }, [events, typeFilter, searchTerm]);

  // Đổi bộ lọc / máy / khoảng thời gian thì tự về trang 1
  const pageKey = `${typeFilter}|${searchTerm}|${dataKey}`;
  const setPage = (next: number) => setPageState({ page: next, key: pageKey });

  const totalPages = Math.max(1, Math.ceil(filteredEvents.length / PAGE_SIZE));
  const currentPage = Math.min(pageState.key === pageKey ? pageState.page : 1, totalPages);
  const pageStart = (currentPage - 1) * PAGE_SIZE;
  const pageEvents = filteredEvents.slice(pageStart, pageStart + PAGE_SIZE);
  const hasFilter = searchTerm !== '' || typeFilter !== 'all';

  // Tóm tắt kỳ
  const summary = useMemo(() => {
    const alertRows = events.filter((e) => e.kind === 'alert');
    const commandRows = events.filter((e) => e.kind === 'command');
    const resolvedDurations = alertRows.filter((e) => !e.open && e.durationMs !== null).map((e) => e.durationMs as number);
    const critical = ready
      ? bundle.alerts.filter((a) => a.severity === 'CRITICAL' && alertRows.some((r) => r.key === `a-${a.alertId}`)).length
      : 0;
    return {
      alerts: alertRows.length,
      critical,
      open: alertRows.filter((e) => e.open).length,
      commands: commandRows.length,
      failedCommands: commandRows.filter((e) => e.statusLabel === COMMAND_STATUS_STYLE.FAILED.label).length,
      meanResolveMs: resolvedDurations.length === 0 ? null : resolvedDurations.reduce((a, b) => a + b, 0) / resolvedDurations.length,
    };
  }, [events, ready, bundle]);

  const totalGapMs = gapInfo.gaps.reduce((sum, g) => sum + (g.to - g.from), 0);

  // Xuất dữ liệu đang xem ra file CSV
  const exportCsv = () => {
    if (samples.length === 0) return;
    const lines = ['thoi_gian,cpu_%,ram_%,disk_%,tai_xuong_B/s,tai_len_B/s'];
    for (const s of samples) {
      lines.push(
        [formatFull(new Date(s.ts)), s.cpu ?? '', s.ram ?? '', s.disk ?? '', s.down ?? '', s.up ?? ''].join(',')
      );
    }
    const blob = new Blob([`\uFEFF${lines.join('\n')}`], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `lich-su-${activeClient?.hostname ?? clientId}-${rangeKey}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  };

  // ----------------------------------------------------------
  // Style (cùng bộ với Dashboard / Alerts / Agents)
  // ----------------------------------------------------------
  const card = `rounded-2xl backdrop-blur-xl border transition-colors duration-500 ${
    isDark
      ? 'bg-[#081226]/80 border-blue-500/20 text-white shadow-[0_0_32px_-10px_rgba(37,99,235,0.45)]'
      : 'bg-white border-slate-200 text-slate-900 shadow-sm'
  }`;
  const muted = isDark ? 'text-slate-400' : 'text-slate-500';
  const subtle = isDark ? 'text-slate-300' : 'text-slate-600';
  const track = isDark ? 'bg-slate-700/60' : 'bg-slate-200';
  const divider = isDark ? 'border-blue-500/10' : 'border-slate-100';
  const fieldClass = `rounded-xl border px-3 py-2 text-xs font-medium focus:outline-none focus:ring-1 focus:ring-cyan-400 ${
    isDark ? 'bg-[#050c1c] border-blue-500/25 text-slate-200' : 'bg-slate-50 border-slate-300 text-slate-700'
  }`;
  const iconButton = `p-1.5 rounded-lg border transition-colors disabled:opacity-40 disabled:cursor-not-allowed ${
    isDark
      ? 'bg-blue-600/20 border-blue-500/40 text-cyan-300 hover:bg-blue-600/40'
      : 'bg-blue-50 border-blue-200 text-blue-600 hover:bg-blue-100'
  }`;
  const chipIdle = isDark
    ? 'bg-blue-600/20 border border-blue-500/40 text-cyan-300 hover:bg-blue-600/40'
    : 'bg-blue-50 border border-blue-200 text-blue-600 hover:bg-blue-100';
  const tooltipStyle = {
    backgroundColor: isDark ? '#0B1527' : '#ffffff',
    border: `1px solid ${isDark ? '#1e3a8a' : '#e2e8f0'}`,
    borderRadius: 12,
    fontSize: 12,
  };
  const gridColor = isDark ? '#1e293b' : '#e2e8f0';
  const axisColor = isDark ? '#64748b' : '#94a3b8';

  const xAxisProps = {
    dataKey: 'ts',
    type: 'number' as const,
    scale: 'time' as const,
    domain: ['dataMin', 'dataMax'] as [string, string],
    tickFormatter: (v: number | string) => axisLabel(Number(v), minutes),
    tick: { fontSize: 10, fill: axisColor },
    minTickGap: 50,
  };
  const tooltipLabel = (label: unknown) => formatFull(new Date(Number(label)));

  const chartEmptyText = loadError
    ? loadError
    : !clientId
      ? clientsLoaded
        ? 'Chưa có máy nào trong hệ thống.'
        : 'Đang tải danh sách máy...'
      : isLoading
        ? 'Đang tải lịch sử giám sát...'
        : `Máy này chưa gửi dữ liệu trong ${range.label.toLowerCase()} gần nhất.`;

  const warningLines = [...(clientsError ? [clientsError] : []), ...(ready ? bundle.warnings : [])];

  // ----------------------------------------------------------
  // Giao diện
  // ----------------------------------------------------------
  return (
    <div className="space-y-5 pb-12 relative">

      {/* 1. BREADCRUMB + TIÊU ĐỀ */}
      <div className="space-y-3">
        <div className={`flex items-center gap-2 text-xs ${muted}`}>
          <LayoutDashboard className="w-3.5 h-3.5" />
          <ChevronRight className="w-3 h-3" />
          <span>History</span>
          <ChevronRight className="w-3 h-3" />
          <span className={isDark ? 'text-slate-200' : 'text-slate-700'}>Lịch sử giám sát</span>
        </div>

        <div className="flex flex-col lg:flex-row justify-between items-start lg:items-center gap-4">
          <div className="flex items-center gap-4">
            <div className="p-3 rounded-2xl bg-gradient-to-br from-blue-500/30 to-cyan-400/10 border border-cyan-400/30 shadow-[0_0_24px_rgba(34,211,238,0.3)]">
              <History className="w-8 h-8 text-cyan-400" />
            </div>
            <div>
              <h1 className="text-2xl sm:text-3xl font-black tracking-tight">Lịch sử giám sát</h1>
              <p className={`text-xs sm:text-sm mt-1 ${muted}`}>
                Xem lại tải CPU, RAM, Disk, mạng và các sự cố đã xảy ra trên từng máy tính Linux
                {fetchedAt && ` · Cập nhật lúc ${fetchedAt.toLocaleTimeString('vi-VN')}`}
              </p>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-3 relative z-10">
            <div className={`flex items-center rounded-xl border overflow-hidden ${
              isDark ? 'bg-[#081226]/80 border-blue-500/40' : 'bg-white border-slate-300'
            }`}>
              <button
                onClick={refreshNow}
                disabled={isRefreshing || !clientId}
                className={`px-4 py-2.5 text-xs font-bold flex items-center gap-2 disabled:opacity-60 ${
                  isDark ? 'text-slate-100 hover:bg-blue-600/20' : 'text-slate-700 hover:bg-slate-100'
                }`}
              >
                <RefreshCw className={`w-4 h-4 ${isLoading || isRefreshing ? 'animate-spin' : ''}`} />
                Làm mới
              </button>
              <select
                value={refreshMs}
                onChange={(e) => setRefreshMs(Number(e.target.value))}
                title="Tự động làm mới"
                className={`py-2.5 pl-2 pr-1 text-xs font-medium border-l focus:outline-none cursor-pointer ${
                  isDark ? 'bg-transparent border-blue-500/40 text-slate-300' : 'bg-transparent border-slate-300 text-slate-600'
                }`}
              >
                <option value={10000}>Tự động 10s</option>
                <option value={30000}>Tự động 30s</option>
                <option value={60000}>Tự động 60s</option>
                <option value={0}>Tắt tự động</option>
              </select>
            </div>

            <button
              onClick={exportCsv}
              disabled={samples.length === 0}
              className="px-5 py-2.5 rounded-xl bg-gradient-to-r from-blue-600 to-cyan-500 text-white text-xs font-bold shadow-[0_0_24px_rgba(34,211,238,0.45)] hover:from-blue-500 hover:to-cyan-400 transition-all flex items-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <Download className="w-4 h-4" />
              Xuất CSV
            </button>
          </div>
        </div>
      </div>

      {/* Thông báo lỗi */}
      {loadError && (
        <div className="flex items-start gap-3 p-4 rounded-2xl border bg-red-500/10 border-red-500/30 text-red-400 text-sm">
          <ShieldAlert className="w-5 h-5 shrink-0 mt-0.5" />
          <div className="flex-1">
            <p className="font-semibold">{bundle ? 'Mất kết nối tới máy chủ, đang hiển thị dữ liệu cũ' : 'Không thể tải lịch sử giám sát'}</p>
            <p className="text-xs mt-0.5 opacity-90">{loadError}</p>
          </div>
          <button onClick={refreshNow} className="text-xs font-bold underline underline-offset-2">Thử lại</button>
        </div>
      )}
      {warningLines.length > 0 && (
        <div className="p-3 rounded-2xl border text-xs bg-amber-500/10 border-amber-500/30 text-amber-400 space-y-1">
          {warningLines.map((w) => <p key={w}>{w}</p>)}
        </div>
      )}

      {/* 2. THANH CHỌN MÁY + KHOẢNG THỜI GIAN */}
      <div className={`${card} p-4 flex flex-col xl:flex-row xl:items-center justify-between gap-4`}>
        <div className="flex flex-wrap items-center gap-3">
          <div className="flex items-center gap-2">
            <Monitor className="w-4 h-4 text-cyan-400" />
            <span className={`text-xs font-semibold ${subtle}`}>Máy tính</span>
          </div>
          <select
            value={clientId}
            onChange={(e) => selectClient(e.target.value)}
            disabled={clients.length === 0}
            className={`${fieldClass} min-w-64 max-w-full`}
          >
            {clients.length === 0 && <option value="">Chưa có máy nào</option>}
            {clients.map((c) => (
              <option key={c.clientId} value={c.clientId}>
                {c.hostname} ({c.ipAddress}){c.status === 'ONLINE' ? '' : ` - ${c.status === 'OFFLINE' ? 'Ngoại tuyến' : c.status}`}
              </option>
            ))}
          </select>
          <button
            className={iconButton}
            title="Xem chi tiết máy"
            disabled={!clientId}
            onClick={() => navigate(`/computers/${encodeURIComponent(clientId)}`)}
          >
            <Eye className="w-3.5 h-3.5" />
          </button>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <span className={`text-[11px] font-mono ${muted}`}>
            {fetchedAt
              ? `${formatDateTime(new Date(rangeStartMs))} → ${formatDateTime(fetchedAt)} · ${samples.length} mẫu`
              : '—'}
          </span>
          <div className="flex items-center gap-1.5">
            {RANGES.map((r) => (
              <button
                key={r.key}
                onClick={() => setRangeKey(r.key)}
                className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-colors ${
                  rangeKey === r.key ? 'bg-cyan-500 text-slate-950' : chipIdle
                }`}
              >
                {r.label}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* 3. BIỂU ĐỒ TÀI NGUYÊN + THỐNG KÊ KỲ */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-5">

        {/* Biểu đồ CPU / RAM / Disk có thể kéo chọn đoạn */}
        <div className={`${card} lg:col-span-8 p-5`}>
          <PanelTitle
            icon={<Activity className="w-4 h-4" />}
            title={`Diễn biến tải · ${range.label} gần nhất`}
            right={
              <div className="flex items-center gap-1.5">
                {METRICS.map((m) => (
                  <button
                    key={m.key}
                    onClick={() => setVisible((prev) => ({ ...prev, [m.key]: !prev[m.key] }))}
                    className={`px-2.5 py-1 rounded-lg text-[11px] font-bold flex items-center gap-1.5 border transition-opacity ${
                      isDark ? 'border-blue-500/30' : 'border-slate-300'
                    } ${visible[m.key] ? '' : 'opacity-40'}`}
                    title={visible[m.key] ? `Ẩn ${m.label}` : `Hiện ${m.label}`}
                  >
                    <span className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: m.color }} />
                    <span className={subtle}>{m.label}</span>
                  </button>
                ))}
              </div>
            }
          />

          <div className="h-72 w-full">
            {chartData.length > 1 ? (
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={chartData} margin={{ top: 5, right: 10, left: -16, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke={gridColor} />
                  <XAxis {...xAxisProps} />
                  <YAxis domain={[0, 100]} unit="%" tick={{ fontSize: 10, fill: axisColor }} />
                  <Tooltip
                    contentStyle={tooltipStyle}
                    labelFormatter={tooltipLabel}
                    formatter={(value) => (Number.isFinite(Number(value)) ? `${Number(value).toFixed(1)}%` : '--')}
                  />
                  <ReferenceLine y={90} stroke="#ef4444" strokeDasharray="4 4" strokeOpacity={0.55} />
                  <ReferenceLine y={75} stroke="#f59e0b" strokeDasharray="4 4" strokeOpacity={0.45} />
                  {alertMarkers.map((m) => (
                    <ReferenceLine key={m.id} x={m.ts} stroke={m.color} strokeDasharray="2 4" strokeOpacity={0.8} ifOverflow="hidden" />
                  ))}
                  {METRICS.filter((m) => visible[m.key]).map((m) => (
                    <Line
                      key={m.key}
                      type="monotone"
                      dataKey={m.key}
                      name={m.label}
                      stroke={m.color}
                      strokeWidth={2}
                      dot={false}
                      connectNulls={false}
                      isAnimationActive={false}
                    />
                  ))}
                  <Brush
                    dataKey="ts"
                    height={22}
                    travellerWidth={8}
                    stroke="#22d3ee"
                    fill={isDark ? '#050c1c' : '#f1f5f9'}
                    tickFormatter={(v: number | string) => axisLabel(Number(v), minutes)}
                  />
                </LineChart>
              </ResponsiveContainer>
            ) : (
              <div className={`h-full flex items-center justify-center text-xs text-center px-6 ${muted}`}>
                {isLoading && <Loader2 className="w-4 h-4 animate-spin mr-2 text-cyan-400" />}
                {chartEmptyText}
              </div>
            )}
          </div>

          <p className={`text-[11px] mt-3 pt-3 border-t ${divider} ${muted}`}>
            Đường đứt đỏ/vàng là ngưỡng 90% / 75%. Vạch dọc đánh dấu lúc phát sinh cảnh báo. Kéo hai đầu thanh dưới biểu đồ để phóng to một đoạn.
          </p>
        </div>

        {/* Thống kê trong kỳ */}
        <div className={`${card} lg:col-span-4 p-5`}>
          <PanelTitle
            icon={<Cpu className="w-4 h-4" />}
            title="Thống kê trong kỳ"
            right={<span className={`text-[11px] font-medium truncate max-w-[50%] ${muted}`}>{activeClient?.hostname ?? ''}</span>}
          />

          <div className="space-y-4">
            {METRICS.map((m) => {
              const stat = stats[m.key];
              return (
                <div key={m.key} className={`pb-4 border-b last:border-0 last:pb-0 ${divider}`}>
                  <div className="flex items-center justify-between gap-2">
                    <div className="flex items-center gap-2">
                      <span className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: m.color }} />
                      <span className="text-xs font-bold">{m.label}</span>
                    </div>
                    <span className="text-lg font-black font-mono" style={{ color: m.color }}>{fmtPct(stat.avg)}</span>
                  </div>
                  <div className={`h-1.5 rounded-full overflow-hidden mt-2 ${track}`}>
                    <div className="h-full rounded-full" style={{ width: `${Math.min(100, stat.avg ?? 0)}%`, backgroundColor: m.color }} />
                  </div>
                  <div className={`grid grid-cols-3 gap-2 mt-2.5 text-[11px] font-mono ${muted}`}>
                    <span>Thấp <b className={subtle}>{fmtPct(stat.min)}</b></span>
                    <span>Cao <b className={subtle}>{fmtPct(stat.max)}</b></span>
                    <span>P95 <b className={subtle}>{fmtPct(stat.p95)}</b></span>
                  </div>
                  <p className={`text-[11px] mt-1 ${muted}`}>Đỉnh lúc {formatClock(stat.peakAt)}</p>
                </div>
              );
            })}
          </div>
        </div>
      </div>

      {/* 4. MẠNG + PHÂN BỐ MỨC TẢI */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-5">

        {/* Lưu lượng mạng */}
        <div className={`${card} lg:col-span-8 p-5`}>
          <PanelTitle
            icon={<HardDrive className="w-4 h-4" />}
            title="Lưu lượng mạng"
            right={
              <div className="flex items-center gap-4 text-[11px]">
                <span className="flex items-center gap-1.5">
                  <span className="w-2.5 h-2.5 rounded-full border-2 border-emerald-400" />
                  <span className={subtle}>Tải xuống · đỉnh {fmtBytes(stats.down.max)}/s</span>
                </span>
                <span className="flex items-center gap-1.5">
                  <span className="w-2.5 h-2.5 rounded-full border-2 border-pink-400" />
                  <span className={subtle}>Tải lên · đỉnh {fmtBytes(stats.up.max)}/s</span>
                </span>
              </div>
            }
          />
          <div className="h-48 w-full">
            {chartData.length > 1 ? (
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={chartData} margin={{ top: 5, right: 10, left: 8, bottom: 0 }}>
                  <defs>
                    <linearGradient id="historyDownFill" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#34d399" stopOpacity={0.35} />
                      <stop offset="100%" stopColor="#34d399" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke={gridColor} />
                  <XAxis {...xAxisProps} />
                  <YAxis width={64} tickFormatter={(v) => fmtBytes(Number(v))} tick={{ fontSize: 10, fill: axisColor }} />
                  <Tooltip
                    contentStyle={tooltipStyle}
                    labelFormatter={tooltipLabel}
                    formatter={(value) => `${fmtBytes(Number(value))}/s`}
                  />
                  <Area type="monotone" dataKey="down" name="Tải xuống" stroke="#34d399" strokeWidth={2} fill="url(#historyDownFill)" dot={false} connectNulls={false} isAnimationActive={false} />
                  <Area type="monotone" dataKey="up" name="Tải lên" stroke="#f472b6" strokeWidth={2} fill="none" dot={false} connectNulls={false} isAnimationActive={false} />
                </AreaChart>
              </ResponsiveContainer>
            ) : (
              <div className={`h-full flex items-center justify-center text-xs text-center px-6 ${muted}`}>
                {chartEmptyText}
              </div>
            )}
          </div>
        </div>

        {/* Phân bố thời gian theo mức tải */}
        <div className={`${card} lg:col-span-4 p-5`}>
          <PanelTitle icon={<Clock className="w-4 h-4" />} title="Thời gian theo mức tải" />

          <div className="space-y-5">
            {METRICS.map((m) => {
              const dist = distributions[m.key];
              const hasData = samples.length > 0;
              return (
                <div key={m.key} className="space-y-1.5">
                  <div className="flex items-center justify-between text-xs">
                    <span className="font-bold">{m.label}</span>
                    <span className={`font-mono text-[11px] ${muted}`}>
                      {hasData ? `${(dist[2] + dist[3]).toFixed(1)}% thời gian ≥ 75%` : '--'}
                    </span>
                  </div>
                  <div className={`h-2.5 rounded-full overflow-hidden flex ${track}`}>
                    {hasData && dist.map((pct, i) => (
                      <div
                        key={LEVELS[i].label}
                        title={`${LEVELS[i].label} (${LEVELS[i].hint}): ${pct.toFixed(1)}%`}
                        style={{ width: `${pct}%`, backgroundColor: LEVELS[i].color }}
                      />
                    ))}
                  </div>
                </div>
              );
            })}
          </div>

          <div className={`mt-5 pt-4 border-t grid grid-cols-2 gap-x-3 gap-y-2 text-[11px] ${divider}`}>
            {LEVELS.map((level) => (
              <span key={level.label} className="flex items-center gap-2">
                <span className="w-2.5 h-2.5 rounded-full shrink-0" style={{ backgroundColor: level.color }} />
                <span className={subtle}>{level.label}</span>
                <span className={`font-mono ${muted}`}>{level.hint}</span>
              </span>
            ))}
          </div>
        </div>
      </div>

      {/* 5. NHẬT KÝ SỰ KIỆN + TÓM TẮT KỲ */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-5">

        {/* Nhật ký sự kiện */}
        <div className={`${card} lg:col-span-8 p-5`}>
          <PanelTitle
            icon={<Bell className="w-4 h-4" />}
            title="Nhật ký sự kiện"
            right={
              <div className="flex flex-wrap items-center justify-end gap-2">
                <select
                  value={typeFilter}
                  onChange={(e) => setTypeFilter(e.target.value as 'all' | 'alert' | 'command')}
                  className={fieldClass}
                >
                  <option value="all">Tất cả loại</option>
                  <option value="alert">Cảnh báo</option>
                  <option value="command">Lệnh từ xa</option>
                </select>

                <div className="relative">
                  <Search className="w-3.5 h-3.5 text-cyan-400 absolute left-2.5 top-1/2 -translate-y-1/2" />
                  <input
                    type="text"
                    value={searchTerm}
                    onChange={(e) => setSearchTerm(e.target.value)}
                    placeholder="Tìm kiếm..."
                    className={`${fieldClass} w-36 pl-8`}
                  />
                </div>

                <button
                  onClick={() => {
                    setSearchTerm('');
                    setTypeFilter('all');
                  }}
                  disabled={!hasFilter}
                  title="Xóa bộ lọc"
                  className="px-3 py-2 rounded-xl bg-gradient-to-r from-blue-600 to-cyan-500 text-white text-xs font-bold flex items-center gap-1.5 disabled:opacity-50"
                >
                  <Filter className="w-3.5 h-3.5" />
                  {hasFilter ? 'Xóa lọc' : 'Lọc'}
                </button>
              </div>
            }
          />

          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-xs">
              <thead>
                <tr className={`text-[11px] font-semibold ${isDark ? 'bg-blue-950/40 text-slate-400' : 'bg-slate-50 text-slate-500'}`}>
                  <th className="p-3 rounded-l-xl">Thời gian</th>
                  <th className="p-3">Loại</th>
                  <th className="p-3">Sự kiện</th>
                  <th className="p-3">Chi tiết</th>
                  <th className="p-3">Kéo dài</th>
                  <th className="p-3 rounded-r-xl">Trạng thái</th>
                </tr>
              </thead>
              <tbody>
                {isLoading ? (
                  <tr>
                    <td colSpan={6} className={`text-center py-10 ${muted}`}>
                      <Loader2 className="w-5 h-5 animate-spin inline mr-2 text-cyan-400" />
                      Đang tải nhật ký sự kiện...
                    </td>
                  </tr>
                ) : pageEvents.length === 0 ? (
                  <tr>
                    <td colSpan={6} className={`text-center py-10 ${muted}`}>
                      {events.length === 0
                        ? `Không có cảnh báo hay lệnh từ xa nào trong ${range.label.toLowerCase()} gần nhất.`
                        : 'Không tìm thấy sự kiện phù hợp.'}
                    </td>
                  </tr>
                ) : (
                  pageEvents.map((event) => (
                    <tr
                      key={event.key}
                      className={`border-b last:border-0 transition-colors ${
                        isDark ? 'border-blue-500/10 hover:bg-blue-950/30' : 'border-slate-100 hover:bg-slate-50'
                      }`}
                    >
                      <td className={`p-3 font-mono text-[11px] whitespace-nowrap ${muted}`}>{formatDateTime(event.time)}</td>
                      <td className="p-3">
                        <span className="inline-flex items-center gap-1.5 font-semibold" style={{ color: event.accent }}>
                          {event.kind === 'alert' ? <Bell className="w-3.5 h-3.5" /> : <Terminal className="w-3.5 h-3.5" />}
                          {event.kind === 'alert' ? 'Cảnh báo' : 'Lệnh'}
                        </span>
                      </td>
                      <td className="p-3 font-bold max-w-[12rem] truncate" title={event.title}>{event.title}</td>
                      <td className={`p-3 max-w-[16rem] truncate ${subtle}`} title={event.detail}>{event.detail}</td>
                      <td className={`p-3 font-mono text-[11px] whitespace-nowrap ${muted}`}>
                        {event.durationMs === null ? '—' : event.open ? `${formatDuration(event.durationMs)}…` : formatDuration(event.durationMs)}
                      </td>
                      <td className="p-3">
                        <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[10px] font-semibold border whitespace-nowrap ${event.statusClass}`}>
                          <span className={`w-1.5 h-1.5 rounded-full ${event.statusDot}`} />
                          {event.statusLabel}
                        </span>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>

          {/* Phân trang */}
          <div className={`mt-4 pt-4 border-t flex flex-col sm:flex-row items-center justify-between gap-3 text-xs ${divider} ${muted}`}>
            <span>
              {filteredEvents.length === 0
                ? 'Không có kết quả'
                : `Hiển thị ${pageStart + 1}–${pageStart + pageEvents.length} / ${filteredEvents.length} sự kiện`}
            </span>
            <div className="flex items-center gap-1.5">
              <button
                onClick={() => setPage(Math.max(1, currentPage - 1))}
                disabled={currentPage <= 1}
                className={iconButton}
                aria-label="Trang trước"
              >
                <ChevronLeft className="w-4 h-4" />
              </button>
              <span className="px-3 py-1 rounded-lg bg-cyan-500 text-slate-950 font-bold">
                {currentPage} / {totalPages}
              </span>
              <button
                onClick={() => setPage(Math.min(totalPages, currentPage + 1))}
                disabled={currentPage >= totalPages}
                className={iconButton}
                aria-label="Trang sau"
              >
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
          </div>
        </div>

        {/* Tóm tắt kỳ + khoảng mất dữ liệu */}
        <div className="lg:col-span-4 space-y-5">

          <div className={`${card} p-5`}>
            <PanelTitle icon={<ShieldAlert className="w-4 h-4" />} title="Tóm tắt trong kỳ" />
            <div className="space-y-3 text-xs">
              <div className="flex items-center justify-between gap-2">
                <span className={subtle}>Cảnh báo phát sinh</span>
                <span className="font-mono font-bold">
                  {summary.alerts}
                  <span className={`font-normal ${muted}`}> · {summary.critical} nghiêm trọng · {summary.open} đang mở</span>
                </span>
              </div>
              <div className="flex items-center justify-between gap-2">
                <span className={subtle}>Thời gian xử lý trung bình</span>
                <span className="font-mono font-bold">
                  {summary.meanResolveMs === null ? '--' : formatDuration(summary.meanResolveMs)}
                </span>
              </div>
              <div className="flex items-center justify-between gap-2">
                <span className={subtle}>Lệnh từ xa</span>
                <span className="font-mono font-bold">
                  {canViewCommands ? summary.commands : '--'}
                  <span className={`font-normal ${muted}`}>
                    {canViewCommands ? ` · ${summary.failedCommands} thất bại` : ' · không có quyền'}
                  </span>
                </span>
              </div>
              <div className="flex items-center justify-between gap-2">
                <span className={subtle}>Mẫu dữ liệu thu được</span>
                <span className="font-mono font-bold">{samples.length}</span>
              </div>
              <div className="flex items-center justify-between gap-2">
                <span className={subtle}>Mẫu đầu → cuối</span>
                <span className="font-mono font-bold">
                  {samples.length === 0 ? '—' : `${formatClock(samples[0].ts)} → ${formatClock(samples[samples.length - 1].ts)}`}
                </span>
              </div>
            </div>
          </div>

          <div className={`${card} p-5`}>
            <PanelTitle
              icon={<WifiOff className="w-4 h-4" />}
              title="Khoảng mất dữ liệu"
              right={
                <span className="text-[11px] text-cyan-400 font-medium">
                  {gapInfo.gaps.length === 0 ? 'Không có' : `${gapInfo.gaps.length} lần · ${formatDuration(totalGapMs)}`}
                </span>
              }
            />

            {gapInfo.gaps.length === 0 ? (
              <p className={`text-xs text-center py-4 ${muted}`}>
                {samples.length < 2 ? 'Chưa đủ dữ liệu để đánh giá.' : 'Agent gửi dữ liệu liên tục, không bị gián đoạn.'}
              </p>
            ) : (
              <div className="relative">
                <div className={`absolute left-[5px] top-2 bottom-2 w-px ${isDark ? 'bg-blue-500/25' : 'bg-slate-200'}`} />
                <ul className="space-y-5">
                  {[...gapInfo.gaps].reverse().slice(0, 5).map((gap) => (
                    <li key={gap.from} className="relative pl-6 flex items-start justify-between gap-3">
                      <span
                        className="absolute left-0 top-1 w-[11px] h-[11px] rounded-full ring-4"
                        style={{
                          backgroundColor: gap.ongoing ? '#ef4444' : '#f59e0b',
                          boxShadow: `0 0 10px ${gap.ongoing ? '#ef4444' : '#f59e0b'}`,
                          ['--tw-ring-color' as string]: isDark ? '#081226' : '#ffffff',
                        }}
                      />
                      <div className="min-w-0">
                        <p className="text-[11px] font-mono text-cyan-400">{formatClock(gap.from)} → {gap.ongoing ? 'hiện tại' : formatClock(gap.to)}</p>
                        <p className="text-xs font-bold">{gap.ongoing ? 'Đang mất kết nối' : 'Agent ngừng gửi dữ liệu'}</p>
                      </div>
                      <p className={`text-[11px] text-right shrink-0 font-medium ${gap.ongoing ? 'text-red-400' : 'text-amber-400'}`}>
                        {formatDuration(gap.to - gap.from)}
                      </p>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}