import { useCallback, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Activity,
  Bell,
  ChevronLeft,
  ChevronRight,
  Clock,
  Cpu,
  Eye,
  Filter,
  HardDrive,
  LayoutDashboard,
  Loader2,
  Monitor,
  RefreshCw,
  Search,
  ShieldAlert,
} from 'lucide-react';
import {
  Area,
  AreaChart,
  CartesianGrid,
  Cell,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import {
  UNAUTHORIZED_EVENT,
  getDashboardSnapshot,
  startPolling,
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

const PAGE_SIZE = 6;
const MAX_SERIES_POINTS = 24;
const HISTORY_MINUTES = 30;
const HISTORY_REFRESH_MS = 10000;

type OsFamily = 'Ubuntu' | 'Debian' | 'Windows' | 'Khác';

interface SeriesPoint {
  online: number;
  cpu: number | null;
  mem: number | null;
  alerts: number;
}

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

const SEVERITY_COLOR: Record<AlertSeverity, { label: string; color: string }> = {
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

// "20:45:20 01/11"
function formatDateTime(date: Date | null): string {
  if (!date) return '—';
  return `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())} ${pad(date.getDate())}/${pad(date.getMonth() + 1)}`;
}

function detectOs(osName: string): OsFamily {
  const name = osName.toLowerCase();
  if (name.includes('ubuntu')) return 'Ubuntu';
  if (name.includes('debian')) return 'Debian';
  if (name.includes('windows')) return 'Windows';
  return 'Khác';
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

// Màu theo mức tải: bình thường - cao - nguy hiểm
function levelHex(value: number | null | undefined): string {
  if (typeof value !== 'number') return '#64748b';
  if (value >= 90) return '#ef4444';
  if (value >= 75) return '#f59e0b';
  return '#22d3ee';
}

// Trung bình % sử dụng các ổ đĩa của một máy (khớp cách tính ở api.ts)
function diskPercent(latest: LatestMetrics | undefined): number | null {
  if (!latest || latest.disks.length === 0) return null;
  return latest.disks.reduce((sum, d) => sum + d.usagePercent, 0) / latest.disks.length;
}

// ============================================================
// COMPONENT PHỤ
// ============================================================

// Đường biểu đồ nhỏ dưới thẻ thống kê
function Sparkline({ values, color }: { values: number[]; color: string }) {
  const w = 110;
  const h = 40;
  const pad2 = 3;
  const data = values.length >= 2 ? values : [values[0] ?? 0, values[0] ?? 0];
  const min = Math.min(...data);
  const max = Math.max(...data);
  const range = max - min;

  const points = data.map((v, i) => {
    const x = pad2 + (i * (w - pad2 * 2)) / (data.length - 1);
    const y = range === 0 ? h / 2 : h - pad2 - ((v - min) / range) * (h - pad2 * 2);
    return [x, y] as const;
  });

  const line = points.map(([x, y], i) => `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${y.toFixed(1)}`).join(' ');
  const area = `${line} L${points[points.length - 1][0].toFixed(1)},${h} L${points[0][0].toFixed(1)},${h} Z`;
  const gradientId = `spark-${color.replace('#', '')}`;

  return (
    <svg width={w} height={h} viewBox={`0 0 ${w} ${h}`} className="overflow-visible">
      <defs>
        <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={color} stopOpacity="0.35" />
          <stop offset="100%" stopColor={color} stopOpacity="0" />
        </linearGradient>
      </defs>
      <path d={area} fill={`url(#${gradientId})`} />
      <path d={line} fill="none" stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

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

// Thanh mức sử dụng nhỏ trong bảng và danh sách ổ đĩa
function MiniBar({ value, track }: { value: number | null | undefined; track: string }) {
  const pct = typeof value === 'number' ? Math.max(0, Math.min(100, value)) : 0;
  return (
    <div className="flex items-center gap-2">
      <div className={`w-14 h-1.5 rounded-full overflow-hidden ${track}`}>
        <div className="h-full rounded-full" style={{ width: `${pct}%`, backgroundColor: levelHex(value) }} />
      </div>
      <span className="font-mono text-[11px] w-11">{fmtPct(value)}</span>
    </div>
  );
}

// ============================================================
// TRANG DASHBOARD
// ============================================================

export default function Dashboard() {
  const { isDark } = useTheme();
  const navigate = useNavigate();

  const [snapshot, setSnapshot] = useState<DashboardSnapshot | null>(null);
  const [series, setSeries] = useState<SeriesPoint[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  // 0 = không tự làm mới
  const [refreshMs, setRefreshMs] = useState(5000);

  // Máy đang xem chi tiết ('' = tự chọn máy online đầu tiên)
  const [selectedClientId, setSelectedClientId] = useState('');
  const [history, setHistory] = useState<{ clientId: string; points: HistoryPoint[] } | null>(null);
  const [historyError, setHistoryError] = useState<{ clientId: string; message: string } | null>(null);
  const [chartMode, setChartMode] = useState<'resource' | 'network'>('resource');

  const [statusFilter, setStatusFilter] = useState<'all' | ClientStatus>('all');
  const [searchTerm, setSearchTerm] = useState('');
  const [page, setPage] = useState(1);

  // ----------------------------------------------------------
  // Tải dữ liệu
  // ----------------------------------------------------------
  const load = useCallback(async () => {
    try {
      const data = await getDashboardSnapshot(10);
      setSnapshot(data);
      setLoadError(null);
      setSeries((prev) =>
        [
          ...prev,
          {
            online: data.stats.onlineClients,
            cpu: data.stats.avgCpu,
            mem: data.stats.avgMemory,
            alerts: data.stats.openAlerts,
          },
        ].slice(-MAX_SERIES_POINTS)
      );
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : 'Không thể tải dữ liệu Dashboard.');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    if (refreshMs === 0) {
      void load();
      return;
    }
    return startPolling(load, refreshMs);
  }, [refreshMs, load]);

  // Token hết hạn (401) -> về trang đăng nhập
  useEffect(() => {
    const onUnauthorized = () => navigate('/login', { replace: true });
    window.addEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
  }, [navigate]);

  const refreshNow = async () => {
    setIsRefreshing(true);
    await load();
    setIsRefreshing(false);
  };

  // ----------------------------------------------------------
  // Dữ liệu dẫn xuất
  // ----------------------------------------------------------
  const stats = snapshot?.stats;
  const clients = useMemo(() => snapshot?.clients ?? [], [snapshot]);

  const activeClientId = useMemo(() => {
    if (selectedClientId && clients.some((c) => c.clientId === selectedClientId)) {
      return selectedClientId;
    }
    const firstOnline = clients.find((c) => c.status === 'ONLINE');
    return (firstOnline ?? clients[0])?.clientId ?? '';
  }, [selectedClientId, clients]);

  const activeClient = clients.find((c) => c.clientId === activeClientId);
  const latest: LatestMetrics | undefined = snapshot?.latestByClient[activeClientId];

  // Lịch sử của máy đang chọn: tự làm mới mỗi 10 giây
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
        Download: p.networkDownloadSpeed,
        Upload: p.networkUploadSpeed,
      }));
  }, [history, activeClientId]);

  const chartError =
    historyError && historyError.clientId === activeClientId ? historyError.message : null;

  const hostnameById = useMemo(
    () => new Map(clients.map((c) => [c.clientId, c.hostname])),
    [clients]
  );

  // Bảng máy: lọc + tìm kiếm + phân trang
  const filteredClients = useMemo(() => {
    const keyword = searchTerm.trim().toLowerCase();
    return clients.filter((c) => {
      const matchesSearch =
        keyword === '' ||
        c.hostname.toLowerCase().includes(keyword) ||
        c.ipAddress.toLowerCase().includes(keyword) ||
        `${c.osName} ${c.osVersion}`.toLowerCase().includes(keyword);
      const matchesStatus = statusFilter === 'all' || c.status === statusFilter;
      return matchesSearch && matchesStatus;
    });
  }, [clients, searchTerm, statusFilter]);

  useEffect(() => {
    setPage(1);
  }, [searchTerm, statusFilter]);

  const totalPages = Math.max(1, Math.ceil(filteredClients.length / PAGE_SIZE));
  const currentPage = Math.min(page, totalPages);
  const pageStart = (currentPage - 1) * PAGE_SIZE;
  const pageClients = filteredClients.slice(pageStart, pageStart + PAGE_SIZE);
  const hasFilter = searchTerm !== '' || statusFilter !== 'all';

  const recentAlerts = (snapshot?.recentAlerts ?? []).slice(0, 6);

  // Biểu đồ tròn RAM của máy đang chọn
  const memory = latest?.memory;
  const ramData = memory
    ? [
        { name: 'Đã dùng', value: memory.usedBytes, color: levelHex(memory.usagePercent) },
        { name: 'Còn trống', value: Math.max(0, memory.totalBytes - memory.usedBytes), color: '#334155' },
      ]
    : [{ name: 'Chưa có dữ liệu', value: 1, color: '#334155' }];

  // ----------------------------------------------------------
  // Style (cùng bộ với trang Agents)
  // ----------------------------------------------------------
  const card = `rounded-2xl backdrop-blur-xl border transition-colors duration-500 ${
    isDark
      ? 'bg-[#081226]/80 border-blue-500/20 text-white shadow-[0_0_32px_-10px_rgba(37,99,235,0.45)]'
      : 'bg-white border-slate-200 text-slate-900 shadow-sm'
  }`;
  const muted = isDark ? 'text-slate-400' : 'text-slate-500';
  const subtle = isDark ? 'text-slate-300' : 'text-slate-600';
  const track = isDark ? 'bg-slate-700/60' : 'bg-slate-200';
  const fieldClass = `rounded-xl border px-3 py-2 text-xs font-medium focus:outline-none focus:ring-1 focus:ring-cyan-400 ${
    isDark ? 'bg-[#050c1c] border-blue-500/25 text-slate-200' : 'bg-slate-50 border-slate-300 text-slate-700'
  }`;
  const iconButton = `p-1.5 rounded-lg border transition-colors disabled:opacity-40 disabled:cursor-not-allowed ${
    isDark
      ? 'bg-blue-600/20 border-blue-500/40 text-cyan-300 hover:bg-blue-600/40'
      : 'bg-blue-50 border-blue-200 text-blue-600 hover:bg-blue-100'
  }`;
  const tooltipStyle = {
    backgroundColor: isDark ? '#0B1527' : '#ffffff',
    border: `1px solid ${isDark ? '#1e3a8a' : '#e2e8f0'}`,
    borderRadius: 12,
    fontSize: 12,
  };
  const gridColor = isDark ? '#1e293b' : '#e2e8f0';
  const axisColor = isDark ? '#64748b' : '#94a3b8';

  // Thẻ thống kê: số liệu thật + lịch sử các lần làm mới để vẽ sparkline
  const seriesOf = (key: 'online' | 'cpu' | 'mem' | 'alerts') =>
    series.map((s) => s[key]).filter((v): v is number => typeof v === 'number');
  const deltaOf = (values: number[]) => (values.length >= 2 ? values[values.length - 1] - values[0] : 0);
  const formatDelta = (value: number, unit = '') =>
    value > 0 ? `↑ +${Number(value.toFixed(1))}${unit}` : value < 0 ? `↓ ${Number(value.toFixed(1))}${unit}` : `↑ +0${unit}`;

  const statCards = [
    {
      key: 'online' as const,
      label: `Máy trực tuyến / ${stats?.totalClients ?? 0}`,
      sub: `${stats?.offlineClients ?? 0} mất kết nối · ${stats?.pendingClients ?? 0} chờ duyệt`,
      value: String(stats?.onlineClients ?? 0),
      unit: '',
      icon: <Monitor className="w-7 h-7" />,
      color: '#22c55e',
      iconBg: 'from-emerald-500 to-emerald-700 shadow-[0_0_26px_rgba(34,197,94,0.6)]',
    },
    {
      key: 'cpu' as const,
      label: 'CPU trung bình',
      sub: 'Các máy đang trực tuyến',
      value: typeof stats?.avgCpu === 'number' ? `${stats.avgCpu}%` : '--',
      unit: '%',
      icon: <Cpu className="w-7 h-7" />,
      color: '#22d3ee',
      iconBg: 'from-cyan-500 to-blue-700 shadow-[0_0_26px_rgba(34,211,238,0.6)]',
    },
    {
      key: 'mem' as const,
      label: 'RAM trung bình',
      sub: `Disk trung bình ${fmtPct(stats?.avgDisk)}`,
      value: typeof stats?.avgMemory === 'number' ? `${stats.avgMemory}%` : '--',
      unit: '%',
      icon: <HardDrive className="w-7 h-7" />,
      color: '#a78bfa',
      iconBg: 'from-violet-500 to-purple-700 shadow-[0_0_26px_rgba(139,92,246,0.6)]',
    },
    {
      key: 'alerts' as const,
      label: 'Cảnh báo chưa xử lý',
      sub: `${stats?.criticalAlerts ?? 0} nghiêm trọng · ${stats?.warningAlerts ?? 0} cảnh báo`,
      value: String(stats?.openAlerts ?? 0),
      unit: '',
      icon: <Bell className="w-7 h-7" />,
      color: '#ef4444',
      iconBg: 'from-rose-500 to-red-700 shadow-[0_0_26px_rgba(239,68,68,0.6)]',
    },
  ];

  const partialErrors = snapshot ? Object.values(snapshot.errors).filter(Boolean) : [];

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
          <span>Dashboard</span>
          <ChevronRight className="w-3 h-3" />
          <span className={isDark ? 'text-slate-200' : 'text-slate-700'}>Tổng quan hệ thống</span>
        </div>

        <div className="flex flex-col lg:flex-row justify-between items-start lg:items-center gap-4">
          <div className="flex items-center gap-4">
            <div className="p-3 rounded-2xl bg-gradient-to-br from-blue-500/30 to-cyan-400/10 border border-cyan-400/30 shadow-[0_0_24px_rgba(34,211,238,0.3)]">
              <Activity className="w-8 h-8 text-cyan-400" />
            </div>
            <div>
              <h1 className="text-2xl sm:text-3xl font-black tracking-tight">Tổng quan hệ thống</h1>
              <p className={`text-xs sm:text-sm mt-1 ${muted}`}>
                Giám sát tài nguyên CPU, RAM, Disk và mạng của các máy tính Linux
                {snapshot && ` · Cập nhật lúc ${snapshot.updatedAt.toLocaleTimeString('vi-VN')}`}
              </p>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-3 relative z-10">
            <div className={`flex items-center rounded-xl border overflow-hidden ${
              isDark ? 'bg-[#081226]/80 border-blue-500/40' : 'bg-white border-slate-300'
            }`}>
              <button
                onClick={refreshNow}
                disabled={isRefreshing}
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
                <option value={5000}>Tự động 5s</option>
                <option value={10000}>Tự động 10s</option>
                <option value={30000}>Tự động 30s</option>
                <option value={0}>Tắt tự động</option>
              </select>
            </div>

            <button
              onClick={() => navigate('/alerts')}
              className="px-5 py-2.5 rounded-xl bg-gradient-to-r from-blue-600 to-cyan-500 text-white text-xs font-bold shadow-[0_0_24px_rgba(34,211,238,0.45)] hover:from-blue-500 hover:to-cyan-400 transition-all flex items-center gap-2"
            >
              <Bell className="w-4 h-4" />
              Xem cảnh báo
            </button>
          </div>
        </div>
      </div>

      {/* Thông báo lỗi */}
      {loadError && (
        <div className="flex items-start gap-3 p-4 rounded-2xl border bg-red-500/10 border-red-500/30 text-red-400 text-sm">
          <ShieldAlert className="w-5 h-5 shrink-0 mt-0.5" />
          <div className="flex-1">
            <p className="font-semibold">{snapshot ? 'Mất kết nối tới máy chủ, đang hiển thị dữ liệu cũ' : 'Không thể tải dữ liệu Dashboard'}</p>
            <p className="text-xs mt-0.5 opacity-90">{loadError}</p>
          </div>
          <button onClick={refreshNow} className="text-xs font-bold underline underline-offset-2">Thử lại</button>
        </div>
      )}
      {partialErrors.length > 0 && (
        <div className="p-3 rounded-2xl border text-xs bg-amber-500/10 border-amber-500/30 text-amber-400 space-y-1">
          {partialErrors.map((w) => <p key={w}>{w}</p>)}
        </div>
      )}

      {/* 2. BỐN THẺ THỐNG KÊ */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        {statCards.map((item) => {
          const values = seriesOf(item.key);
          return (
            <div key={item.key} className={`${card} p-5 relative overflow-hidden`}>
              <div className="flex items-center gap-4">
                <div className={`w-14 h-14 shrink-0 rounded-2xl bg-gradient-to-br ${item.iconBg} flex items-center justify-center text-white`}>
                  {item.icon}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-3xl font-black leading-none">
                    {isLoading ? <Loader2 className="w-6 h-6 animate-spin" /> : item.value}
                  </p>
                  <p className={`text-xs mt-1.5 font-medium truncate ${subtle}`}>{item.label}</p>
                </div>
                <div className="flex flex-col items-end gap-1">
                  <span className="text-[11px] font-bold font-mono" style={{ color: item.color }}>
                    {formatDelta(deltaOf(values), item.unit)}
                  </span>
                  <Sparkline values={values} color={item.color} />
                </div>
              </div>
              <p className={`text-[11px] mt-3 pt-3 border-t truncate ${isDark ? 'border-blue-500/10' : 'border-slate-100'} ${muted}`}>
                {item.sub}
              </p>
            </div>
          );
        })}
      </div>

      {/* 3. BIỂU ĐỒ TÀI NGUYÊN + RAM / Ổ ĐĨA */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-5">

        {/* Lịch sử tài nguyên của máy đang chọn */}
        <div className={`${card} lg:col-span-8 p-5`}>
          <PanelTitle
            icon={<Activity className="w-4 h-4" />}
            title={`Tài nguyên ${HISTORY_MINUTES} phút gần nhất`}
            right={
              <div className="flex items-center gap-4 text-[11px]">
                {chartMode === 'resource' ? (
                  <>
                    <span className="flex items-center gap-1.5"><span className="w-2.5 h-2.5 rounded-full border-2 border-cyan-400" /><span className={subtle}>CPU</span></span>
                    <span className="flex items-center gap-1.5"><span className="w-2.5 h-2.5 rounded-full border-2 border-violet-400" /><span className={subtle}>RAM</span></span>
                    <span className="flex items-center gap-1.5"><span className="w-2.5 h-2.5 rounded-full border-2 border-amber-400" /><span className={subtle}>Disk</span></span>
                  </>
                ) : (
                  <>
                    <span className="flex items-center gap-1.5"><span className="w-2.5 h-2.5 rounded-full border-2 border-emerald-400" /><span className={subtle}>Tải xuống</span></span>
                    <span className="flex items-center gap-1.5"><span className="w-2.5 h-2.5 rounded-full border-2 border-pink-400" /><span className={subtle}>Tải lên</span></span>
                  </>
                )}
              </div>
            }
          />

          <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
            {clients.length > 0 ? (
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
            ) : (
              <span />
            )}

            <div className="flex items-center gap-1.5">
              {(
                [
                  ['resource', 'Tài nguyên'],
                  ['network', 'Mạng'],
                ] as const
              ).map(([mode, label]) => (
                <button
                  key={mode}
                  onClick={() => setChartMode(mode)}
                  className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-colors ${
                    chartMode === mode
                      ? 'bg-cyan-500 text-slate-950'
                      : isDark
                        ? 'bg-blue-600/20 border border-blue-500/40 text-cyan-300 hover:bg-blue-600/40'
                        : 'bg-blue-50 border border-blue-200 text-blue-600 hover:bg-blue-100'
                  }`}
                >
                  {label}
                </button>
              ))}
            </div>
          </div>

          <div className="h-60 w-full">
            {chartData.length > 0 ? (
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={chartData} margin={{ top: 5, right: 10, left: chartMode === 'resource' ? -16 : 8, bottom: 0 }}>
                  <defs>
                    <linearGradient id="dashCpuFill" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#22d3ee" stopOpacity={0.35} />
                      <stop offset="100%" stopColor="#22d3ee" stopOpacity={0} />
                    </linearGradient>
                    <linearGradient id="dashDownFill" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#34d399" stopOpacity={0.35} />
                      <stop offset="100%" stopColor="#34d399" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke={gridColor} />
                  <XAxis dataKey="time" tick={{ fontSize: 10, fill: axisColor }} minTickGap={40} />
                  {chartMode === 'resource' ? (
                    <YAxis domain={[0, 100]} unit="%" tick={{ fontSize: 10, fill: axisColor }} />
                  ) : (
                    <YAxis width={64} tickFormatter={(v) => fmtBytes(Number(v))} tick={{ fontSize: 10, fill: axisColor }} />
                  )}
                  <Tooltip
                    contentStyle={tooltipStyle}
                    formatter={(value) =>
                      chartMode === 'resource' ? `${Number(value).toFixed(1)}%` : `${fmtBytes(Number(value))}/s`
                    }
                  />
                  {chartMode === 'resource' ? (
                    <>
                      <Area type="monotone" dataKey="CPU" stroke="#22d3ee" strokeWidth={2} fill="url(#dashCpuFill)" dot={false} isAnimationActive={false} />
                      <Area type="monotone" dataKey="RAM" stroke="#a78bfa" strokeWidth={2} fill="none" dot={false} isAnimationActive={false} />
                      <Area type="monotone" dataKey="Disk" stroke="#fbbf24" strokeWidth={2} fill="none" dot={false} isAnimationActive={false} />
                    </>
                  ) : (
                    <>
                      <Area type="monotone" dataKey="Download" name="Tải xuống" stroke="#34d399" strokeWidth={2} fill="url(#dashDownFill)" dot={false} isAnimationActive={false} />
                      <Area type="monotone" dataKey="Upload" name="Tải lên" stroke="#f472b6" strokeWidth={2} fill="none" dot={false} isAnimationActive={false} />
                    </>
                  )}
                </AreaChart>
              </ResponsiveContainer>
            ) : (
              <div className={`h-full flex items-center justify-center text-xs text-center px-6 ${muted}`}>
                {chartError
                  ? chartError
                  : !activeClientId
                    ? isLoading ? 'Đang đồng bộ dữ liệu...' : 'Chưa có máy nào để hiển thị biểu đồ.'
                    : history?.clientId === activeClientId
                      ? `Máy này chưa gửi dữ liệu trong ${HISTORY_MINUTES} phút gần nhất.`
                      : 'Đang tải biểu đồ...'}
              </div>
            )}
          </div>
        </div>

        {/* RAM và ổ đĩa của máy đang chọn */}
        <div className={`${card} lg:col-span-4 p-5`}>
          <PanelTitle
            icon={<HardDrive className="w-4 h-4" />}
            title="RAM và ổ đĩa"
            right={<span className={`text-[11px] font-medium truncate max-w-[50%] ${muted}`}>{activeClient?.hostname ?? ''}</span>}
          />

          <div className="flex items-center gap-4">
            <div className="relative w-32 h-32 shrink-0">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={ramData}
                    dataKey="value"
                    innerRadius="68%"
                    outerRadius="95%"
                    paddingAngle={memory ? 3 : 0}
                    startAngle={90}
                    endAngle={-270}
                    stroke="none"
                  >
                    {ramData.map((d) => (
                      <Cell key={d.name} fill={d.color} />
                    ))}
                  </Pie>
                  {memory && <Tooltip contentStyle={tooltipStyle} formatter={(v) => fmtBytes(Number(v))} />}
                </PieChart>
              </ResponsiveContainer>
              <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
                <span className="text-xl font-black">{memory ? `${memory.usagePercent.toFixed(0)}%` : '--'}</span>
                <span className={`text-[10px] ${muted}`}>RAM</span>
              </div>
            </div>

            <div className="flex-1 space-y-3 text-xs min-w-0">
              <div className="flex items-center justify-between gap-2">
                <span className={subtle}>Đã dùng</span>
                <span className="font-mono font-bold">{fmtBytes(memory?.usedBytes)}</span>
              </div>
              <div className="flex items-center justify-between gap-2">
                <span className={subtle}>Tổng</span>
                <span className="font-mono font-bold">{fmtBytes(memory?.totalBytes)}</span>
              </div>
              <div className="flex items-center justify-between gap-2">
                <span className={subtle}>Load 1 phút</span>
                <span className="font-mono font-bold">
                  {typeof latest?.cpu?.loadAverage1m === 'number' ? latest.cpu.loadAverage1m.toFixed(2) : '--'}
                </span>
              </div>
            </div>
          </div>

          <div className={`mt-5 pt-4 border-t space-y-3 max-h-40 overflow-y-auto ${isDark ? 'border-blue-500/10' : 'border-slate-100'}`}>
            {!latest ? (
              <p className={`text-xs text-center py-3 ${muted}`}>Máy chưa gửi dữ liệu hoặc đang ngoại tuyến.</p>
            ) : latest.disks.length === 0 ? (
              <p className={`text-xs text-center py-3 ${muted}`}>Chưa có thông tin ổ đĩa.</p>
            ) : (
              latest.disks.map((disk) => (
                <div key={disk.diskId} className="space-y-1.5">
                  <div className="flex items-center justify-between gap-2 text-xs">
                    <span className="font-bold truncate">{disk.mountPoint || disk.deviceName}</span>
                    <span className={`font-mono text-[11px] ${muted}`}>
                      {fmtBytes(disk.usedBytes)} / {fmtBytes(disk.totalBytes)}
                    </span>
                  </div>
                  <div className={`h-1.5 rounded-full overflow-hidden ${track}`}>
                    <div
                      className="h-full rounded-full"
                      style={{ width: `${Math.min(100, disk.usagePercent)}%`, backgroundColor: levelHex(disk.usagePercent) }}
                    />
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </div>

      {/* 4. DANH SÁCH MÁY + CẢNH BÁO GẦN ĐÂY */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-5">

        {/* Bảng máy tính */}
        <div className={`${card} lg:col-span-8 p-5`}>
          <PanelTitle
            icon={<Monitor className="w-4 h-4" />}
            title="Máy tính đang giám sát"
            right={
              <div className="flex flex-wrap items-center justify-end gap-2">
                <select
                  value={statusFilter}
                  onChange={(e) => setStatusFilter(e.target.value as 'all' | ClientStatus)}
                  className={fieldClass}
                >
                  <option value="all">Tất cả trạng thái</option>
                  <option value="ONLINE">Online</option>
                  <option value="OFFLINE">Ngoại tuyến</option>
                  <option value="PENDING_APPROVAL">Chờ duyệt</option>
                  <option value="DISABLED">Vô hiệu hóa</option>
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
                    setStatusFilter('all');
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
                  <th className="p-3 rounded-l-xl">#</th>
                  <th className="p-3">Máy tính</th>
                  <th className="p-3">Hệ điều hành</th>
                  <th className="p-3">Trạng thái</th>
                  <th className="p-3">CPU</th>
                  <th className="p-3">RAM</th>
                  <th className="p-3">Disk</th>
                  <th className="p-3">Cập nhật cuối</th>
                  <th className="p-3 rounded-r-xl text-right">Chi tiết</th>
                </tr>
              </thead>
              <tbody>
                {isLoading ? (
                  <tr>
                    <td colSpan={9} className={`text-center py-10 ${muted}`}>
                      <Loader2 className="w-5 h-5 animate-spin inline mr-2 text-cyan-400" />
                      Đang tải danh sách máy...
                    </td>
                  </tr>
                ) : pageClients.length === 0 ? (
                  <tr>
                    <td colSpan={9} className={`text-center py-10 ${muted}`}>
                      {clients.length === 0
                        ? 'Chưa có máy nào trong hệ thống. Hãy cài Agent rồi duyệt tại trang Agents.'
                        : 'Không tìm thấy máy phù hợp.'}
                    </td>
                  </tr>
                ) : (
                  pageClients.map((client, idx) => {
                    const pill = STATUS_PILL[client.status] ?? STATUS_PILL.UNKNOWN;
                    const os = OS_STYLE[detectOs(client.osName)];
                    const metrics = snapshot?.latestByClient[client.clientId];
                    const selected = client.clientId === activeClientId;

                    return (
                      <tr
                        key={client.clientId}
                        onClick={() => setSelectedClientId(client.clientId)}
                        className={`border-b last:border-0 transition-colors cursor-pointer ${
                          isDark ? 'border-blue-500/10 hover:bg-blue-950/30' : 'border-slate-100 hover:bg-slate-50'
                        } ${selected ? (isDark ? 'bg-cyan-500/10' : 'bg-cyan-50') : ''}`}
                      >
                        <td className="p-3">
                          <span className={`inline-flex w-6 h-6 rounded-full border items-center justify-center text-[11px] font-mono ${
                            selected ? 'border-cyan-400 text-cyan-400' : isDark ? 'border-slate-600 text-slate-300' : 'border-slate-300 text-slate-600'
                          }`}>
                            {pageStart + idx + 1}
                          </span>
                        </td>
                        <td className="p-3">
                          <p className="font-bold">{client.hostname}</p>
                          <p className={`font-mono text-[11px] ${muted}`}>{client.ipAddress}</p>
                        </td>
                        <td className="p-3">
                          <span className="inline-flex items-center gap-2">
                            <span
                              className="w-6 h-6 rounded-full flex items-center justify-center text-[11px] font-bold text-white shrink-0"
                              style={{ backgroundColor: os.color }}
                            >
                              {os.letter}
                            </span>
                            <span className={subtle}>{[client.osName, client.osVersion].filter(Boolean).join(' ') || '—'}</span>
                          </span>
                        </td>
                        <td className="p-3">
                          <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[10px] font-semibold border ${pill.className}`}>
                            <span className={`w-1.5 h-1.5 rounded-full ${pill.dot} ${client.status === 'ONLINE' ? 'animate-pulse' : ''}`} />
                            {pill.label}
                          </span>
                        </td>
                        <td className="p-3">
                          {metrics ? <MiniBar value={metrics.cpu?.usagePercent} track={track} /> : <span className={muted}>—</span>}
                        </td>
                        <td className="p-3">
                          {metrics ? <MiniBar value={metrics.memory?.usagePercent} track={track} /> : <span className={muted}>—</span>}
                        </td>
                        <td className="p-3">
                          {metrics ? <MiniBar value={diskPercent(metrics)} track={track} /> : <span className={muted}>—</span>}
                        </td>
                        <td className={`p-3 font-mono text-[11px] ${muted}`}>{formatDateTime(toDate(client.lastSeenAt))}</td>
                        <td className="p-3">
                          <div className="flex items-center justify-end">
                            <button
                              className={iconButton}
                              title="Xem chi tiết máy"
                              onClick={(e) => {
                                e.stopPropagation();
                                navigate(`/computers/${encodeURIComponent(client.clientId)}`);
                              }}
                            >
                              <Eye className="w-3.5 h-3.5" />
                            </button>
                          </div>
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>

          {/* Phân trang */}
          <div className={`mt-4 pt-4 border-t flex flex-col sm:flex-row items-center justify-between gap-3 text-xs ${
            isDark ? 'border-blue-500/10 text-slate-400' : 'border-slate-100 text-slate-500'
          }`}>
            <span>
              {filteredClients.length === 0
                ? 'Không có kết quả'
                : `Hiển thị ${pageStart + 1}–${pageStart + pageClients.length} / ${filteredClients.length} máy`}
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

        {/* Cảnh báo gần đây */}
        <div className={`${card} lg:col-span-4 p-5`}>
          <PanelTitle
            icon={<Clock className="w-4 h-4" />}
            title="Cảnh báo gần đây"
            right={<span className="text-[11px] text-cyan-400 font-medium">{stats?.openAlerts ?? 0} đang mở</span>}
          />

          {recentAlerts.length === 0 ? (
            <p className={`text-xs text-center py-8 ${muted}`}>
              {isLoading ? 'Đang đồng bộ dữ liệu...' : 'Chưa có cảnh báo nào. Hệ thống đang ổn định.'}
            </p>
          ) : (
            <div className="relative">
              <div className={`absolute left-[5px] top-2 bottom-2 w-px ${isDark ? 'bg-blue-500/25' : 'bg-slate-200'}`} />
              <ul className="space-y-5">
                {recentAlerts.map((alert) => {
                  const severity = SEVERITY_COLOR[alert.severity] ?? SEVERITY_COLOR.INFO;
                  const resolved = alert.status === 'RESOLVED';
                  const color = resolved ? '#64748b' : severity.color;
                  const unit = PERCENT_RESOURCES.includes(alert.resourceType) ? '%' : '';

                  return (
                    <li key={alert.alertId} className="relative pl-6 flex items-start justify-between gap-3">
                      <span
                        className="absolute left-0 top-1 w-[11px] h-[11px] rounded-full ring-4"
                        style={{
                          backgroundColor: color,
                          boxShadow: `0 0 10px ${color}`,
                          ['--tw-ring-color' as string]: isDark ? '#081226' : '#ffffff',
                        }}
                      />
                      <div className="min-w-0">
                        <p className="text-[11px] font-mono text-cyan-400">{formatDateTime(toDate(alert.createdAt))}</p>
                        <p className="text-xs font-bold truncate" title={alert.message}>
                          {alert.ruleName || RESOURCE_LABEL[alert.resourceType] || 'Cảnh báo'}
                        </p>
                        <p className={`text-[11px] truncate ${muted}`}>
                          {hostnameById.get(alert.clientId) ?? alert.clientId} · {RESOURCE_LABEL[alert.resourceType]}{' '}
                          {Number(alert.currentValue).toFixed(1)}{unit} / ngưỡng {Number(alert.thresholdValue).toFixed(1)}{unit}
                        </p>
                      </div>
                      <p className="text-[11px] text-right shrink-0 font-medium" style={{ color }}>
                        {resolved ? 'Đã xử lý' : alert.status === 'ACKNOWLEDGED' ? 'Đã ghi nhận' : severity.label}
                      </p>
                    </li>
                  );
                })}
              </ul>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}