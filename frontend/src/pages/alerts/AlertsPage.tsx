import { useCallback, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Activity,
  AlertTriangle,
  Bell,
  Check,
  CheckCheck,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Clock,
  Eye,
  Filter,
  LayoutDashboard,
  Loader2,
  RefreshCw,
  Search,
  ShieldAlert,
  X,
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
  acknowledgeAlert,
  getAlerts,
  getClients,
  getSession,
  hasPermission,
  startPolling,
  toDate,
} from '../../lib/api';
import type {
  AlertInfo,
  AlertSeverity,
  AlertStatus,
  ClientInfo,
  ResourceType,
} from '../../lib/api';
import { useTheme } from '../../context/ThemeContext';

// ============================================================
// HẰNG SỐ
// ============================================================

const PAGE_SIZE = 6;
const MAX_SERIES_POINTS = 24;
// Chu kỳ cập nhật ngầm (mili giây)
const REFRESH_MS = 10000;

interface Counts {
  total: number;
  critical: number;
  warning: number;
  resolved: number;
}

const SEVERITY_STYLE: Record<AlertSeverity, { label: string; color: string; className: string }> = {
  CRITICAL: {
    label: 'Nghiêm trọng',
    color: '#ef4444',
    className: 'bg-red-500/15 text-red-400 border-red-500/40',
  },
  WARNING: {
    label: 'Cảnh báo',
    color: '#f59e0b',
    className: 'bg-amber-500/15 text-amber-400 border-amber-500/40',
  },
  INFO: {
    label: 'Thông tin',
    color: '#38bdf8',
    className: 'bg-sky-500/15 text-sky-400 border-sky-500/40',
  },
};

const STATUS_PILL: Record<AlertStatus, { label: string; className: string; dot: string }> = {
  ACTIVE: {
    label: 'Chờ xử lý',
    className: 'bg-amber-500/15 text-amber-400 border-amber-500/40',
    dot: 'bg-amber-400',
  },
  ACKNOWLEDGED: {
    label: 'Đã ghi nhận',
    className: 'bg-sky-500/15 text-sky-400 border-sky-500/40',
    dot: 'bg-sky-400',
  },
  RESOLVED: {
    label: 'Đã giải quyết',
    className: 'bg-emerald-500/15 text-emerald-400 border-emerald-500/40',
    dot: 'bg-emerald-400',
  },
};

const RESOURCE_LABEL: Record<ResourceType, string> = {
  CPU: 'CPU',
  MEMORY: 'RAM',
  DISK: 'Disk',
  NETWORK: 'Mạng',
  PROCESS: 'Tiến trình',
};

const RESOURCE_COLOR: Record<ResourceType, string> = {
  CPU: '#22d3ee',
  MEMORY: '#a78bfa',
  DISK: '#fbbf24',
  NETWORK: '#34d399',
  PROCESS: '#f472b6',
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

// Rút gọn các số thập phân dài trong nội dung cảnh báo (84.4290932 -> 84.4)
function cleanMessage(message: string): string {
  return message.replace(/\d+\.\d{3,}/g, (m) => Number(m).toFixed(1));
}

function isSameDay(date: Date, start: Date, end: Date): boolean {
  return date >= start && date < end;
}

function countAlerts(alerts: AlertInfo[]): Counts {
  const open = alerts.filter((a) => a.status !== 'RESOLVED');
  return {
    total: alerts.length,
    critical: open.filter((a) => a.severity === 'CRITICAL').length,
    warning: open.filter((a) => a.severity === 'WARNING').length,
    resolved: alerts.filter((a) => a.status === 'RESOLVED').length,
  };
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

// ============================================================
// TRANG QUẢN LÝ CẢNH BÁO
// ============================================================

export default function AlertsPage() {
  const { isDark } = useTheme();
  const navigate = useNavigate();
  const session = getSession();
  const canManage = hasPermission(session, 'ALERT_MANAGE');

  const [alerts, setAlerts] = useState<AlertInfo[]>([]);
  const [clients, setClients] = useState<ClientInfo[]>([]);
  const [warnings, setWarnings] = useState<string[]>([]);
  const [series, setSeries] = useState<Counts[]>([]);
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null);

  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | 'all' | null>(null);

  const [severityFilter, setSeverityFilter] = useState<'all' | AlertSeverity>('all');
  const [statusFilter, setStatusFilter] = useState<'all' | AlertStatus>('all');
  const [searchTerm, setSearchTerm] = useState('');
  const [page, setPage] = useState(1);

  // ----------------------------------------------------------
  // Tải dữ liệu
  // ----------------------------------------------------------
  const load = useCallback(async () => {
    const [alertsRes, clientsRes] = await Promise.allSettled([getAlerts(), getClients()]);

    if (alertsRes.status === 'rejected') {
      const reason = alertsRes.reason;
      setLoadError(reason instanceof Error ? reason.message : 'Không thể tải danh sách cảnh báo.');
      setIsLoading(false);
      return;
    }

    // Danh sách máy chỉ dùng để hiện tên máy, lỗi thì vẫn hiển thị cảnh báo
    const warn: string[] = [];
    if (clientsRes.status === 'fulfilled') {
      setClients(clientsRes.value);
    } else {
      const reason = clientsRes.reason;
      warn.push(`Danh sách máy: ${reason instanceof Error ? reason.message : 'không tải được'}`);
    }

    const sorted = [...alertsRes.value].sort(
      (a, b) => (toDate(b.createdAt)?.getTime() ?? 0) - (toDate(a.createdAt)?.getTime() ?? 0)
    );

    setAlerts(sorted);
    setWarnings(warn);
    setUpdatedAt(new Date());
    setLoadError(null);
    setSeries((prev) => [...prev, countAlerts(sorted)].slice(-MAX_SERIES_POINTS));
    setIsLoading(false);
  }, []);

  // Cập nhật ngầm theo chu kỳ cố định
  useEffect(() => startPolling(load, REFRESH_MS), [load]);

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
  // Thao tác
  // ----------------------------------------------------------
  const handleAcknowledge = async (alert: AlertInfo) => {
    setActionError(null);
    setBusyId(alert.alertId);
    try {
      await acknowledgeAlert(alert.alertId);
      await load();
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Không thể xác nhận cảnh báo.');
    } finally {
      setBusyId(null);
    }
  };

  const activeAlerts = useMemo(() => alerts.filter((a) => a.status === 'ACTIVE'), [alerts]);

  const handleAcknowledgeAll = async () => {
    if (activeAlerts.length === 0) return;
    if (!window.confirm(`Xác nhận ${activeAlerts.length} cảnh báo đang chờ xử lý?`)) return;

    setActionError(null);
    setBusyId('all');
    const results = await Promise.allSettled(activeAlerts.map((a) => acknowledgeAlert(a.alertId)));
    const failed = results.filter((r) => r.status === 'rejected').length;
    if (failed > 0) {
      setActionError(`Có ${failed}/${results.length} cảnh báo không xác nhận được.`);
    }
    await load();
    setBusyId(null);
  };

  // ----------------------------------------------------------
  // Dữ liệu dẫn xuất
  // ----------------------------------------------------------
  const counts = useMemo(() => countAlerts(alerts), [alerts]);
  const openCount = alerts.filter((a) => a.status !== 'RESOLVED').length;

  const hostnameById = useMemo(
    () => new Map(clients.map((c) => [c.clientId, c.hostname])),
    [clients]
  );
  const machineName = (clientId: string) => hostnameById.get(clientId) ?? clientId;

  const percent = (value: number) => (alerts.length === 0 ? 0 : Math.round((value / alerts.length) * 1000) / 10);

  // Biểu đồ tròn theo mức độ
  const severityData = useMemo(() => {
    const order: AlertSeverity[] = ['CRITICAL', 'WARNING', 'INFO'];
    return order
      .map((key) => ({
        name: SEVERITY_STYLE[key].label,
        value: alerts.filter((a) => a.severity === key).length,
        color: SEVERITY_STYLE[key].color,
      }))
      .filter((item) => item.value > 0);
  }, [alerts]);

  // Số cảnh báo phát sinh và được giải quyết trong 7 ngày gần nhất
  const weekData = useMemo(() => {
    const result = [];
    for (let i = 6; i >= 0; i--) {
      const start = new Date();
      start.setHours(0, 0, 0, 0);
      start.setDate(start.getDate() - i);
      const end = new Date(start);
      end.setDate(end.getDate() + 1);

      result.push({
        label: `${pad(start.getDate())}/${pad(start.getMonth() + 1)}`,
        created: alerts.filter((a) => {
          const d = toDate(a.createdAt);
          return d !== null && isSameDay(d, start, end);
        }).length,
        resolved: alerts.filter((a) => {
          const d = toDate(a.resolvedAt);
          return d !== null && isSameDay(d, start, end);
        }).length,
      });
    }
    return result;
  }, [alerts]);

  // Thống kê theo loại tài nguyên
  const resourceData = useMemo(() => {
    const keys = Object.keys(RESOURCE_LABEL) as ResourceType[];
    return keys
      .map((key) => ({
        key,
        name: RESOURCE_LABEL[key],
        value: alerts.filter((a) => a.resourceType === key).length,
        color: RESOURCE_COLOR[key],
      }))
      .filter((item) => item.value > 0)
      .sort((a, b) => b.value - a.value);
  }, [alerts]);
  const maxResource = Math.max(1, ...resourceData.map((r) => r.value));

  // Hoạt động gần đây: phát sinh, xác nhận, giải quyết (lấy từ mốc thời gian thật)
  const activities = useMemo(() => {
    const items: { key: string; time: Date; alert: AlertInfo; text: string; color: string }[] = [];
    alerts.forEach((a) => {
      const created = toDate(a.createdAt);
      const acked = toDate(a.acknowledgedAt);
      const resolved = toDate(a.resolvedAt);
      if (created) items.push({ key: `${a.alertId}-c`, time: created, alert: a, text: 'phát sinh cảnh báo', color: SEVERITY_STYLE[a.severity]?.color ?? '#38bdf8' });
      if (acked) items.push({ key: `${a.alertId}-a`, time: acked, alert: a, text: 'đã được ghi nhận', color: '#38bdf8' });
      if (resolved) items.push({ key: `${a.alertId}-r`, time: resolved, alert: a, text: 'đã giải quyết', color: '#22c55e' });
    });
    return items.sort((a, b) => b.time.getTime() - a.time.getTime()).slice(0, 6);
  }, [alerts]);

  // Bảng: lọc + tìm kiếm + phân trang
  const filteredAlerts = useMemo(() => {
    const keyword = searchTerm.trim().toLowerCase();
    return alerts.filter((a) => {
      const matchesSearch =
        keyword === '' ||
        (a.ruleName ?? '').toLowerCase().includes(keyword) ||
        a.message.toLowerCase().includes(keyword) ||
        machineName(a.clientId).toLowerCase().includes(keyword);
      const matchesSeverity = severityFilter === 'all' || a.severity === severityFilter;
      const matchesStatus = statusFilter === 'all' || a.status === statusFilter;
      return matchesSearch && matchesSeverity && matchesStatus;
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [alerts, searchTerm, severityFilter, statusFilter, hostnameById]);

  useEffect(() => {
    setPage(1);
  }, [searchTerm, severityFilter, statusFilter]);

  const totalPages = Math.max(1, Math.ceil(filteredAlerts.length / PAGE_SIZE));
  const currentPage = Math.min(page, totalPages);
  const pageStart = (currentPage - 1) * PAGE_SIZE;
  const pageAlerts = filteredAlerts.slice(pageStart, pageStart + PAGE_SIZE);

  const hasFilter = searchTerm !== '' || severityFilter !== 'all' || statusFilter !== 'all';
  const clearFilters = () => {
    setSearchTerm('');
    setSeverityFilter('all');
    setStatusFilter('all');
  };

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

  const first = series[0];
  const last = series[series.length - 1];
  const delta = (key: keyof Counts) => (first && last ? last[key] - first[key] : 0);
  const formatDelta = (value: number) => (value > 0 ? `↑ +${value}` : value < 0 ? `↓ ${value}` : '↑ +0');

  const statCards = [
    {
      key: 'total' as const,
      label: 'Tổng số cảnh báo',
      value: counts.total,
      icon: <Bell className="w-7 h-7" />,
      color: '#3b82f6',
      iconBg: 'from-blue-500 to-blue-700 shadow-[0_0_26px_rgba(59,130,246,0.65)]',
    },
    {
      key: 'critical' as const,
      label: 'Nghiêm trọng chưa xử lý',
      value: counts.critical,
      icon: <ShieldAlert className="w-7 h-7" />,
      color: '#ef4444',
      iconBg: 'from-rose-500 to-red-700 shadow-[0_0_26px_rgba(239,68,68,0.6)]',
    },
    {
      key: 'warning' as const,
      label: 'Cảnh báo chưa xử lý',
      value: counts.warning,
      icon: <AlertTriangle className="w-7 h-7" />,
      color: '#f59e0b',
      iconBg: 'from-amber-500 to-orange-600 shadow-[0_0_26px_rgba(245,158,11,0.6)]',
    },
    {
      key: 'resolved' as const,
      label: 'Đã giải quyết',
      value: counts.resolved,
      icon: <CheckCircle2 className="w-7 h-7" />,
      color: '#22c55e',
      iconBg: 'from-emerald-500 to-emerald-700 shadow-[0_0_26px_rgba(34,197,94,0.6)]',
    },
  ];

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
          <span>Alerts</span>
          <ChevronRight className="w-3 h-3" />
          <span className={isDark ? 'text-slate-200' : 'text-slate-700'}>Quản lý cảnh báo</span>
        </div>

        <div className="flex flex-col lg:flex-row justify-between items-start lg:items-center gap-4">
          <div className="flex items-center gap-4">
            <div className="p-3 rounded-2xl bg-gradient-to-br from-blue-500/30 to-cyan-400/10 border border-cyan-400/30 shadow-[0_0_24px_rgba(34,211,238,0.3)]">
              <Bell className="w-8 h-8 text-cyan-400" />
            </div>
            <div>
              <h1 className="text-2xl sm:text-3xl font-black tracking-tight">Quản lý cảnh báo</h1>
              <p className={`text-xs sm:text-sm mt-1 ${muted}`}>
                Theo dõi các sự cố vượt ngưỡng CPU, RAM, Disk, mạng trên các máy tính Linux
                {updatedAt && ` · Cập nhật lúc ${updatedAt.toLocaleTimeString('vi-VN')}`}
              </p>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-3 relative z-10">
            <button
              onClick={refreshNow}
              disabled={isRefreshing}
              className={`px-4 py-2.5 rounded-xl border text-xs font-bold flex items-center gap-2 disabled:opacity-60 ${
                isDark
                  ? 'bg-[#081226]/80 border-blue-500/40 text-slate-100 hover:bg-blue-600/20'
                  : 'bg-white border-slate-300 text-slate-700 hover:bg-slate-100'
              }`}
            >
              <RefreshCw className={`w-4 h-4 ${isLoading || isRefreshing ? 'animate-spin' : ''}`} />
              Làm mới
            </button>

            {canManage && (
              <button
                onClick={handleAcknowledgeAll}
                disabled={activeAlerts.length === 0 || busyId !== null}
                className="px-5 py-2.5 rounded-xl bg-gradient-to-r from-blue-600 to-cyan-500 text-white text-xs font-bold shadow-[0_0_24px_rgba(34,211,238,0.45)] hover:from-blue-500 hover:to-cyan-400 transition-all flex items-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {busyId === 'all' ? <Loader2 className="w-4 h-4 animate-spin" /> : <CheckCheck className="w-4 h-4" />}
                Xác nhận tất cả ({activeAlerts.length})
              </button>
            )}
          </div>
        </div>
      </div>

      {/* Thông báo lỗi */}
      {loadError && (
        <div className="flex items-start gap-3 p-4 rounded-2xl border bg-red-500/10 border-red-500/30 text-red-400 text-sm">
          <ShieldAlert className="w-5 h-5 shrink-0 mt-0.5" />
          <div className="flex-1">
            <p className="font-semibold">Không thể tải danh sách cảnh báo</p>
            <p className="text-xs mt-0.5 opacity-90">{loadError}</p>
          </div>
          <button onClick={refreshNow} className="text-xs font-bold underline underline-offset-2">Thử lại</button>
        </div>
      )}
      {actionError && (
        <div className="flex items-start gap-3 p-4 rounded-2xl border bg-red-500/10 border-red-500/30 text-red-400 text-sm">
          <ShieldAlert className="w-5 h-5 shrink-0 mt-0.5" />
          <p className="flex-1">{actionError}</p>
          <button onClick={() => setActionError(null)} aria-label="Đóng"><X className="w-4 h-4" /></button>
        </div>
      )}
      {warnings.length > 0 && (
        <div className="p-3 rounded-2xl border text-xs bg-amber-500/10 border-amber-500/30 text-amber-400 space-y-1">
          {warnings.map((w) => <p key={w}>{w}</p>)}
        </div>
      )}

      {/* 2. BỐN THẺ THỐNG KÊ */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        {statCards.map((item) => (
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
                  {formatDelta(delta(item.key))}
                </span>
                <Sparkline values={series.map((s) => s[item.key])} color={item.color} />
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* 3. BA BIỂU ĐỒ */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-5">

        {/* Theo mức độ */}
        <div className={`${card} lg:col-span-4 p-5`}>
          <PanelTitle icon={<ShieldAlert className="w-4 h-4" />} title="Cảnh báo theo mức độ" />
          <div className="flex items-center gap-4">
            <div className="relative w-40 h-40 shrink-0">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={alerts.length === 0 ? [{ name: 'Chưa có dữ liệu', value: 1, color: '#334155' }] : severityData}
                    dataKey="value"
                    innerRadius="68%"
                    outerRadius="95%"
                    paddingAngle={severityData.length > 1 ? 3 : 0}
                    startAngle={90}
                    endAngle={-270}
                    stroke="none"
                  >
                    {(alerts.length === 0 ? [{ name: 'x', color: '#334155' }] : severityData).map((d) => (
                      <Cell key={d.name} fill={d.color} />
                    ))}
                  </Pie>
                  {alerts.length > 0 && <Tooltip contentStyle={tooltipStyle} />}
                </PieChart>
              </ResponsiveContainer>
              <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
                <span className="text-2xl font-black">{openCount}</span>
                <span className={`text-[10px] ${muted}`}>Đang mở</span>
              </div>
            </div>

            <div className="flex-1 space-y-3 text-xs">
              {severityData.length === 0 ? (
                <p className={muted}>Chưa có cảnh báo nào.</p>
              ) : (
                severityData.map((item) => (
                  <div key={item.name} className="flex items-center justify-between gap-2">
                    <div className="flex items-center gap-2">
                      <span className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: item.color }} />
                      <span className={subtle}>{item.name}</span>
                    </div>
                    <span className="font-mono font-bold">
                      {item.value} <span className={`font-normal ${muted}`}>{percent(item.value)}%</span>
                    </span>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>

        {/* 7 ngày qua */}
        <div className={`${card} lg:col-span-5 p-5`}>
          <PanelTitle
            icon={<Activity className="w-4 h-4" />}
            title="Cảnh báo 7 ngày qua"
            right={
              <div className="flex items-center gap-4 text-[11px]">
                <span className="flex items-center gap-1.5"><span className="w-2.5 h-2.5 rounded-full border-2 border-cyan-400" /><span className={subtle}>Phát sinh</span></span>
                <span className="flex items-center gap-1.5"><span className="w-2.5 h-2.5 rounded-full border-2 border-emerald-400" /><span className={subtle}>Đã giải quyết</span></span>
              </div>
            }
          />
          <div className="h-44 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={weekData} margin={{ top: 5, right: 10, left: -24, bottom: 0 }}>
                <defs>
                  <linearGradient id="alertsCreatedFill" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#22d3ee" stopOpacity={0.35} />
                    <stop offset="100%" stopColor="#22d3ee" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke={gridColor} />
                <XAxis dataKey="label" tick={{ fontSize: 10, fill: axisColor }} />
                <YAxis allowDecimals={false} tick={{ fontSize: 10, fill: axisColor }} />
                <Tooltip contentStyle={tooltipStyle} />
                <Area
                  type="monotone"
                  dataKey="created"
                  name="Phát sinh"
                  stroke="#22d3ee"
                  strokeWidth={2}
                  fill="url(#alertsCreatedFill)"
                  dot={{ r: 3, fill: '#0b1527', strokeWidth: 2 }}
                  isAnimationActive={false}
                />
                <Area
                  type="monotone"
                  dataKey="resolved"
                  name="Đã giải quyết"
                  stroke="#34d399"
                  strokeWidth={2}
                  fill="none"
                  dot={{ r: 3, fill: '#0b1527', strokeWidth: 2 }}
                  isAnimationActive={false}
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Theo tài nguyên */}
        <div className={`${card} lg:col-span-3 p-5`}>
          <PanelTitle icon={<Activity className="w-4 h-4" />} title="Theo tài nguyên" />
          <div className="space-y-4">
            {resourceData.length === 0 ? (
              <p className={`text-xs text-center py-8 ${muted}`}>Chưa có dữ liệu</p>
            ) : (
              resourceData.map((item) => (
                <div key={item.key} className="space-y-1.5">
                  <div className="flex items-center justify-between text-xs">
                    <div className="flex items-center gap-2">
                      <span className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: item.color }} />
                      <span className={subtle}>{item.name}</span>
                    </div>
                    <span className="font-mono font-bold">{item.value}</span>
                  </div>
                  <div className={`h-1.5 rounded-full overflow-hidden ${track}`}>
                    <div
                      className="h-full rounded-full"
                      style={{ width: `${(item.value / maxResource) * 100}%`, backgroundColor: item.color }}
                    />
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </div>

      {/* 4. DANH SÁCH CẢNH BÁO + HOẠT ĐỘNG GẦN ĐÂY */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-5">

        {/* Bảng cảnh báo */}
        <div className={`${card} lg:col-span-8 p-5`}>
          <PanelTitle
            icon={<Bell className="w-4 h-4" />}
            title="Danh sách cảnh báo"
            right={
              <div className="flex flex-wrap items-center justify-end gap-2">
                <select
                  value={severityFilter}
                  onChange={(e) => setSeverityFilter(e.target.value as 'all' | AlertSeverity)}
                  className={fieldClass}
                >
                  <option value="all">Tất cả mức độ</option>
                  <option value="CRITICAL">Nghiêm trọng</option>
                  <option value="WARNING">Cảnh báo</option>
                  <option value="INFO">Thông tin</option>
                </select>

                <select
                  value={statusFilter}
                  onChange={(e) => setStatusFilter(e.target.value as 'all' | AlertStatus)}
                  className={fieldClass}
                >
                  <option value="all">Tất cả trạng thái</option>
                  <option value="ACTIVE">Chờ xử lý</option>
                  <option value="ACKNOWLEDGED">Đã ghi nhận</option>
                  <option value="RESOLVED">Đã giải quyết</option>
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
                  onClick={clearFilters}
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
                  <th className="p-3">Nội dung cảnh báo</th>
                  <th className="p-3">Máy tính</th>
                  <th className="p-3">Mức độ</th>
                  <th className="p-3">Giá trị / Ngưỡng</th>
                  <th className="p-3">Thời gian</th>
                  <th className="p-3">Trạng thái</th>
                  <th className="p-3 rounded-r-xl text-right">Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {isLoading ? (
                  <tr>
                    <td colSpan={8} className={`text-center py-10 ${muted}`}>
                      <Loader2 className="w-5 h-5 animate-spin inline mr-2 text-cyan-400" />
                      Đang tải danh sách cảnh báo...
                    </td>
                  </tr>
                ) : pageAlerts.length === 0 ? (
                  <tr>
                    <td colSpan={8} className={`text-center py-10 ${muted}`}>
                      {alerts.length === 0
                        ? 'Chưa có cảnh báo nào. Hệ thống đang ổn định.'
                        : 'Không tìm thấy cảnh báo phù hợp.'}
                    </td>
                  </tr>
                ) : (
                  pageAlerts.map((alert, idx) => {
                    const severity = SEVERITY_STYLE[alert.severity] ?? SEVERITY_STYLE.INFO;
                    const pill = STATUS_PILL[alert.status] ?? STATUS_PILL.ACTIVE;
                    const unit = PERCENT_RESOURCES.includes(alert.resourceType) ? '%' : '';
                    const isBusy = busyId === alert.alertId;

                    return (
                      <tr
                        key={alert.alertId}
                        className={`border-b last:border-0 transition-colors ${
                          isDark ? 'border-blue-500/10 hover:bg-blue-950/30' : 'border-slate-100 hover:bg-slate-50'
                        } ${isBusy ? 'opacity-60' : ''}`}
                      >
                        <td className="p-3">
                          <span className={`inline-flex w-6 h-6 rounded-full border items-center justify-center text-[11px] font-mono ${
                            isDark ? 'border-slate-600 text-slate-300' : 'border-slate-300 text-slate-600'
                          }`}>
                            {pageStart + idx + 1}
                          </span>
                        </td>
                        <td className="p-3 max-w-[16rem]">
                          <p className="font-bold truncate">
                            {alert.ruleName || RESOURCE_LABEL[alert.resourceType] || 'Cảnh báo'}
                          </p>
                          <p className={`text-[11px] truncate ${muted}`} title={cleanMessage(alert.message)}>
                            {cleanMessage(alert.message)}
                          </p>
                        </td>
                        <td className={`p-3 ${subtle}`}>{machineName(alert.clientId)}</td>
                        <td className="p-3">
                          <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-[10px] font-semibold border ${severity.className}`}>
                            {severity.label}
                          </span>
                        </td>
                        <td className="p-3 font-mono text-[11px]">
                          <span className="font-bold">
                            {RESOURCE_LABEL[alert.resourceType]} {Number(alert.currentValue).toFixed(1)}{unit}
                          </span>
                          <span className={`block ${muted}`}>
                            ngưỡng {Number(alert.thresholdValue).toFixed(1)}{unit}
                          </span>
                        </td>
                        <td className={`p-3 font-mono text-[11px] ${muted}`}>{formatDateTime(toDate(alert.createdAt))}</td>
                        <td className="p-3">
                          <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[10px] font-semibold border ${pill.className}`}>
                            <span className={`w-1.5 h-1.5 rounded-full ${pill.dot} ${alert.status === 'ACTIVE' ? 'animate-pulse' : ''}`} />
                            {pill.label}
                          </span>
                        </td>
                        <td className="p-3">
                          <div className="flex items-center justify-end gap-1.5">
                            {isBusy ? (
                              <Loader2 className="w-4 h-4 animate-spin text-cyan-400" />
                            ) : (
                              <>
                                <button
                                  className={iconButton}
                                  title="Xem chi tiết máy"
                                  onClick={() => navigate(`/computers/${encodeURIComponent(alert.clientId)}`)}
                                >
                                  <Eye className="w-3.5 h-3.5" />
                                </button>
                                {alert.status === 'ACTIVE' && canManage && (
                                  <button
                                    className={iconButton}
                                    title="Xác nhận đã xem cảnh báo"
                                    onClick={() => handleAcknowledge(alert)}
                                  >
                                    <Check className="w-3.5 h-3.5 text-emerald-400" />
                                  </button>
                                )}
                              </>
                            )}
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
              {filteredAlerts.length === 0
                ? 'Không có kết quả'
                : `Hiển thị ${pageStart + 1}–${pageStart + pageAlerts.length} / ${filteredAlerts.length} cảnh báo`}
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

        {/* Hoạt động gần đây */}
        <div className={`${card} lg:col-span-4 p-5`}>
          <PanelTitle
            icon={<Clock className="w-4 h-4" />}
            title="Hoạt động gần đây"
            right={<span className="text-[11px] text-cyan-400 font-medium">{activities.length} sự kiện</span>}
          />

          {activities.length === 0 ? (
            <p className={`text-xs text-center py-8 ${muted}`}>
              {isLoading ? 'Đang đồng bộ dữ liệu...' : 'Chưa có hoạt động nào.'}
            </p>
          ) : (
            <div className="relative">
              <div className={`absolute left-[5px] top-2 bottom-2 w-px ${isDark ? 'bg-blue-500/25' : 'bg-slate-200'}`} />
              <ul className="space-y-5">
                {activities.map(({ key, time, alert, text, color }) => (
                  <li key={key} className="relative pl-6 flex items-start justify-between gap-3">
                    <span
                      className="absolute left-0 top-1 w-[11px] h-[11px] rounded-full ring-4"
                      style={{
                        backgroundColor: color,
                        boxShadow: `0 0 10px ${color}`,
                        ['--tw-ring-color' as string]: isDark ? '#081226' : '#ffffff',
                      }}
                    />
                    <div className="min-w-0">
                      <p className="text-[11px] font-mono text-cyan-400">{formatDateTime(time)}</p>
                      <p className="text-xs font-bold truncate">{machineName(alert.clientId)}</p>
                      <p className={`text-[11px] truncate ${muted}`}>
                        {alert.ruleName || RESOURCE_LABEL[alert.resourceType]}
                      </p>
                    </div>
                    <p className={`text-[11px] text-right shrink-0 max-w-[45%] ${muted}`}>{text}</p>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}