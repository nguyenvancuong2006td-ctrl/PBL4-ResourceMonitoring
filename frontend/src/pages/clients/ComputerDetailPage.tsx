import { useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Activity,
  ArrowDown,
  ArrowLeft,
  ArrowUp,
  Bell,
  CheckCircle2,
  ChevronRight,
  Clock,
  Cpu,
  HardDrive,
  Loader2,
  Monitor,
  Network,
  Power,
  RotateCw,
  Search,
  Server,
  ShieldAlert,
  Tv,
  WifiOff,
} from 'lucide-react';
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import {
  UNAUTHORIZED_EVENT,
  createRemoteCommand,
  getAlertsByClient,
  getClient,
  getClientAgentByClientId,
  getClientHardware,
  getLatestMetrics,
  startPolling,
  subscribeMetricHistory,
  toDate,
} from '../../lib/api';
import type {
  AgentStatus,
  AlertInfo,
  AlertSeverity,
  ClientAgentInfo,
  ClientHardware,
  ClientInfo,
  ClientStatus,
  HistoryPoint,
  LatestMetrics,
  ResourceType,
} from '../../lib/api';
import { useTheme } from '../../context/ThemeContext';

// ============================================================
// HẰNG SỐ
// ============================================================

const SNAPSHOT_REFRESH_MS = 5000;
const LATEST_REFRESH_MS = 5000;
const HISTORY_REFRESH_MS = 10000;

type OsFamily = 'Ubuntu' | 'Debian' | 'Windows' | 'Khác';
type TabKey = 'overview' | 'hardware' | 'processes' | 'alerts';

const RANGES = [
  { minutes: 30, label: '30 phút' },
  { minutes: 60, label: '1 giờ' },
  { minutes: 180, label: '3 giờ' },
];

const STATUS_PILL: Record<ClientStatus, { label: string; className: string; dot: string }> = {
  ONLINE: {
    label: 'Đang hoạt động',
    className: 'bg-emerald-500/15 text-emerald-400 border-emerald-500/40',
    dot: 'bg-emerald-400',
  },
  PENDING_APPROVAL: {
    label: 'Chờ duyệt',
    className: 'bg-amber-500/15 text-amber-400 border-amber-500/40',
    dot: 'bg-amber-400',
  },
  OFFLINE: {
    label: 'Ngoại tuyến',
    className: 'bg-red-500/15 text-red-400 border-red-500/40',
    dot: 'bg-red-400',
  },
  DISABLED: {
    label: 'Vô hiệu hóa',
    className: 'bg-slate-500/15 text-slate-400 border-slate-500/40',
    dot: 'bg-slate-400',
  },
  UNKNOWN: {
    label: 'Chưa rõ',
    className: 'bg-slate-500/15 text-slate-400 border-slate-500/40',
    dot: 'bg-slate-400',
  },
};

const AGENT_LABEL: Record<AgentStatus, string> = {
  ONLINE: 'Đang kết nối',
  OFFLINE: 'Mất kết nối',
  PENDING_APPROVAL: 'Chờ duyệt',
  DISABLED: 'Vô hiệu hóa',
  REJECTED: 'Bị từ chối',
};

const OS_STYLE: Record<OsFamily, { color: string; letter: string }> = {
  Ubuntu: { color: '#e95420', letter: 'U' },
  Debian: { color: '#d70a53', letter: 'D' },
  Windows: { color: '#0078d4', letter: 'W' },
  Khác: { color: '#64748b', letter: 'L' },
};

const SEVERITY_STYLE: Record<AlertSeverity, { label: string; color: string }> = {
  CRITICAL: { label: 'Nghiêm trọng', color: '#ef4444' },
  WARNING: { label: 'Cảnh báo', color: '#f59e0b' },
  INFO: { label: 'Thông tin', color: '#38bdf8' },
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
// HÀM TIỆN ÍCH
// ============================================================

const pad = (n: number) => String(n).padStart(2, '0');

function formatDateTime(value: Parameters<typeof toDate>[0]): string {
  const date = toDate(value);
  if (!date) return '—';
  return `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())} ${pad(date.getDate())}/${pad(date.getMonth() + 1)}/${date.getFullYear()}`;
}

function timeAgo(value: Parameters<typeof toDate>[0]): string {
  const date = toDate(value);
  if (!date) return 'Chưa có dữ liệu';
  const seconds = Math.max(0, Math.floor((Date.now() - date.getTime()) / 1000));
  if (seconds < 10) return 'Vừa xong';
  if (seconds < 60) return `${seconds} giây trước`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes} phút trước`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} giờ trước`;
  return `${Math.floor(hours / 24)} ngày trước`;
}

function formatUptime(ms: number): string {
  const totalMinutes = Math.max(0, Math.floor(ms / 60000));
  const days = Math.floor(totalMinutes / 1440);
  const hours = Math.floor((totalMinutes % 1440) / 60);
  const minutes = totalMinutes % 60;
  if (days > 0) return `${days} ngày ${hours} giờ`;
  if (hours > 0) return `${hours} giờ ${minutes} phút`;
  return `${minutes} phút`;
}

function fmtPct(value: number | null | undefined): string {
  return typeof value === 'number' ? `${value.toFixed(1)}%` : '--';
}

function fmtGhz(mhz: number | null | undefined): string {
  if (typeof mhz !== 'number' || !Number.isFinite(mhz) || mhz <= 0) return '--';
  return `${(mhz / 1000).toFixed(2)} GHz`;
}

function fmtCount(value: number | null | undefined): string {
  return typeof value === 'number' && value > 0 ? String(value) : '--';
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

function detectOs(osName: string): OsFamily {
  const name = (osName ?? '').toLowerCase();
  if (name.includes('ubuntu')) return 'Ubuntu';
  if (name.includes('debian')) return 'Debian';
  if (name.includes('windows')) return 'Windows';
  return 'Khác';
}

// Màu theo mức tải: giữ màu gốc khi bình thường, vàng khi cao, đỏ khi nguy hiểm.
// Khi dữ liệu đã cũ (máy ngoại tuyến) luôn dùng màu xám để không bị nhầm là số liệu đang chạy.
function levelColor(value: number | null | undefined, base = '#22d3ee', stale = false): string {
  if (stale || typeof value !== 'number') return '#64748b';
  if (value >= 90) return '#ef4444';
  if (value >= 75) return '#f59e0b';
  return base;
}

function diskPercent(latest: LatestMetrics | null): number | null {
  if (!latest || latest.disks.length === 0) return null;
  return latest.disks.reduce((sum, d) => sum + d.usagePercent, 0) / latest.disks.length;
}

// ============================================================
// COMPONENT PHỤ
// ============================================================

// Nhãn báo số liệu hiển thị là bản ghi cũ (máy không còn trực tuyến)
function StaleBadge({ at, isDark }: { at: string; isDark: boolean }) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold border ${
        isDark
          ? 'bg-amber-500/10 text-amber-400 border-amber-500/30'
          : 'bg-amber-50 text-amber-700 border-amber-300'
      }`}
    >
      <Clock className="w-3.5 h-3.5" />
      Dữ liệu lúc {at}
    </span>
  );
}

function PanelTitle({ icon, title, right }: { icon: ReactNode; title: string; right?: ReactNode }) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 mb-5">
      <div className="flex items-center gap-2.5">
        <span className="text-cyan-400">{icon}</span>
        <h3 className="text-base font-bold tracking-wide">{title}</h3>
      </div>
      {right}
    </div>
  );
}

// Vòng tròn phần trăm, nội dung đặt ở giữa
function Ring({
  value,
  color,
  track,
  dim,
  children,
}: {
  value: number | null | undefined;
  color: string;
  track: string;
  dim?: boolean;
  children?: ReactNode;
}) {
  const size = 84;
  const stroke = 8;
  const radius = (size - stroke) / 2;
  const circumference = 2 * Math.PI * radius;
  const pct = typeof value === 'number' ? Math.max(0, Math.min(100, value)) : 0;

  return (
    <div className="relative shrink-0" style={{ width: size, height: size }}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} className="-rotate-90">
        <circle cx={size / 2} cy={size / 2} r={radius} fill="none" stroke={track} strokeWidth={stroke} />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke={color}
          strokeWidth={stroke}
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={circumference - (pct / 100) * circumference}
          className="transition-all duration-700"
          style={dim ? undefined : { filter: `drop-shadow(0 0 6px ${color}88)` }}
        />
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center">{children}</div>
    </div>
  );
}

function InfoRow({
  label,
  value,
  mono,
  valueClass,
  divider,
  muted,
}: {
  label: string;
  value: ReactNode;
  mono?: boolean;
  valueClass?: string;
  divider: string;
  muted: string;
}) {
  return (
    <div className={`flex items-center justify-between gap-4 py-3 border-b last:border-0 ${divider}`}>
      <span className={`text-sm shrink-0 ${muted}`}>{label}</span>
      <span className={`text-sm font-semibold text-right break-all ${mono ? 'font-mono' : ''} ${valueClass ?? ''}`}>
        {value}
      </span>
    </div>
  );
}

function UsageBar({
  value,
  track,
  stale,
}: {
  value: number | null | undefined;
  track: string;
  stale?: boolean;
}) {
  const pct = typeof value === 'number' ? Math.max(0, Math.min(100, value)) : 0;
  return (
    <div className={`h-2 rounded-full overflow-hidden ${track}`}>
      <div
        className="h-full rounded-full transition-all duration-700"
        style={{ width: `${pct}%`, backgroundColor: levelColor(value, '#22d3ee', stale) }}
      />
    </div>
  );
}

// ============================================================
// TRANG CHI TIẾT MÁY
// ============================================================

export default function ComputerDetailPage() {
  const { id } = useParams<{ id: string }>();
  const clientId = id ?? '';
  const navigate = useNavigate();
  const { isDark } = useTheme();

  const [tab, setTab] = useState<TabKey>('overview');
  const [clientState, setClientState] = useState<{ clientId: string; data: ClientInfo } | null>(null);
  const [alertsState, setAlertsState] = useState<{ clientId: string; data: AlertInfo[] } | null>(null);
  const [agentState, setAgentState] = useState<{ clientId: string; data: ClientAgentInfo | null } | null>(null);
  const [snapshotError, setSnapshotError] = useState<string | null>(null);
  const [latestState, setLatestState] = useState<{ clientId: string; data: LatestMetrics | null } | null>(null);
  const [hardwareState, setHardwareState] = useState<{ clientId: string; data: ClientHardware | null } | null>(null);

  const [minutes, setMinutes] = useState(60);
  const [history, setHistory] = useState<{ clientId: string; minutes: number; points: HistoryPoint[] } | null>(null);
  const [historyError, setHistoryError] = useState<{ clientId: string; message: string } | null>(null);
  const [chartMode, setChartMode] = useState<'resource' | 'network'>('resource');
  const [processSearch, setProcessSearch] = useState('');
  const [commandLoading, setCommandLoading] = useState<'RESTART' | 'SHUTDOWN' | null>(null);
  const [commandMessage, setCommandMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  // Token hết hạn (401) -> về trang đăng nhập
  useEffect(() => {
    const onUnauthorized = () => navigate('/login', { replace: true });
    window.addEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
  }, [navigate]);

  // Thông tin máy + cảnh báo của ĐÚNG máy này (không tải cả danh sách máy / cảnh báo)
  useEffect(() => {
    if (!clientId) return;
    return startPolling(async () => {
      const [clientRes, alertsRes] = await Promise.allSettled([
        getClient(clientId),
        getAlertsByClient(clientId, 100),
      ]);

      if (clientRes.status === 'rejected') {
        setSnapshotError(
          clientRes.reason instanceof Error ? clientRes.reason.message : 'Không thể tải thông tin máy.'
        );
        return;
      }

      setClientState({ clientId, data: clientRes.value });
      setSnapshotError(null);

      // Thiếu quyền xem cảnh báo thì vẫn hiển thị máy, chỉ không có danh sách cảnh báo
      if (alertsRes.status === 'fulfilled') {
        setAlertsState({ clientId, data: alertsRes.value });
      }
    }, SNAPSHOT_REFRESH_MS);
  }, [clientId]);

  // Thông tin Agent (phiên bản, trạng thái)
  useEffect(() => {
    if (!clientId) return;
    return startPolling(async () => {
      try {
        const data = await getClientAgentByClientId(clientId);
        setAgentState({ clientId, data });
      } catch {
        // Máy chưa có Agent hoặc thiếu quyền AGENT_VIEW: bỏ qua, hiển thị "—"
        setAgentState((prev) => prev ?? { clientId, data: null });
      }
    }, 10_000);
  }, [clientId]);

  // Số liệu mới nhất (gồm ổ đĩa, mạng, tiến trình)
  useEffect(() => {
    if (!clientId) return;
    return startPolling(async () => {
      try {
        const data = await getLatestMetrics(clientId);
        setLatestState({ clientId, data });
      } catch {
        // Giữ lại số liệu cũ nếu lần tải này lỗi
      }
    }, LATEST_REFRESH_MS);
  }, [clientId]);

  // Phần cứng tĩnh (CPU, RAM): chỉ tải một lần khi đổi máy
  useEffect(() => {
    if (!clientId) return;
    let cancelled = false;
    getClientHardware(clientId)
      .then((data) => {
        if (!cancelled) setHardwareState({ clientId, data });
      })
      .catch(() => {
        if (!cancelled) setHardwareState({ clientId, data: null });
      });
    return () => {
      cancelled = true;
    };
  }, [clientId]);

  // Lịch sử theo khoảng thời gian đang chọn
  useEffect(() => {
    if (!clientId) return;
    return subscribeMetricHistory(
      clientId,
      (points) => {
        setHistory({ clientId, minutes, points });
        setHistoryError(null);
      },
      (err) => setHistoryError({ clientId, message: err.message }),
      minutes,
      HISTORY_REFRESH_MS
    );
  }, [clientId, minutes]);

  // ----------------------------------------------------------
  // Dữ liệu dẫn xuất
  // ----------------------------------------------------------
  const client = clientState?.clientId === clientId ? clientState.data : undefined;
  const isLoading = client === undefined && snapshotError === null;
  const agent = agentState?.clientId === clientId ? agentState.data : null;
  const latest = latestState?.clientId === clientId ? latestState.data : null;
  const hardware = hardwareState?.clientId === clientId ? hardwareState.data : null;
  const totalRamBytes = latest?.memory?.totalBytes ?? hardware?.memory?.totalBytes ?? null;

  const alerts: AlertInfo[] = useMemo(
    () => (alertsState?.clientId === clientId ? alertsState.data : []),
    [alertsState, clientId]
  );
  const openAlerts = alerts.filter((a) => a.status !== 'RESOLVED').length;

  const chartData = useMemo(() => {
    if (!history || history.clientId !== clientId || history.minutes !== minutes) return [];
    return history.points
      .map((p) => ({ date: toDate(p.collectedAt), p }))
      .filter((item): item is { date: Date; p: HistoryPoint } => item.date !== null)
      .sort((a, b) => a.date.getTime() - b.date.getTime())
      .map(({ date, p }) => ({
        time: date.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }),
        CPU: p.cpuUsagePercent,
        RAM: p.memoryUsagePercent,
        Disk: p.diskUsagePercent,
        Download: p.networkDownloadSpeed,
        Upload: p.networkUploadSpeed,
      }));
  }, [history, clientId, minutes]);

  const chartError = historyError && historyError.clientId === clientId ? historyError.message : null;

  const processes = useMemo(() => {
    const keyword = processSearch.trim().toLowerCase();
    return [...(latest?.processes ?? [])]
      .filter((p) => keyword === '' || p.name.toLowerCase().includes(keyword) || String(p.pid).includes(keyword))
      .sort((a, b) => b.cpuUsage - a.cpuUsage);
  }, [latest, processSearch]);

  const status: ClientStatus = client?.status ?? 'UNKNOWN';
  const pill = STATUS_PILL[status] ?? STATUS_PILL.UNKNOWN;
  const isOnline = status === 'ONLINE';

  const handleRemoteDesktop = () => {
    if (!isOnline) {
      setCommandMessage({
        type: 'error',
        text: 'Không thể kết nối Remote Desktop vì máy đang ngoại tuyến.',
      });
      return;
    }

    navigate(`/remote-desktop?clientId=${encodeURIComponent(clientId)}`);
  };

  const handleRemoteCommand = async (command: 'RESTART' | 'SHUTDOWN') => {
    if (!clientId || !isOnline || commandLoading) return;

    const isRestart = command === 'RESTART';
    const confirmed = window.confirm(
      isRestart
        ? `Bạn có chắc chắn muốn khởi động lại máy "${client?.hostname ?? clientId}" không?`
        : `Bạn có chắc chắn muốn tắt máy "${client?.hostname ?? clientId}" không?`
    );

    if (!confirmed) return;

    try {
      setCommandLoading(command);
      setCommandMessage(null);
      await createRemoteCommand(clientId, command, {} as Parameters<typeof createRemoteCommand>[2]);
      setCommandMessage({
        type: 'success',
        text: isRestart
          ? `Đã gửi yêu cầu khởi động lại máy ${client?.hostname ?? clientId}. Đang chờ Agent thực hiện.`
          : `Đã gửi yêu cầu tắt máy ${client?.hostname ?? clientId}. Đang chờ Agent thực hiện.`,
      });
    } catch (error) {
      setCommandMessage({
        type: 'error',
        text: error instanceof Error ? error.message : 'Không thể gửi lệnh đến máy tính.',
      });
    } finally {
      setCommandLoading(null);
    }
  };

  // Chỉ máy ONLINE mới có số liệu "đang chạy". Còn lại là bản ghi cuối cùng, chỉ để tham khảo.
  const stale = !isOnline;
  const dataAt = formatDateTime(latest?.collectedAt);
  const os = OS_STYLE[detectOs(client?.osName ?? '')];

  const bootTime = toDate(client?.bootTime);
  const uptime = isOnline && bootTime ? formatUptime(Date.now() - bootTime.getTime()) : '—';

  const diskTotal = latest?.disks.reduce((s, d) => s + d.totalBytes, 0) ?? 0;
  const diskUsed = latest?.disks.reduce((s, d) => s + d.usedBytes, 0) ?? 0;
  const netDown = latest?.networks.reduce((s, n) => s + (n.downloadSpeed ?? 0), 0) ?? 0;
  const netUp = latest?.networks.reduce((s, n) => s + (n.uploadSpeed ?? 0), 0) ?? 0;

  // ----------------------------------------------------------
  // Style dùng chung
  // ----------------------------------------------------------
  const card = `rounded-2xl backdrop-blur-xl border transition-colors duration-500 ${
    isDark
      ? 'bg-[#081226]/80 border-blue-500/20 text-white shadow-[0_0_32px_-10px_rgba(37,99,235,0.45)]'
      : 'bg-white border-slate-200 text-slate-900 shadow-sm'
  }`;
  const innerCard = isDark ? 'bg-[#050c1c]/70 border-blue-500/15' : 'bg-slate-50 border-slate-200';
  const muted = isDark ? 'text-slate-400' : 'text-slate-500';
  const subtle = isDark ? 'text-slate-300' : 'text-slate-600';
  const track = isDark ? 'bg-slate-700/60' : 'bg-slate-200';
  const ringTrack = isDark ? '#1e293b' : '#e2e8f0';
  const divider = isDark ? 'border-blue-500/10' : 'border-slate-100';
  const fieldClass = `rounded-xl border px-3 py-2.5 text-sm font-medium focus:outline-none focus:ring-1 focus:ring-cyan-400 ${
    isDark ? 'bg-[#050c1c] border-blue-500/25 text-slate-200' : 'bg-slate-50 border-slate-300 text-slate-700'
  }`;
  const tooltipStyle = {
    backgroundColor: isDark ? '#0B1527' : '#ffffff',
    border: `1px solid ${isDark ? '#1e3a8a' : '#e2e8f0'}`,
    borderRadius: 12,
    fontSize: 12,
  };
  const gridColor = isDark ? '#1e293b' : '#e2e8f0';
  const axisColor = isDark ? '#64748b' : '#94a3b8';
  const chipClass = (active: boolean) =>
    `px-3.5 py-1.5 rounded-lg text-sm font-semibold transition-colors ${
      active
        ? 'bg-cyan-500 text-slate-950'
        : isDark
          ? 'bg-blue-600/15 border border-blue-500/30 text-slate-300 hover:bg-blue-600/30'
          : 'bg-blue-50 border border-blue-200 text-blue-700 hover:bg-blue-100'
    }`;

  const tabs: { key: TabKey; label: string; badge?: number }[] = [
    { key: 'overview', label: 'Tổng quan' },
    { key: 'hardware', label: 'Phần cứng & Mạng' },
    { key: 'processes', label: 'Tiến trình' },
    { key: 'alerts', label: 'Cảnh báo', badge: openAlerts },
  ];

  const gauges = [
    {
      key: 'cpu',
      label: 'CPU',
      icon: <Cpu className="w-4 h-4" />,
      value: latest?.cpu?.usagePercent,
      base: '#22d3ee',
      sub: `Load 1 phút: ${typeof latest?.cpu?.loadAverage1m === 'number' ? latest.cpu.loadAverage1m.toFixed(2) : '--'}`,
    },
    {
      key: 'ram',
      label: 'RAM',
      icon: <Server className="w-4 h-4" />,
      value: latest?.memory?.usagePercent,
      base: '#a78bfa',
      sub: `${fmtBytes(latest?.memory?.usedBytes)} / ${fmtBytes(latest?.memory?.totalBytes)}`,
    },
    {
      key: 'disk',
      label: 'Disk',
      icon: <HardDrive className="w-4 h-4" />,
      value: diskPercent(latest),
      base: '#fbbf24',
      sub: latest && latest.disks.length > 0 ? `${fmtBytes(diskUsed)} / ${fmtBytes(diskTotal)}` : 'Chưa có dữ liệu',
    },
  ];

  // Một dòng cảnh báo dùng chung cho tab Tổng quan và tab Cảnh báo
  const renderAlert = (alert: AlertInfo) => {
    const severity = SEVERITY_STYLE[alert.severity] ?? SEVERITY_STYLE.INFO;
    const resolved = alert.status === 'RESOLVED';
    const color = resolved ? '#64748b' : severity.color;
    const unit = PERCENT_RESOURCES.includes(alert.resourceType) ? '%' : '';

    return (
      <li
        key={alert.alertId}
        className={`p-4 rounded-2xl border flex items-start justify-between gap-3 ${innerCard} ${resolved ? 'opacity-70' : ''}`}
      >
        <div className="flex items-start gap-3 min-w-0">
          <span
            className="w-2.5 h-2.5 rounded-full mt-1.5 shrink-0"
            style={{ backgroundColor: color, boxShadow: `0 0 10px ${color}` }}
          />
          <div className="min-w-0">
            <p className="text-sm font-bold truncate" title={alert.message}>
              {alert.ruleName || RESOURCE_LABEL[alert.resourceType] || 'Cảnh báo'}
            </p>
            <p className={`text-xs mt-1 font-mono ${muted}`}>
              {RESOURCE_LABEL[alert.resourceType]} {Number(alert.currentValue).toFixed(1)}
              {unit} / ngưỡng {Number(alert.thresholdValue).toFixed(1)}
              {unit}
            </p>
            <p className="text-xs mt-1 font-mono text-cyan-400">{formatDateTime(alert.createdAt)}</p>
          </div>
        </div>
        <span
          className="px-2.5 py-1 rounded-full text-xs font-semibold border shrink-0"
          style={{ color, borderColor: `${color}66`, backgroundColor: `${color}1f` }}
        >
          {resolved ? 'Đã xử lý' : alert.status === 'ACKNOWLEDGED' ? 'Đã ghi nhận' : severity.label}
        </span>
      </li>
    );
  };

  // ----------------------------------------------------------
  // Trạng thái tải / không tìm thấy
  // ----------------------------------------------------------
  if (isLoading) {
    return (
      <div className={`${card} p-16 text-center text-sm ${muted}`}>
        <Loader2 className="w-6 h-6 animate-spin inline mr-2 text-cyan-400" />
        Đang tải thông tin máy...
      </div>
    );
  }

  if (!client) {
    return (
      <div className={`${card} p-14 flex flex-col items-center text-center gap-4`}>
        <div className="p-4 rounded-2xl bg-red-500/10 border border-red-500/25">
          <WifiOff className="w-8 h-8 text-red-400" />
        </div>
        <div>
          <p className="text-lg font-bold">Không tìm thấy máy này</p>
          <p className={`text-sm mt-1 ${muted}`}>
            {snapshotError ?? 'Máy có thể đã bị xóa hoặc đường dẫn không đúng.'}
          </p>
        </div>
        <button
          onClick={() => navigate('/computers')}
          className="px-5 py-2.5 rounded-xl bg-gradient-to-r from-blue-600 to-cyan-500 text-white text-sm font-bold shadow-[0_0_24px_rgba(34,211,238,0.45)] flex items-center gap-2"
        >
          <ArrowLeft className="w-4 h-4" />
          Về danh sách máy
        </button>
      </div>
    );
  }

  // ----------------------------------------------------------
  // Giao diện
  // ----------------------------------------------------------
  return (
    <div className="space-y-5 pb-12">

      {/* 1. BREADCRUMB */}
      <div className={`flex flex-wrap items-center gap-2 text-sm ${muted}`}>
        <button
          onClick={() => navigate('/computers')}
          className="flex items-center gap-1.5 hover:text-cyan-400 transition-colors"
        >
          <ArrowLeft className="w-4 h-4" />
          Computers
        </button>
        <ChevronRight className="w-3.5 h-3.5" />
        <span>Danh sách máy</span>
        <ChevronRight className="w-3.5 h-3.5" />
        <span className={isDark ? 'text-slate-200' : 'text-slate-700'}>{client.hostname}</span>
      </div>

      {/* Cảnh báo mất kết nối tới máy chủ */}
      {snapshotError && (
        <div className="flex items-start gap-3 p-4 rounded-2xl border bg-red-500/10 border-red-500/30 text-red-400 text-sm">
          <WifiOff className="w-5 h-5 shrink-0 mt-0.5" />
          <p>Mất kết nối tới máy chủ, đang hiển thị dữ liệu cũ: {snapshotError}</p>
        </div>
      )}

      {/* 2. BANNER THÔNG TIN MÁY */}
      <div className={`${card} relative overflow-hidden p-6 lg:p-7`}>
        <div className="absolute -top-24 -right-16 w-96 h-96 rounded-full bg-cyan-500/10 blur-3xl pointer-events-none" />
        <div className="absolute -bottom-28 left-1/3 w-72 h-72 rounded-full bg-blue-600/10 blur-3xl pointer-events-none" />

        <div className="relative z-10 flex flex-col xl:flex-row justify-between items-start xl:items-center gap-6">
          <div className="flex items-center gap-5 min-w-0">
            <div
              className="w-16 h-16 rounded-2xl flex items-center justify-center text-2xl font-black text-white shrink-0"
              style={{ backgroundColor: os.color, boxShadow: `0 0 28px ${os.color}66` }}
            >
              {os.letter}
            </div>
            <div className="min-w-0 space-y-2">
              <div className="flex flex-wrap items-center gap-3">
                <h1 className="text-2xl sm:text-3xl font-black tracking-tight truncate">{client.hostname}</h1>
                <span
                  className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold border ${pill.className}`}
                >
                  <span className={`w-2 h-2 rounded-full ${pill.dot} ${isOnline ? 'animate-pulse' : ''}`} />
                  {pill.label}
                </span>
              </div>
              <div className={`flex flex-wrap items-center gap-x-5 gap-y-1.5 text-sm ${subtle}`}>
                <span className="flex items-center gap-1.5 font-mono text-cyan-400">
                  <Monitor className="w-4 h-4" />
                  {client.ipAddress}
                </span>
                <span className="flex items-center gap-1.5">
                  <Server className="w-4 h-4 text-blue-400" />
                  {[client.osName, client.osVersion].filter(Boolean).join(' ') || 'Hệ điều hành chưa rõ'}
                </span>
                <span className="flex items-center gap-1.5">
                  <Clock className="w-4 h-4 text-purple-400" />
                  {isOnline ? `Hoạt động ${uptime}` : `Thấy lần cuối ${timeAgo(client.lastSeenAt)}`}
                </span>
              </div>
            </div>
          </div>

          {/* Thao tác nhanh */}
          <div className="flex flex-wrap items-center gap-3">
            <button
              type="button"
              onClick={handleRemoteDesktop}
              disabled={!isOnline}
              title={isOnline ? 'Mở phiên điều khiển màn hình từ xa' : 'Máy đang ngoại tuyến'}
              className={`px-4 py-2.5 rounded-xl bg-gradient-to-r from-blue-600 to-cyan-500 text-white text-sm font-bold shadow-[0_0_24px_rgba(34,211,238,0.35)] flex items-center gap-2 transition ${
                isOnline ? 'hover:brightness-110' : 'opacity-50 cursor-not-allowed'
              }`}
            >
              <Tv className="w-4 h-4" />
              Remote Desktop
            </button>
            <button
              type="button"
              onClick={() => void handleRemoteCommand('RESTART')}
              disabled={!isOnline || commandLoading !== null}
              title={isOnline ? 'Gửi lệnh khởi động lại máy' : 'Máy đang ngoại tuyến'}
              className={`px-4 py-2.5 rounded-xl border text-sm font-bold flex items-center gap-2 transition ${
                isDark ? 'bg-slate-900/60 border-slate-700 text-slate-200' : 'bg-white border-slate-300 text-slate-700'
              } ${!isOnline || commandLoading !== null ? 'opacity-50 cursor-not-allowed' : 'hover:border-cyan-400 hover:text-cyan-400'}`}
            >
              {commandLoading === 'RESTART' ? <Loader2 className="w-4 h-4 animate-spin" /> : <RotateCw className="w-4 h-4" />}
              {commandLoading === 'RESTART' ? 'Đang gửi...' : 'Restart'}
            </button>
            <button
              type="button"
              onClick={() => void handleRemoteCommand('SHUTDOWN')}
              disabled={!isOnline || commandLoading !== null}
              title={isOnline ? 'Gửi lệnh tắt máy' : 'Máy đang ngoại tuyến'}
              className={`px-4 py-2.5 rounded-xl bg-red-600/20 border border-red-500/40 text-red-400 text-sm font-bold flex items-center gap-2 transition ${
                !isOnline || commandLoading !== null ? 'opacity-50 cursor-not-allowed' : 'hover:bg-red-600/30'
              }`}
            >
              {commandLoading === 'SHUTDOWN' ? <Loader2 className="w-4 h-4 animate-spin" /> : <Power className="w-4 h-4" />}
              {commandLoading === 'SHUTDOWN' ? 'Đang gửi...' : 'Shutdown'}
            </button>
          </div>
        </div>
      </div>

      {commandMessage && (
        <div
          role="status"
          className={`flex items-start gap-3 p-4 rounded-2xl border text-sm ${
            commandMessage.type === 'success'
              ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-400'
              : 'bg-red-500/10 border-red-500/30 text-red-400'
          }`}
        >
          {commandMessage.type === 'success' ? (
            <CheckCircle2 className="w-5 h-5 shrink-0 mt-0.5" />
          ) : (
            <ShieldAlert className="w-5 h-5 shrink-0 mt-0.5" />
          )}
          <p className="flex-1">{commandMessage.text}</p>
          <button
            type="button"
            onClick={() => setCommandMessage(null)}
            className="shrink-0 opacity-70 hover:opacity-100"
            aria-label="Đóng thông báo"
          >
            ✕
          </button>
        </div>
      )}

      {/* Ghi chú khi máy ngoại tuyến */}
      {!isOnline && (
        <div className="flex items-start gap-3 p-4 rounded-2xl border bg-amber-500/10 border-amber-500/30 text-amber-400 text-sm">
          <ShieldAlert className="w-5 h-5 shrink-0 mt-0.5" />
          <p>
            Máy này hiện không trực tuyến.
            {latest
              ? ` Đang hiển thị bản ghi cuối cùng lúc ${dataAt} (${timeAgo(latest.collectedAt)}). Số liệu được làm mờ; tốc độ mạng và trạng thái tiến trình không còn phản ánh thực tế nên không hiển thị.`
              : ' Chưa có số liệu nào được ghi nhận.'}
          </p>
        </div>
      )}

      {/* 3. TAB ĐIỀU HƯỚNG */}
      <div
        className={`flex items-center gap-2 p-1.5 rounded-2xl border backdrop-blur-xl overflow-x-auto ${
          isDark ? 'bg-[#081226]/80 border-blue-500/20' : 'bg-white border-slate-200'
        }`}
      >
        {tabs.map((t) => (
          <button
            key={t.key}
            onClick={() => setTab(t.key)}
            className={`px-5 py-2.5 rounded-xl text-sm font-semibold whitespace-nowrap transition-all flex items-center gap-2 ${
              tab === t.key
                ? 'bg-gradient-to-r from-blue-600 to-cyan-500 text-white shadow-[0_0_22px_rgba(34,211,238,0.35)]'
                : isDark
                  ? 'text-slate-300 hover:text-white hover:bg-blue-600/15'
                  : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100'
            }`}
          >
            {t.label}
            {!!t.badge && t.badge > 0 && (
              <span className="min-w-5 h-5 px-1.5 inline-flex items-center justify-center text-xs font-bold bg-red-500 text-white rounded-full">
                {t.badge}
              </span>
            )}
          </button>
        ))}
      </div>

      {/* ======================= TAB: TỔNG QUAN ======================= */}
      {tab === 'overview' && (
        <>
          {/* Các chỉ số hiện tại */}
          <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
            {gauges.map((g) => {
              const color = levelColor(g.value, g.base, stale);
              return (
                <div key={g.key} className={`${card} p-5 ${stale ? 'opacity-70' : ''}`}>
                  <div className="flex items-center gap-4">
                    <Ring value={g.value} color={color} track={ringTrack} dim={stale}>
                      <span className="text-lg font-black leading-none">
                        {typeof g.value === 'number' ? `${g.value.toFixed(0)}%` : '--'}
                      </span>
                    </Ring>
                    <div className="min-w-0">
                      <p className={`flex items-center gap-2 text-sm font-semibold ${subtle}`}>
                        <span style={{ color }}>{g.icon}</span>
                        {g.label}
                      </p>
                      <p className="text-2xl font-black leading-tight mt-1">{fmtPct(g.value)}</p>
                      <p className={`text-xs mt-1 font-mono truncate ${muted}`}>{g.sub}</p>
                      {stale && <p className="text-[11px] mt-1 font-semibold text-amber-400/80">Số liệu cuối cùng</p>}
                    </div>
                  </div>
                </div>
              );
            })}

            <div className={`${card} p-5 ${stale ? 'opacity-70' : ''}`}>
              <p className={`flex items-center gap-2 text-sm font-semibold mb-3 ${subtle}`}>
                <Network className="w-4 h-4 text-emerald-400" />
                Mạng
              </p>
              <div className="space-y-2.5">
                <div className="flex items-center justify-between gap-2">
                  <span className="flex items-center gap-2 text-sm text-emerald-400 font-semibold">
                    <ArrowDown className="w-4 h-4" />
                    Tải xuống
                  </span>
                  <span className="font-mono text-base font-bold">{stale ? '—' : `${fmtBytes(netDown)}/s`}</span>
                </div>
                <div className="flex items-center justify-between gap-2">
                  <span className="flex items-center gap-2 text-sm text-pink-400 font-semibold">
                    <ArrowUp className="w-4 h-4" />
                    Tải lên
                  </span>
                  <span className="font-mono text-base font-bold">{stale ? '—' : `${fmtBytes(netUp)}/s`}</span>
                </div>
              </div>
              <p className={`text-xs mt-3 pt-3 border-t ${divider} ${muted}`}>
                {latest?.networks.length ?? 0} card mạng
                {stale ? ' · không có tốc độ khi máy offline' : ''}
              </p>
            </div>
          </div>

          {/* Biểu đồ + thông tin hệ thống */}
          <div className="grid grid-cols-1 xl:grid-cols-12 gap-5">
            <div className={`${card} xl:col-span-8 p-5`}>
              <PanelTitle
                icon={<Activity className="w-5 h-5" />}
                title="Lịch sử hoạt động"
                right={
                  <div className="flex flex-wrap items-center gap-2">
                    {stale && latest && <StaleBadge at={dataAt} isDark={isDark} />}
                    {RANGES.map((r) => (
                      <button key={r.minutes} onClick={() => setMinutes(r.minutes)} className={chipClass(minutes === r.minutes)}>
                        {r.label}
                      </button>
                    ))}
                  </div>
                }
              />

              <div className="flex flex-wrap items-center justify-between gap-3 mb-3">
                <div className="flex items-center gap-2">
                  <button onClick={() => setChartMode('resource')} className={chipClass(chartMode === 'resource')}>
                    Tài nguyên
                  </button>
                  <button onClick={() => setChartMode('network')} className={chipClass(chartMode === 'network')}>
                    Mạng
                  </button>
                </div>
                <div className="flex items-center gap-5 text-sm">
                  {(chartMode === 'resource'
                    ? [
                        ['CPU', '#22d3ee'],
                        ['RAM', '#a78bfa'],
                        ['Disk', '#fbbf24'],
                      ]
                    : [
                        ['Tải xuống', '#34d399'],
                        ['Tải lên', '#f472b6'],
                      ]
                  ).map(([name, color]) => (
                    <span key={name} className="flex items-center gap-2">
                      <span className="w-3 h-3 rounded-full" style={{ backgroundColor: color }} />
                      <span className={subtle}>{name}</span>
                    </span>
                  ))}
                </div>
              </div>

              <div className={`h-80 w-full rounded-2xl border p-4 ${innerCard}`}>
                {chartData.length > 0 ? (
                  <ResponsiveContainer width="100%" height="100%">
                    <AreaChart
                      data={chartData}
                      margin={{ top: 5, right: 10, left: chartMode === 'resource' ? -16 : 8, bottom: 0 }}
                    >
                      <defs>
                        <linearGradient id="detCpuFill" x1="0" y1="0" x2="0" y2="1">
                          <stop offset="0%" stopColor="#22d3ee" stopOpacity={0.35} />
                          <stop offset="100%" stopColor="#22d3ee" stopOpacity={0} />
                        </linearGradient>
                        <linearGradient id="detDownFill" x1="0" y1="0" x2="0" y2="1">
                          <stop offset="0%" stopColor="#34d399" stopOpacity={0.35} />
                          <stop offset="100%" stopColor="#34d399" stopOpacity={0} />
                        </linearGradient>
                      </defs>
                      <CartesianGrid strokeDasharray="3 3" stroke={gridColor} />
                      <XAxis dataKey="time" tick={{ fontSize: 11, fill: axisColor }} minTickGap={40} />
                      {chartMode === 'resource' ? (
                        <YAxis domain={[0, 100]} unit="%" tick={{ fontSize: 11, fill: axisColor }} />
                      ) : (
                        <YAxis width={64} tickFormatter={(v) => fmtBytes(Number(v))} tick={{ fontSize: 11, fill: axisColor }} />
                      )}
                      <Tooltip
                        contentStyle={tooltipStyle}
                        formatter={(value) =>
                          chartMode === 'resource' ? `${Number(value).toFixed(1)}%` : `${fmtBytes(Number(value))}/s`
                        }
                      />
                      {chartMode === 'resource' ? (
                        <>
                          <Area type="monotone" dataKey="CPU" stroke="#22d3ee" strokeWidth={2} fill="url(#detCpuFill)" dot={false} isAnimationActive={false} />
                          <Area type="monotone" dataKey="RAM" stroke="#a78bfa" strokeWidth={2} fill="none" dot={false} isAnimationActive={false} />
                          <Area type="monotone" dataKey="Disk" stroke="#fbbf24" strokeWidth={2} fill="none" dot={false} isAnimationActive={false} />
                        </>
                      ) : (
                        <>
                          <Area type="monotone" dataKey="Download" name="Tải xuống" stroke="#34d399" strokeWidth={2} fill="url(#detDownFill)" dot={false} isAnimationActive={false} />
                          <Area type="monotone" dataKey="Upload" name="Tải lên" stroke="#f472b6" strokeWidth={2} fill="none" dot={false} isAnimationActive={false} />
                        </>
                      )}
                    </AreaChart>
                  </ResponsiveContainer>
                ) : (
                  <div className={`h-full flex flex-col items-center justify-center gap-3 text-center px-6 ${muted}`}>
                    <div className="p-3.5 rounded-2xl bg-blue-600/10 border border-blue-500/20">
                      <Activity className="w-7 h-7 text-cyan-400" />
                    </div>
                    <p className="text-sm max-w-md">
                      {chartError ?? `Máy này chưa gửi dữ liệu trong ${RANGES.find((r) => r.minutes === minutes)?.label ?? ''} gần nhất.`}
                    </p>
                  </div>
                )}
              </div>
            </div>

            <div className={`${card} xl:col-span-4 p-5`}>
              <PanelTitle icon={<Server className="w-5 h-5" />} title="Thông tin hệ thống" />
              <div>
                <InfoRow label="Tên máy" value={client.hostname} divider={divider} muted={muted} />
                <InfoRow label="Địa chỉ IP" value={client.ipAddress} mono divider={divider} muted={muted} />
                <InfoRow
                  label="Hệ điều hành"
                  value={[client.osName, client.osVersion].filter(Boolean).join(' ') || '—'}
                  divider={divider}
                  muted={muted}
                />
                <InfoRow label="Kernel" value={client.kernelVersion || '—'} mono divider={divider} muted={muted} />
                <InfoRow
                  label="Trạng thái"
                  value={pill.label}
                  valueClass={isOnline ? 'text-emerald-400' : status === 'OFFLINE' ? 'text-red-400' : 'text-amber-400'}
                  divider={divider}
                  muted={muted}
                />
                <InfoRow label="Thời gian hoạt động" value={uptime} divider={divider} muted={muted} />
                <InfoRow
                  label="Agent"
                  value={
                    agent?.version
                      ? `v${agent.version.replace(/^v/i, '')}${agent.status ? ` · ${AGENT_LABEL[agent.status]}` : ''}`
                      : '—'
                  }
                  divider={divider}
                  muted={muted}
                />
                <InfoRow label="Thấy lần cuối" value={formatDateTime(client.lastSeenAt)} mono divider={divider} muted={muted} />
                <InfoRow label="Đăng ký lúc" value={formatDateTime(client.createdAt)} mono divider={divider} muted={muted} />
              </div>
              {client.managementNote && (
                <p className={`mt-4 p-3 rounded-xl border text-sm ${innerCard} ${subtle}`}>
                  <span className={`block text-xs font-semibold mb-1 ${muted}`}>Ghi chú quản lý</span>
                  {client.managementNote}
                </p>
              )}
            </div>
          </div>

          {/* Cảnh báo gần đây + tiến trình nổi bật */}
          <div className="grid grid-cols-1 xl:grid-cols-2 gap-5">
            <div className={`${card} p-5`}>
              <PanelTitle
                icon={<Bell className="w-5 h-5" />}
                title="Cảnh báo gần đây"
                right={
                  <button onClick={() => setTab('alerts')} className="text-sm text-cyan-400 font-semibold hover:underline">
                    Xem tất cả
                  </button>
                }
              />
              {alerts.length === 0 ? (
                <div className={`py-10 flex flex-col items-center text-center gap-3 ${muted}`}>
                  <div className="p-4 rounded-2xl bg-emerald-500/10 border border-emerald-500/25">
                    <CheckCircle2 className="w-7 h-7 text-emerald-400" />
                  </div>
                  <p className="text-sm">Máy này chưa có cảnh báo nào.</p>
                </div>
              ) : (
                <ul className="space-y-3">{alerts.slice(0, 3).map(renderAlert)}</ul>
              )}
            </div>

            <div className={`${card} p-5`}>
              <PanelTitle
                icon={<Cpu className="w-5 h-5" />}
                title={stale ? 'Tiến trình dùng nhiều CPU (lần cuối)' : 'Tiến trình dùng nhiều CPU'}
                right={
                  <button onClick={() => setTab('processes')} className="text-sm text-cyan-400 font-semibold hover:underline">
                    Xem tất cả
                  </button>
                }
              />
              {!latest || latest.processes.length === 0 ? (
                <p className={`py-10 text-sm text-center ${muted}`}>Chưa có dữ liệu tiến trình.</p>
              ) : (
                <ul className={`space-y-3 ${stale ? 'opacity-60' : ''}`}>
                  {[...latest.processes]
                    .sort((a, b) => b.cpuUsage - a.cpuUsage)
                    .slice(0, 5)
                    .map((p) => (
                      <li key={`${p.pid}-${p.name}`} className="space-y-1.5">
                        <div className="flex items-center justify-between gap-3 text-sm">
                          <span className="font-semibold truncate">
                            {p.name} <span className={`font-mono text-xs ${muted}`}>#{p.pid}</span>
                          </span>
                          <span className="font-mono text-xs font-semibold shrink-0">
                            {fmtPct(p.cpuUsage)} · {fmtBytes(p.memoryUsage)}
                          </span>
                        </div>
                        <UsageBar value={p.cpuUsage} track={track} stale={stale} />
                      </li>
                    ))}
                </ul>
              )}
            </div>
          </div>
        </>
      )}

      {/* ======================= TAB: Ổ ĐĨA & MẠNG ======================= */}
      {tab === 'hardware' && (
        <div className="grid grid-cols-1 xl:grid-cols-12 gap-5">
          {/* Cấu hình phần cứng (CPU, RAM) */}
          <div className={`${card} xl:col-span-12 p-5`}>
            <PanelTitle icon={<Cpu className="w-5 h-5" />} title="Cấu hình phần cứng" />

            {!hardware || (!hardware.cpu && !hardware.memory) ? (
              <p className={`py-6 text-sm text-center ${muted}`}>
                Chưa có thông tin phần cứng. Máy này đăng ký bằng Agent phiên bản cũ.
              </p>
            ) : (
              <div className="grid grid-cols-2 lg:grid-cols-5 gap-3">
                {[
                  { label: 'Bộ xử lý (CPU)', value: hardware.cpu?.modelName || '--', wide: true },
                  { label: 'Hãng', value: hardware.cpu?.manufacturer || '--' },
                  { label: 'Số nhân / luồng', value: `${fmtCount(hardware.cpu?.coreCount)} / ${fmtCount(hardware.cpu?.threadCount)}` },
                  { label: 'Tần số tối đa', value: fmtGhz(hardware.cpu?.frequencyMhz) },
                  { label: 'Tổng RAM', value: fmtBytes(hardware.memory?.totalBytes) },
                ].map((item) => (
                  <div
                    key={item.label}
                    className={`p-4 rounded-2xl border ${innerCard} ${item.wide ? 'col-span-2 lg:col-span-2' : ''}`}
                  >
                    <p className={`text-xs ${muted}`}>{item.label}</p>
                    <p className="mt-1 text-sm font-bold break-words">{item.value}</p>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Bộ nhớ + ổ đĩa */}
          <div className={`${card} xl:col-span-6 p-5`}>
            <PanelTitle
              icon={<HardDrive className="w-5 h-5" />}
              title="Bộ nhớ và ổ đĩa"
              right={stale && latest ? <StaleBadge at={dataAt} isDark={isDark} /> : undefined}
            />

            <div className={`p-4 rounded-2xl border flex items-center gap-5 mb-4 ${innerCard} ${stale ? 'opacity-60' : ''}`}>
              <Ring
                value={latest?.memory?.usagePercent}
                color={levelColor(latest?.memory?.usagePercent, '#a78bfa', stale)}
                track={ringTrack}
                dim={stale}
              >
                <span className="text-lg font-black leading-none">
                  {typeof latest?.memory?.usagePercent === 'number' ? `${latest.memory.usagePercent.toFixed(0)}%` : '--'}
                </span>
              </Ring>
              <div className="flex-1 space-y-1.5 text-sm">
                <p className="font-bold text-base">Bộ nhớ RAM</p>
                <p className={`flex justify-between ${subtle}`}>
                  <span>Đã dùng</span>
                  <span className="font-mono font-semibold">{fmtBytes(latest?.memory?.usedBytes)}</span>
                </p>
                <p className={`flex justify-between ${subtle}`}>
                  <span>Còn trống</span>
                  <span className="font-mono font-semibold">{fmtBytes(latest?.memory?.availableBytes)}</span>
                </p>
                <p className={`flex justify-between ${subtle}`}>
                  <span>Tổng</span>
                  <span className="font-mono font-semibold">{fmtBytes(totalRamBytes)}</span>
                </p>
              </div>
            </div>

            {!latest || latest.disks.length === 0 ? (
              <p className={`py-8 text-sm text-center ${muted}`}>Chưa có thông tin ổ đĩa.</p>
            ) : (
              <ul className="space-y-3">
                {latest.disks.map((disk) => (
                  <li key={disk.diskId} className={`p-4 rounded-2xl border space-y-2.5 ${innerCard} ${stale ? 'opacity-60' : ''}`}>
                    <div className="flex items-center justify-between gap-3">
                      <div className="min-w-0">
                        <p className="text-sm font-bold truncate">{disk.mountPoint || disk.deviceName}</p>
                        <p className={`text-xs font-mono truncate ${muted}`}>
                          {disk.deviceName}
                          {disk.fileSystem ? ` · ${disk.fileSystem}` : ''}
                        </p>
                      </div>
                      <span className="font-mono text-sm font-bold shrink-0" style={{ color: levelColor(disk.usagePercent, '#22d3ee', stale) }}>
                        {fmtPct(disk.usagePercent)}
                      </span>
                    </div>
                    <UsageBar value={disk.usagePercent} track={track} stale={stale} />
                    <p className={`text-xs font-mono ${muted}`}>
                      {fmtBytes(disk.usedBytes)} đã dùng / {fmtBytes(disk.totalBytes)} · còn trống {fmtBytes(disk.freeBytes)}
                    </p>
                  </li>
                ))}
              </ul>
            )}
          </div>

          {/* Card mạng */}
          <div className={`${card} xl:col-span-6 p-5`}>
            <PanelTitle
              icon={<Network className="w-5 h-5" />}
              title="Card mạng"
              right={stale && latest ? <StaleBadge at={dataAt} isDark={isDark} /> : undefined}
            />

            {!latest || latest.networks.length === 0 ? (
              <p className={`py-8 text-sm text-center ${muted}`}>Chưa có thông tin card mạng.</p>
            ) : (
              <ul className="space-y-3">
                {latest.networks.map((nic) => {
                  // Máy offline thì không biết card mạng còn UP hay không
                  const up = !stale && (nic.status ?? '').toUpperCase() === 'UP';
                  return (
                    <li key={nic.interfaceId} className={`p-4 rounded-2xl border space-y-3 ${innerCard} ${stale ? 'opacity-60' : ''}`}>
                      <div className="flex items-center justify-between gap-3">
                        <div className="min-w-0">
                          <p className="text-sm font-bold truncate">{nic.interfaceName}</p>
                          {nic.description && (
                            <p className={`text-xs truncate ${muted}`} title={nic.description}>
                              {nic.description}
                            </p>
                          )}
                        </div>
                        <span
                          className={`px-2.5 py-1 rounded-full text-xs font-semibold border shrink-0 ${
                            up
                              ? 'bg-emerald-500/15 text-emerald-400 border-emerald-500/40'
                              : 'bg-slate-500/15 text-slate-400 border-slate-500/40'
                          }`}
                        >
                          {stale ? 'Không rõ' : nic.status || 'Chưa rõ'}
                        </span>
                      </div>
                      <div className="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
                        <div>
                          <p className={`text-xs ${muted}`}>Địa chỉ IP</p>
                          <p className="font-mono font-semibold">{nic.ipAddress || '—'}</p>
                        </div>
                        <div>
                          <p className={`text-xs ${muted}`}>Địa chỉ MAC</p>
                          <p className="font-mono font-semibold">{nic.macAddress || '—'}</p>
                        </div>
                        <div>
                          <p className="text-xs text-emerald-400">Tải xuống</p>
                          <p className="font-mono font-semibold">{stale ? '—' : `${fmtBytes(nic.downloadSpeed)}/s`}</p>
                        </div>
                        <div>
                          <p className="text-xs text-pink-400">Tải lên</p>
                          <p className="font-mono font-semibold">{stale ? '—' : `${fmtBytes(nic.uploadSpeed)}/s`}</p>
                        </div>
                      </div>
                      <p className={`text-xs font-mono pt-2 border-t ${divider} ${muted}`}>
                        Tổng nhận {fmtBytes(nic.receivedBytes)} · Tổng gửi {fmtBytes(nic.transmittedBytes)}
                      </p>
                    </li>
                  );
                })}
              </ul>
            )}
          </div>
        </div>
      )}

      {/* ======================= TAB: TIẾN TRÌNH ======================= */}
      {tab === 'processes' && (
        <div className={`${card} p-5`}>
          <PanelTitle
            icon={<Cpu className="w-5 h-5" />}
            title={stale ? 'Tiến trình lần cuối ghi nhận' : 'Tiến trình đang chạy'}
            right={
              <div className="flex flex-wrap items-center gap-3">
                {stale && latest && <StaleBadge at={dataAt} isDark={isDark} />}
                <div className="relative">
                  <Search className="w-4 h-4 text-cyan-400 absolute left-3 top-1/2 -translate-y-1/2" />
                  <input
                    type="text"
                    value={processSearch}
                    onChange={(e) => setProcessSearch(e.target.value)}
                    placeholder="Tìm theo tên hoặc PID..."
                    className={`${fieldClass} w-64 pl-9`}
                  />
                </div>
              </div>
            }
          />

          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-sm">
              <thead>
                <tr className={`text-xs font-semibold ${isDark ? 'bg-blue-950/40 text-slate-400' : 'bg-slate-50 text-slate-500'}`}>
                  <th className="p-3 rounded-l-xl">PID</th>
                  <th className="p-3">Tên tiến trình</th>
                  <th className="p-3 w-64">CPU</th>
                  <th className="p-3">RAM</th>
                  <th className="p-3 rounded-r-xl">Trạng thái</th>
                </tr>
              </thead>
              <tbody>
                {processes.length === 0 ? (
                  <tr>
                    <td colSpan={5} className={`text-center py-12 ${muted}`}>
                      {latest ? 'Không tìm thấy tiến trình phù hợp.' : 'Chưa có dữ liệu tiến trình.'}
                    </td>
                  </tr>
                ) : (
                  processes.map((p) => (
                    <tr
                      key={`${p.pid}-${p.name}`}
                      className={`border-b last:border-0 ${stale ? 'opacity-60' : ''} ${isDark ? 'border-blue-500/10 hover:bg-blue-950/30' : 'border-slate-100 hover:bg-slate-50'}`}
                    >
                      <td className={`p-3 font-mono ${muted}`}>{p.pid}</td>
                      <td className="p-3 font-semibold">{p.name}</td>
                      <td className="p-3">
                        <div className="flex items-center gap-3">
                          <div className="flex-1">
                            <UsageBar value={p.cpuUsage} track={track} stale={stale} />
                          </div>
                          <span className="font-mono text-xs font-semibold w-14 text-right">{fmtPct(p.cpuUsage)}</span>
                        </div>
                      </td>
                      <td className="p-3 font-mono">{fmtBytes(p.memoryUsage)}</td>
                      <td className={`p-3 ${subtle}`}>{stale ? 'Không rõ' : p.status || '—'}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>

          <p className={`text-xs mt-4 pt-4 border-t ${divider} ${muted}`}>
            {stale
              ? `Danh sách tại thời điểm cuối cùng Agent báo cáo (${dataAt}). Máy đang offline nên các tiến trình này có thể không còn chạy.`
              : `Agent gửi danh sách các tiến trình dùng nhiều CPU nhất · Cập nhật lúc ${dataAt}`}
          </p>
        </div>
      )}

      {/* ======================= TAB: CẢNH BÁO ======================= */}
      {tab === 'alerts' && (
        <div className={`${card} p-5`}>
          <PanelTitle
            icon={<Bell className="w-5 h-5" />}
            title="Cảnh báo của máy này"
            right={<span className="text-sm text-cyan-400 font-semibold">{openAlerts} đang mở</span>}
          />
          {alerts.length === 0 ? (
            <div className={`py-14 flex flex-col items-center text-center gap-3 ${muted}`}>
              <div className="p-4 rounded-2xl bg-emerald-500/10 border border-emerald-500/25">
                <CheckCircle2 className="w-8 h-8 text-emerald-400" />
              </div>
              <p className="text-sm">Máy này chưa có cảnh báo nào.</p>
            </div>
          ) : (
            <ul className="grid grid-cols-1 lg:grid-cols-2 gap-3">{alerts.map(renderAlert)}</ul>
          )}
        </div>
      )}

    </div>
  );
}