import { useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Activity,
  ArrowRight,
  Bell,
  CheckCircle2,
  ChevronRight,
  Clock,
  Cpu,
  Eye,
  HardDrive,
  Loader2,
  Monitor,
  Search,
  Server,
  ShieldAlert,
  Users,
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
  getSession,
  subscribeDashboard,
  subscribeMetricHistory,
  toDate,
} from '../../lib/api';
import type {
  AlertSeverity,
  ClientStatus,
  DashboardSnapshot,
  HistoryPoint,
  LatestMetrics,
  ResourceType,
} from '../../lib/api';
import { useTheme } from '../../context/ThemeContext';

// ============================================================
// HẰNG SỐ
// ============================================================

const DASHBOARD_REFRESH_MS = 5000;
const HISTORY_REFRESH_MS = 10000;
const HISTORY_MINUTES = 30;

type OsFamily = 'Ubuntu' | 'Debian' | 'Windows' | 'Khác';
type StatusFilter = 'all' | ClientStatus;

const STATUS_PILL: Record<ClientStatus, { label: string; className: string; dot: string }> = {
  ONLINE: {
    label: 'Online',
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

const FILTERS: { key: StatusFilter; label: string }[] = [
  { key: 'all', label: 'Tất cả' },
  { key: 'ONLINE', label: 'Online' },
  { key: 'OFFLINE', label: 'Ngoại tuyến' },
  { key: 'PENDING_APPROVAL', label: 'Chờ duyệt' },
];

// ============================================================
// HÀM TIỆN ÍCH
// ============================================================

const pad = (n: number) => String(n).padStart(2, '0');

function formatDateTime(value: Parameters<typeof toDate>[0]): string {
  const date = toDate(value);
  if (!date) return '—';
  return `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())} ${pad(date.getDate())}/${pad(date.getMonth() + 1)}`;
}

// "5 phút trước", "2 giờ trước"...
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

function fmtPct(value: number | null | undefined): string {
  return typeof value === 'number' ? `${value.toFixed(1)}%` : '--';
}

function detectOs(osName: string): OsFamily {
  const name = (osName ?? '').toLowerCase();
  if (name.includes('ubuntu')) return 'Ubuntu';
  if (name.includes('debian')) return 'Debian';
  if (name.includes('windows')) return 'Windows';
  return 'Khác';
}

// Màu theo mức tải: bình thường - cao - nguy hiểm
function levelHex(value: number | null | undefined): string {
  if (typeof value !== 'number') return '#64748b';
  if (value >= 90) return '#ef4444';
  if (value >= 75) return '#f59e0b';
  return '#22d3ee';
}

function diskPercent(latest: LatestMetrics | undefined): number | null {
  if (!latest || latest.disks.length === 0) return null;
  return latest.disks.reduce((sum, d) => sum + d.usagePercent, 0) / latest.disks.length;
}

// ============================================================
// COMPONENT PHỤ
// ============================================================

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

// Thanh mức sử dụng có nhãn (CPU / RAM / Disk)
function MetricBar({
  label,
  value,
  track,
  muted,
}: {
  label: string;
  value: number | null | undefined;
  track: string;
  muted: string;
}) {
  const pct = typeof value === 'number' ? Math.max(0, Math.min(100, value)) : 0;
  return (
    <div className="flex items-center gap-3">
      <span className={`w-10 text-xs font-semibold ${muted}`}>{label}</span>
      <div className={`flex-1 h-2 rounded-full overflow-hidden ${track}`}>
        <div
          className="h-full rounded-full transition-all duration-700"
          style={{ width: `${pct}%`, backgroundColor: levelHex(value) }}
        />
      </div>
      <span className="w-14 text-right font-mono text-xs font-semibold">{fmtPct(value)}</span>
    </div>
  );
}

// Vòng tròn phần trăm trong thẻ thống kê
function Ring({ value, color, track }: { value: number; color: string; track: string }) {
  const size = 56;
  const stroke = 6;
  const radius = (size - stroke) / 2;
  const circumference = 2 * Math.PI * radius;
  const pct = Math.max(0, Math.min(100, value));
  return (
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
      />
    </svg>
  );
}

// ============================================================
// TRANG DANH SÁCH MÁY
// ============================================================

export default function ComputersPage() {
  const session = getSession();
  const { isDark } = useTheme();
  const navigate = useNavigate();

  const [snapshot, setSnapshot] = useState<DashboardSnapshot | null>(null);
  const [error, setError] = useState<string | null>(null);

  const [selectedClientId, setSelectedClientId] = useState('');
  const [history, setHistory] = useState<{ clientId: string; points: HistoryPoint[] } | null>(null);
  const [historyError, setHistoryError] = useState<{ clientId: string; message: string } | null>(null);

  const [statusFilter, setStatusFilter] = useState<StatusFilter>('all');
  const [searchTerm, setSearchTerm] = useState('');

  // Token hết hạn (401) -> về trang đăng nhập
  useEffect(() => {
    const onUnauthorized = () => navigate('/login', { replace: true });
    window.addEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
  }, [navigate]);

  // Dữ liệu tổng quan: tự làm mới mỗi 5 giây
  useEffect(() => {
    return subscribeDashboard(
      (data) => {
        setSnapshot(data);
        setError(null);
      },
      (err) => setError(err.message),
      DASHBOARD_REFRESH_MS
    );
  }, []);

  // ----------------------------------------------------------
  // Dữ liệu dẫn xuất
  // ----------------------------------------------------------
  const clients = useMemo(() => snapshot?.clients ?? [], [snapshot]);
  const stats = snapshot?.stats;
  const isLoading = snapshot === null && error === null;

  const activeClientId = useMemo(() => {
    if (selectedClientId && clients.some((c) => c.clientId === selectedClientId)) {
      return selectedClientId;
    }
    const firstOnline = clients.find((c) => c.status === 'ONLINE');
    return (firstOnline ?? clients[0])?.clientId ?? '';
  }, [selectedClientId, clients]);

  const activeClient = clients.find((c) => c.clientId === activeClientId);
  const latest = snapshot?.latestByClient[activeClientId];

  // Lịch sử CPU/RAM/Disk của máy đang chọn
  useEffect(() => {
    if (!activeClientId) return;

    return subscribeMetricHistory(
      activeClientId,
      (points) => {
        setHistory({ clientId: activeClientId, points });
        setHistoryError(null);
      },
      (err) => setHistoryError({ clientId: activeClientId, message: err.message }),
      HISTORY_MINUTES,
      HISTORY_REFRESH_MS
    );
  }, [activeClientId]);

  const chartData = useMemo(() => {
    if (!history || history.clientId !== activeClientId) return [];

    return history.points
      .map((p) => ({ date: toDate(p.collectedAt), p }))
      .filter((item): item is { date: Date; p: HistoryPoint } => item.date !== null)
      .sort((a, b) => a.date.getTime() - b.date.getTime())
      .map(({ date, p }) => ({
        time: date.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit', second: '2-digit' }),
        CPU: p.cpuUsagePercent,
        RAM: p.memoryUsagePercent,
        Disk: p.diskUsagePercent,
      }));
  }, [history, activeClientId]);

  const chartError =
    historyError && historyError.clientId === activeClientId ? historyError.message : null;

  const hostnameById = useMemo(
    () => new Map(clients.map((c) => [c.clientId, c.hostname])),
    [clients]
  );

  // Đếm số máy theo từng trạng thái để hiện trên nút lọc
  const countByStatus = useMemo(() => {
    const map: Record<string, number> = { all: clients.length };
    clients.forEach((c) => {
      map[c.status] = (map[c.status] ?? 0) + 1;
    });
    return map;
  }, [clients]);

  const filteredClients = useMemo(() => {
    const keyword = searchTerm.trim().toLowerCase();
    return clients.filter((c) => {
      const matchesStatus = statusFilter === 'all' || c.status === statusFilter;
      const matchesSearch =
        keyword === '' ||
        c.hostname.toLowerCase().includes(keyword) ||
        c.ipAddress.toLowerCase().includes(keyword) ||
        `${c.osName} ${c.osVersion}`.toLowerCase().includes(keyword);
      return matchesStatus && matchesSearch;
    });
  }, [clients, searchTerm, statusFilter]);

  const recentAlerts = (snapshot?.recentAlerts ?? []).slice(0, 5);
  const partialErrors = snapshot ? Object.values(snapshot.errors).filter(Boolean) : [];
  const username = session?.username || session?.name || 'Quản trị viên';

  // ----------------------------------------------------------
  // Style dùng chung (cùng bộ với Dashboard / Agents)
  // ----------------------------------------------------------
  const card = `rounded-2xl backdrop-blur-xl border transition-colors duration-500 ${
    isDark
      ? 'bg-[#081226]/80 border-blue-500/20 text-white shadow-[0_0_32px_-10px_rgba(37,99,235,0.45)]'
      : 'bg-white border-slate-200 text-slate-900 shadow-sm'
  }`;
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

  const onlineRate = typeof stats?.onlineRate === 'number' ? stats.onlineRate : 0;

  const statCards = [
    {
      label: 'Tổng số máy giám sát',
      sub: `${stats?.pendingClients ?? 0} máy đang chờ duyệt`,
      value: String(stats?.totalClients ?? 0),
      icon: <Monitor className="w-7 h-7" />,
      iconBg: 'from-blue-500 to-blue-700 shadow-[0_0_26px_rgba(59,130,246,0.6)]',
    },
    {
      label: 'Máy đang hoạt động',
      sub: (stats?.onlineClients ?? 0) > 0 ? 'Đang gửi dữ liệu về máy chủ' : 'Chưa có máy nào trực tuyến',
      value: String(stats?.onlineClients ?? 0),
      icon: <CheckCircle2 className="w-7 h-7" />,
      iconBg: 'from-emerald-500 to-emerald-700 shadow-[0_0_26px_rgba(34,197,94,0.6)]',
    },
    {
      label: 'Máy ngoại tuyến',
      sub: `${stats?.openAlerts ?? 0} cảnh báo đang mở`,
      value: String(stats?.offlineClients ?? 0),
      icon: <ShieldAlert className="w-7 h-7" />,
      iconBg: 'from-rose-500 to-red-700 shadow-[0_0_26px_rgba(239,68,68,0.6)]',
    },
  ];

  // ----------------------------------------------------------
  // Giao diện
  // ----------------------------------------------------------
  return (
    <div className="space-y-5 pb-12 relative">

      {/* 1. BREADCRUMB + TIÊU ĐỀ */}
      <div className="space-y-3">
        <div className={`flex items-center gap-2 text-sm ${muted}`}>
          <Monitor className="w-4 h-4" />
          <ChevronRight className="w-3.5 h-3.5" />
          <span>Computers</span>
          <ChevronRight className="w-3.5 h-3.5" />
          <span className={isDark ? 'text-slate-200' : 'text-slate-700'}>Danh sách máy</span>
        </div>

        <div className="flex flex-col lg:flex-row justify-between items-start lg:items-center gap-4">
          <div className="flex items-center gap-4">
            <div className="p-3 rounded-2xl bg-gradient-to-br from-blue-500/30 to-cyan-400/10 border border-cyan-400/30 shadow-[0_0_24px_rgba(34,211,238,0.3)]">
              <Monitor className="w-8 h-8 text-cyan-400" />
            </div>
            <div>
              <h1 className="text-2xl sm:text-3xl font-black tracking-tight">Danh sách máy tính</h1>
              <p className={`text-sm mt-1 ${muted}`}>
                Xin chào <span className="font-semibold text-cyan-400">{username}</span>, theo dõi trạng thái và tài nguyên
                các máy đang giám sát
              </p>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            <div
              className={`flex items-center gap-2.5 px-4 py-2.5 rounded-xl border text-sm font-medium ${
                error
                  ? 'bg-red-500/10 border-red-500/40 text-red-400'
                  : isDark
                    ? 'bg-[#081226]/80 border-blue-500/40 text-slate-200'
                    : 'bg-white border-slate-300 text-slate-700'
              }`}
            >
              <span
                className={`w-2.5 h-2.5 rounded-full animate-pulse ${
                  error ? 'bg-red-400' : 'bg-emerald-400 shadow-[0_0_10px_#34d399]'
                }`}
              />
              {error
                ? 'Mất kết nối'
                : `Trực tiếp · ${snapshot ? snapshot.updatedAt.toLocaleTimeString('vi-VN') : 'Đang đồng bộ...'}`}
            </div>

            <button
              onClick={() => navigate('/agents')}
              className="px-5 py-2.5 rounded-xl bg-gradient-to-r from-blue-600 to-cyan-500 text-white text-sm font-bold shadow-[0_0_24px_rgba(34,211,238,0.45)] hover:from-blue-500 hover:to-cyan-400 transition-all flex items-center gap-2"
            >
              <Users className="w-4 h-4" />
              Quản lý Agent
            </button>
          </div>
        </div>
      </div>

      {/* Thông báo lỗi */}
      {error && (
        <div className="flex items-start gap-3 p-4 rounded-2xl border bg-red-500/10 border-red-500/30 text-red-400 text-sm">
          <WifiOff className="w-5 h-5 shrink-0 mt-0.5" />
          <div className="flex-1">
            <p className="font-semibold">
              {snapshot ? 'Mất kết nối tới máy chủ, đang hiển thị dữ liệu cũ' : 'Không thể tải dữ liệu'}
            </p>
            <p className="text-xs mt-0.5 opacity-90">{error}</p>
          </div>
        </div>
      )}
      {partialErrors.length > 0 && (
        <div className="p-3 rounded-2xl border text-xs bg-amber-500/10 border-amber-500/30 text-amber-400 space-y-1">
          {partialErrors.map((message) => (
            <p key={message}>{message}</p>
          ))}
        </div>
      )}

      {/* 2. BỐN THẺ THỐNG KÊ */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        {statCards.map((item) => (
          <div key={item.label} className={`${card} p-5`}>
            <div className="flex items-center gap-4">
              <div className={`w-14 h-14 shrink-0 rounded-2xl bg-gradient-to-br ${item.iconBg} flex items-center justify-center text-white`}>
                {item.icon}
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-3xl font-black leading-none">
                  {isLoading ? <Loader2 className="w-6 h-6 animate-spin" /> : item.value}
                </p>
                <p className={`text-sm mt-2 font-medium truncate ${subtle}`}>{item.label}</p>
              </div>
            </div>
            <p className={`text-xs mt-4 pt-3 border-t truncate ${divider} ${muted}`}>{item.sub}</p>
          </div>
        ))}

        {/* Thẻ tỷ lệ online có vòng tròn */}
        <div className={`${card} p-5`}>
          <div className="flex items-center gap-4">
            <div className="relative w-14 h-14 shrink-0 flex items-center justify-center">
              <div className="absolute -inset-0.5 flex items-center justify-center">
                <Ring value={onlineRate} color={levelHex(100 - onlineRate > 50 ? 95 : 0)} track={ringTrack} />
              </div>
              <Activity className="w-5 h-5 text-cyan-400" />
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-3xl font-black leading-none">
                {isLoading ? <Loader2 className="w-6 h-6 animate-spin" /> : `${onlineRate}%`}
              </p>
              <p className={`text-sm mt-2 font-medium truncate ${subtle}`}>Tỷ lệ máy online</p>
            </div>
          </div>
          <p className={`text-xs mt-4 pt-3 border-t truncate ${divider} ${muted}`}>
            {stats ? `${stats.onlineClients}/${stats.totalClients} máy đang kết nối` : 'Đang đồng bộ dữ liệu...'}
          </p>
        </div>
      </div>

      {/* 3. DANH SÁCH MÁY + CẢNH BÁO */}
      <div className="grid grid-cols-1 xl:grid-cols-12 gap-5">

        {/* Danh sách máy dạng thẻ */}
        <div className={`${card} xl:col-span-8 p-5`}>
          <PanelTitle
            icon={<Server className="w-5 h-5" />}
            title="Máy tính đang giám sát"
            right={
              <div className="relative">
                <Search className="w-4 h-4 text-cyan-400 absolute left-3 top-1/2 -translate-y-1/2" />
                <input
                  type="text"
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  placeholder="Tìm theo tên, IP, hệ điều hành..."
                  className={`${fieldClass} w-64 pl-9`}
                />
              </div>
            }
          />

          {/* Nút lọc trạng thái */}
          <div className="flex flex-wrap items-center gap-2 mb-5">
            {FILTERS.map((f) => {
              const active = statusFilter === f.key;
              return (
                <button
                  key={f.key}
                  onClick={() => setStatusFilter(f.key)}
                  className={`px-3.5 py-1.5 rounded-lg text-sm font-semibold transition-colors flex items-center gap-2 ${
                    active
                      ? 'bg-cyan-500 text-slate-950'
                      : isDark
                        ? 'bg-blue-600/15 border border-blue-500/30 text-slate-300 hover:bg-blue-600/30'
                        : 'bg-blue-50 border border-blue-200 text-blue-700 hover:bg-blue-100'
                  }`}
                >
                  {f.label}
                  <span
                    className={`min-w-5 px-1.5 rounded-md text-xs font-bold text-center ${
                      active ? 'bg-slate-950/15' : isDark ? 'bg-slate-900/70' : 'bg-white'
                    }`}
                  >
                    {countByStatus[f.key] ?? 0}
                  </span>
                </button>
              );
            })}
          </div>

          {isLoading ? (
            <div className={`py-16 text-center text-sm ${muted}`}>
              <Loader2 className="w-6 h-6 animate-spin inline mr-2 text-cyan-400" />
              Đang tải danh sách máy...
            </div>
          ) : filteredClients.length === 0 ? (
            <div className={`py-14 flex flex-col items-center text-center gap-3 ${muted}`}>
              <div className="p-4 rounded-2xl bg-blue-600/10 border border-blue-500/20">
                <Monitor className="w-8 h-8 text-cyan-400" />
              </div>
              <p className="text-sm max-w-sm">
                {clients.length === 0
                  ? 'Chưa có máy nào trong hệ thống. Hãy cài Agent rồi duyệt tại trang Agents.'
                  : 'Không tìm thấy máy phù hợp với bộ lọc hiện tại.'}
              </p>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {filteredClients.map((client) => {
                const pill = STATUS_PILL[client.status] ?? STATUS_PILL.UNKNOWN;
                const os = OS_STYLE[detectOs(client.osName)];
                const metrics = snapshot?.latestByClient[client.clientId];
                const selected = client.clientId === activeClientId;
                const isOnline = client.status === 'ONLINE';

                return (
                  <div
                    key={client.clientId}
                    role="button"
                    tabIndex={0}
                    onClick={() => setSelectedClientId(client.clientId)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault();
                        setSelectedClientId(client.clientId);
                      }
                    }}
                    className={`p-4 rounded-2xl border cursor-pointer transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400 ${
                      selected
                        ? isDark
                          ? 'bg-cyan-500/10 border-cyan-400/60 shadow-[0_0_24px_-6px_rgba(34,211,238,0.5)]'
                          : 'bg-cyan-50 border-cyan-400'
                        : isDark
                          ? 'bg-[#050c1c]/70 border-blue-500/15 hover:border-blue-400/40 hover:bg-blue-950/30'
                          : 'bg-slate-50 border-slate-200 hover:border-blue-300 hover:bg-white'
                    }`}
                  >
                    {/* Đầu thẻ */}
                    <div className="flex items-start justify-between gap-3">
                      <div className="flex items-center gap-3 min-w-0">
                        <span
                          className="w-10 h-10 rounded-xl flex items-center justify-center text-sm font-bold text-white shrink-0"
                          style={{ backgroundColor: os.color }}
                        >
                          {os.letter}
                        </span>
                        <div className="min-w-0">
                          <p className="font-bold text-base truncate" title={client.hostname}>
                            {client.hostname}
                          </p>
                          <p className={`font-mono text-xs ${muted}`}>{client.ipAddress}</p>
                        </div>
                      </div>
                      <span
                        className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold border shrink-0 ${pill.className}`}
                      >
                        <span className={`w-1.5 h-1.5 rounded-full ${pill.dot} ${isOnline ? 'animate-pulse' : ''}`} />
                        {pill.label}
                      </span>
                    </div>

                    <p className={`text-xs mt-2.5 truncate ${muted}`}>
                      {[client.osName, client.osVersion].filter(Boolean).join(' ') || 'Hệ điều hành chưa rõ'}
                    </p>

                    {/* Chỉ số tài nguyên */}
                    <div className={`mt-4 space-y-2.5 ${isOnline ? '' : 'opacity-60'}`}>
                      {metrics ? (
                        <>
                          <MetricBar label="CPU" value={metrics.cpu?.usagePercent} track={track} muted={muted} />
                          <MetricBar label="RAM" value={metrics.memory?.usagePercent} track={track} muted={muted} />
                          <MetricBar label="Disk" value={diskPercent(metrics)} track={track} muted={muted} />
                        </>
                      ) : (
                        <p className={`text-sm py-3 text-center rounded-xl border border-dashed ${isDark ? 'border-slate-700' : 'border-slate-300'} ${muted}`}>
                          Chưa có dữ liệu tài nguyên
                        </p>
                      )}
                    </div>

                    {/* Chân thẻ */}
                    <div className={`mt-4 pt-3 border-t flex items-center justify-between gap-2 ${divider}`}>
                      <span className={`flex items-center gap-1.5 text-xs ${muted}`}>
                        <Clock className="w-3.5 h-3.5" />
                        {timeAgo(client.lastSeenAt)}
                      </span>
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          navigate(`/computers/${encodeURIComponent(client.clientId)}`);
                        }}
                        className={`px-3 py-1.5 rounded-lg border text-xs font-bold flex items-center gap-1.5 transition-colors ${
                          isDark
                            ? 'bg-blue-600/20 border-blue-500/40 text-cyan-300 hover:bg-blue-600/40'
                            : 'bg-blue-50 border-blue-200 text-blue-600 hover:bg-blue-100'
                        }`}
                      >
                        <Eye className="w-3.5 h-3.5" />
                        Chi tiết
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          )}

          <p className={`text-xs mt-5 pt-4 border-t ${divider} ${muted}`}>
            Hiển thị {filteredClients.length} / {clients.length} máy · Bấm vào một máy để xem biểu đồ tài nguyên bên dưới
          </p>
        </div>

        {/* Cảnh báo gần đây */}
        <div className={`${card} xl:col-span-4 p-5 flex flex-col`}>
          <PanelTitle
            icon={<Bell className="w-5 h-5" />}
            title="Cảnh báo gần đây"
            right={
              <span className="text-sm text-cyan-400 font-semibold">{stats?.openAlerts ?? 0} đang mở</span>
            }
          />

          <div className="flex-1">
            {recentAlerts.length === 0 ? (
              <div className={`py-12 flex flex-col items-center text-center gap-3 ${muted}`}>
                <div className="p-4 rounded-2xl bg-emerald-500/10 border border-emerald-500/25">
                  <CheckCircle2 className="w-8 h-8 text-emerald-400" />
                </div>
                <p className="text-sm">
                  {isLoading ? 'Đang đồng bộ dữ liệu...' : 'Hệ thống ổn định, chưa có cảnh báo nào.'}
                </p>
              </div>
            ) : (
              <div className="relative">
                <div className={`absolute left-[5px] top-2 bottom-2 w-px ${isDark ? 'bg-blue-500/25' : 'bg-slate-200'}`} />
                <ul className="space-y-5">
                  {recentAlerts.map((alert) => {
                    const severity = SEVERITY_STYLE[alert.severity] ?? SEVERITY_STYLE.INFO;
                    const resolved = alert.status === 'RESOLVED';
                    const color = resolved ? '#64748b' : severity.color;
                    const unit = PERCENT_RESOURCES.includes(alert.resourceType) ? '%' : '';

                    return (
                      <li key={alert.alertId} className="relative pl-7">
                        <span
                          className="absolute left-0 top-1.5 w-[11px] h-[11px] rounded-full ring-4"
                          style={{
                            backgroundColor: color,
                            boxShadow: `0 0 10px ${color}`,
                            ['--tw-ring-color' as string]: isDark ? '#081226' : '#ffffff',
                          }}
                        />
                        <div className="flex items-start justify-between gap-3">
                          <div className="min-w-0">
                            <p className="text-xs font-mono text-cyan-400">{formatDateTime(alert.createdAt)}</p>
                            <p className="text-sm font-bold truncate mt-0.5" title={alert.message}>
                              {alert.ruleName || RESOURCE_LABEL[alert.resourceType] || 'Cảnh báo'}
                            </p>
                            <p className={`text-xs mt-0.5 ${muted}`}>
                              {hostnameById.get(alert.clientId) ?? alert.clientId}
                            </p>
                            <p className={`text-xs mt-0.5 font-mono ${muted}`}>
                              {RESOURCE_LABEL[alert.resourceType]} {Number(alert.currentValue).toFixed(1)}
                              {unit} / ngưỡng {Number(alert.thresholdValue).toFixed(1)}
                              {unit}
                            </p>
                          </div>
                          <span
                            className="px-2.5 py-1 rounded-full text-xs font-semibold border shrink-0"
                            style={{ color, borderColor: `${color}66`, backgroundColor: `${color}1f` }}
                          >
                            {resolved ? 'Đã xử lý' : alert.status === 'ACKNOWLEDGED' ? 'Đã ghi nhận' : severity.label}
                          </span>
                        </div>
                      </li>
                    );
                  })}
                </ul>
              </div>
            )}
          </div>

          <button
            type="button"
            onClick={() => navigate('/alerts')}
            className={`mt-6 w-full px-4 py-3 rounded-xl border text-sm font-bold flex items-center justify-center gap-2 transition-colors ${
              isDark
                ? 'bg-blue-600/15 border-blue-500/30 text-cyan-300 hover:bg-blue-600/30'
                : 'bg-blue-50 border-blue-200 text-blue-600 hover:bg-blue-100'
            }`}
          >
            Xem tất cả cảnh báo
            <ArrowRight className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* 4. BIỂU ĐỒ TÀI NGUYÊN CỦA MÁY ĐANG CHỌN */}
      <div className={`${card} p-5`}>
        <PanelTitle
          icon={<Activity className="w-5 h-5" />}
          title={`Tài nguyên ${HISTORY_MINUTES} phút gần nhất`}
          right={
            clients.length > 0 ? (
              <select
                value={activeClientId}
                onChange={(e) => setSelectedClientId(e.target.value)}
                className={`${fieldClass} max-w-full`}
              >
                {clients.map((c) => (
                  <option key={c.clientId} value={c.clientId}>
                    {c.hostname} ({c.ipAddress}){c.status === 'ONLINE' ? '' : ` - ${STATUS_PILL[c.status]?.label ?? c.status}`}
                  </option>
                ))}
              </select>
            ) : undefined
          }
        />

        {/* Chỉ số hiện tại của máy đang chọn */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 mb-5">
          {[
            {
              key: 'CPU',
              icon: <Cpu className="w-5 h-5" />,
              color: '#22d3ee',
              value: latest?.cpu?.usagePercent,
              avg: stats?.avgCpu,
            },
            {
              key: 'RAM',
              icon: <HardDrive className="w-5 h-5" />,
              color: '#a78bfa',
              value: latest?.memory?.usagePercent,
              avg: stats?.avgMemory,
            },
            {
              key: 'Disk',
              icon: <HardDrive className="w-5 h-5" />,
              color: '#fbbf24',
              value: diskPercent(latest),
              avg: stats?.avgDisk,
            },
          ].map((m) => (
            <div
              key={m.key}
              className={`p-4 rounded-2xl border flex items-center gap-4 ${
                isDark ? 'bg-[#050c1c]/70 border-blue-500/15' : 'bg-slate-50 border-slate-200'
              }`}
            >
              <div
                className="w-11 h-11 rounded-xl flex items-center justify-center shrink-0"
                style={{ color: m.color, backgroundColor: `${m.color}22`, border: `1px solid ${m.color}55` }}
              >
                {m.icon}
              </div>
              <div className="min-w-0">
                <p className={`text-xs font-semibold ${muted}`}>
                  {m.key}
                  {activeClient ? ` · ${activeClient.hostname}` : ''}
                </p>
                <p className="text-2xl font-black leading-tight">{fmtPct(m.value)}</p>
                <p className={`text-xs font-mono ${muted}`}>Trung bình hệ thống: {fmtPct(m.avg)}</p>
              </div>
            </div>
          ))}
        </div>

        {/* Chú thích */}
        <div className="flex items-center gap-5 mb-3 text-sm">
          {[
            ['CPU', '#22d3ee'],
            ['RAM', '#a78bfa'],
            ['Disk', '#fbbf24'],
          ].map(([name, color]) => (
            <span key={name} className="flex items-center gap-2">
              <span className="w-3 h-3 rounded-full" style={{ backgroundColor: color }} />
              <span className={subtle}>{name}</span>
            </span>
          ))}
        </div>

        <div
          className={`h-72 w-full rounded-2xl border p-4 ${
            isDark ? 'bg-[#050c1c]/70 border-blue-500/15' : 'bg-slate-50 border-slate-200'
          }`}
        >
          {chartData.length > 0 ? (
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={chartData} margin={{ top: 5, right: 10, left: -16, bottom: 0 }}>
                <defs>
                  <linearGradient id="compFillCpu" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#22d3ee" stopOpacity={0.35} />
                    <stop offset="100%" stopColor="#22d3ee" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke={gridColor} />
                <XAxis dataKey="time" tick={{ fontSize: 11, fill: axisColor }} minTickGap={40} />
                <YAxis domain={[0, 100]} unit="%" tick={{ fontSize: 11, fill: axisColor }} />
                <Tooltip contentStyle={tooltipStyle} formatter={(value) => `${Number(value).toFixed(1)}%`} />
                <Area type="monotone" dataKey="CPU" stroke="#22d3ee" strokeWidth={2} fill="url(#compFillCpu)" dot={false} isAnimationActive={false} />
                <Area type="monotone" dataKey="RAM" stroke="#a78bfa" strokeWidth={2} fill="none" dot={false} isAnimationActive={false} />
                <Area type="monotone" dataKey="Disk" stroke="#fbbf24" strokeWidth={2} fill="none" dot={false} isAnimationActive={false} />
              </AreaChart>
            </ResponsiveContainer>
          ) : (
            <div className={`h-full flex flex-col items-center justify-center gap-3 text-center px-6 ${muted}`}>
              <div className="p-3.5 rounded-2xl bg-blue-600/10 border border-blue-500/20">
                <Activity className="w-7 h-7 text-cyan-400" />
              </div>
              <p className="text-sm max-w-md">
                {chartError
                  ? chartError
                  : !activeClientId
                    ? isLoading
                      ? 'Đang đồng bộ dữ liệu...'
                      : 'Chưa có máy nào để hiển thị biểu đồ.'
                    : history?.clientId === activeClientId
                      ? `Máy này chưa gửi dữ liệu trong ${HISTORY_MINUTES} phút gần nhất.`
                      : 'Đang tải biểu đồ...'}
              </p>
            </div>
          )}
        </div>
      </div>

    </div>
  );
}