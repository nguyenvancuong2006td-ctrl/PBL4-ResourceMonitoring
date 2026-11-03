import { useCallback, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Server,
  Monitor,
  CheckCircle2,
  Clock,
  ShieldAlert,
  Search,
  RefreshCw,
  Download,
  Eye,
  Activity,
  Check,
  X,
  ShieldOff,
  Filter,
  ChevronRight,
  ChevronLeft,
  LayoutDashboard,
  Loader2,
  Terminal,
  Trash2,
  AlertTriangle,
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
  ApiError,
  UNAUTHORIZED_EVENT,
  getSession,
  hasPermission,
  getClients,
  getClientAgents,
  approveAgent,
  rejectAgent,
  revokeAgent,
  deleteAgent,
  startPolling,
  toDate,
} from '../../lib/api';
import type { AgentStatus, ClientInfo } from '../../lib/api';
import { useTheme } from '../../context/ThemeContext';

// ============================================================
// KIỂU DỮ LIỆU VÀ HẰNG SỐ
// ============================================================

const PAGE_SIZE = 6;
const MAX_SERIES_POINTS = 24;

type OsFamily = 'Ubuntu' | 'Debian' | 'Windows' | 'Khác';

interface AgentRow {
  agentId: string;
  clientId: string | null;
  computer: string;
  os: string;
  osFamily: OsFamily;
  ip: string;
  status: AgentStatus;
  version: string | null;
  registeredAt: Date | null;
  lastUpdate: Date | null;
}

interface Counts {
  total: number;
  online: number;
  pending: number;
  offline: number;
}

interface AgentsData {
  rows: AgentRow[];
  totalMachines: number;
  warnings: string[];
}

// Màu chính của từng trạng thái
const STATUS_COLOR = {
  online: '#22c55e',
  pending: '#f59e0b',
  offline: '#ef4444',
  other: '#64748b',
};

const STATUS_PILL: Record<AgentStatus, { label: string; className: string; dot: string }> = {
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
    label: 'Đã thu hồi',
    className: 'bg-slate-500/15 text-slate-400 border-slate-500/40',
    dot: 'bg-slate-400',
  },
  REJECTED: {
    label: 'Đã từ chối',
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

const OS_CHART_COLOR: Record<OsFamily, string> = {
  Ubuntu: '#f59e0b',
  Debian: '#8b5cf6',
  Windows: '#3b82f6',
  Khác: '#94a3b8',
};

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

function countRows(rows: AgentRow[], totalMachines: number): Counts {
  return {
    total: totalMachines,
    online: rows.filter((r) => r.status === 'ONLINE').length,
    pending: rows.filter((r) => r.status === 'PENDING_APPROVAL').length,
    offline: rows.filter((r) => r.status === 'OFFLINE').length,
  };
}

// Lấy Agent và Máy từ backend rồi ghép theo clientId.
// Danh sách Agent là bắt buộc, danh sách Máy lỗi thì vẫn hiển thị Agent.
async function loadAgents(): Promise<AgentsData> {
  const [agentsRes, clientsRes] = await Promise.allSettled([getClientAgents(), getClients()]);

  if (agentsRes.status === 'rejected') {
    throw agentsRes.reason;
  }

  const warnings: string[] = [];
  let clients: ClientInfo[] = [];
  if (clientsRes.status === 'fulfilled') {
    clients = clientsRes.value;
  } else {
    const reason = clientsRes.reason;
    warnings.push(`Danh sách máy: ${reason instanceof Error ? reason.message : 'không tải được'}`);
  }

  const clientMap = new Map<string, ClientInfo>(
    clients.map((c): [string, ClientInfo] => [c.clientId, c])
  );

  const rows: AgentRow[] = agentsRes.value.map((a) => {
    const clientId = a.client?.clientId ?? a.clientId ?? null;
    const client = clientId ? clientMap.get(clientId) : undefined;
    const osText = client ? [client.osName, client.osVersion].filter(Boolean).join(' ') : '';

    return {
      agentId: a.agentId,
      clientId,
      computer: client?.hostname || clientId || '—',
      os: osText || '—',
      osFamily: detectOs(client?.osName ?? ''),
      ip: client?.ipAddress || '—',
      status: a.status,
      version: a.version,
      registeredAt: toDate(a.registeredAt),
      lastUpdate: toDate(a.lastHeartbeatAt) ?? toDate(a.lastConnectedAt) ?? toDate(client?.lastSeenAt),
    };
  });

  const totalMachines =
    clients.length > 0
      ? clients.length
      : new Set(rows.map((r) => r.clientId).filter((v): v is string => !!v)).size;

  return { rows, totalMachines, warnings };
}

// ============================================================
// COMPONENT PHỤ
// ============================================================

// Ảnh linh vật Tux: đặt file /public/tux.png, không có thì dùng hình thay thế
function TuxImage({ className, fallback }: { className?: string; fallback: ReactNode }) {
  const [failed, setFailed] = useState(false);
  if (failed) return <>{fallback}</>;
  return <img src="/tux.png" alt="" className={className} onError={() => setFailed(true)} />;
}

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
// TRANG QUẢN LÝ AGENT
// ============================================================

export default function AgentsPage() {
  const { isDark } = useTheme();
  const navigate = useNavigate();
  const session = getSession();
  const canManage = hasPermission(session, 'AGENT_MANAGE');

  const [rows, setRows] = useState<AgentRow[]>([]);
  const [totalMachines, setTotalMachines] = useState(0);
  const [warnings, setWarnings] = useState<string[]>([]);
  const [series, setSeries] = useState<Counts[]>([]);
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null);

  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busyAgentId, setBusyAgentId] = useState<string | null>(null);

  // Xóa Agent: Agent đang chờ xác nhận, nội dung ô xác nhận, trạng thái gọi API
  const [deleteTarget, setDeleteTarget] = useState<AgentRow | null>(null);
  const [deleteConfirmText, setDeleteConfirmText] = useState('');
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  // 0 = không tự làm mới
  const [refreshMs, setRefreshMs] = useState(10_000);
  const [showInstall, setShowInstall] = useState(false);

  const [statusFilter, setStatusFilter] = useState<'all' | AgentStatus>('all');
  const [osFilter, setOsFilter] = useState<'all' | OsFamily>('all');
  const [searchTerm, setSearchTerm] = useState('');
  const [page, setPage] = useState(1);

  // ----------------------------------------------------------
  // Tải dữ liệu
  // ----------------------------------------------------------
  const load = useCallback(async () => {
    try {
      const data = await loadAgents();
      setRows(data.rows);
      setTotalMachines(data.totalMachines);
      setWarnings(data.warnings);
      setUpdatedAt(new Date());
      setLoadError(null);
      setSeries((prev) => [...prev, countRows(data.rows, data.totalMachines)].slice(-MAX_SERIES_POINTS));
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : 'Không thể tải danh sách Agent.');
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

  // Thông báo thành công tự ẩn sau vài giây
  useEffect(() => {
    if (!notice) return;
    const timer = window.setTimeout(() => setNotice(null), 6000);
    return () => window.clearTimeout(timer);
  }, [notice]);

  // Nhấn Esc để đóng hộp thoại xóa (không đóng khi đang xóa)
  useEffect(() => {
    if (!deleteTarget) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !isDeleting) {
        setDeleteTarget(null);
        setDeleteConfirmText('');
        setDeleteError(null);
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [deleteTarget, isDeleting]);

  const refreshNow = async () => {
    setIsRefreshing(true);
    await load();
    setIsRefreshing(false);
  };

  const runAction = async (agentId: string, action: () => Promise<unknown>) => {
    setActionError(null);
    setBusyAgentId(agentId);
    try {
      await action();
      await load();
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Thao tác thất bại.');
    } finally {
      setBusyAgentId(null);
    }
  };

  const handleApprove = (agent: AgentRow) => {
    void runAction(agent.agentId, () => approveAgent(agent.agentId));
  };

  const handleReject = (agent: AgentRow) => {
    if (!window.confirm(`Từ chối Agent "${agent.agentId}"?`)) return;
    void runAction(agent.agentId, () => rejectAgent(agent.agentId));
  };

  const handleRevoke = (agent: AgentRow) => {
    const label = agent.computer !== '—' ? `${agent.agentId} (${agent.computer})` : agent.agentId;
    const confirmed = window.confirm(
      `Thu hồi Agent "${label}"?\n\n` +
        'Credential của Agent sẽ bị vô hiệu vĩnh viễn và không thể kích hoạt lại. ' +
        'Agent sẽ ngừng gửi dữ liệu và phải đăng ký lại để được duyệt mới.'
    );
    if (!confirmed) return;
    void runAction(agent.agentId, () => revokeAgent(agent.agentId));
  };

  // ----------------------------------------------------------
  // Xóa Agent (xóa luôn máy và toàn bộ dữ liệu liên quan, không hoàn tác)
  // ----------------------------------------------------------
  const openDelete = (agent: AgentRow) => {
    setActionError(null);
    setDeleteTarget(agent);
    setDeleteConfirmText('');
    setDeleteError(null);
  };

  const closeDelete = () => {
    if (isDeleting) return;
    setDeleteTarget(null);
    setDeleteConfirmText('');
    setDeleteError(null);
  };

  const deleteConfirmed = deleteTarget !== null && deleteConfirmText.trim() === deleteTarget.agentId;

  const handleDelete = async () => {
    if (!deleteTarget || !deleteConfirmed || isDeleting) return;

    const target = deleteTarget;
    setIsDeleting(true);
    setDeleteError(null);

    try {
      await deleteAgent(target.agentId);
      setNotice(`Đã xóa Agent "${target.agentId}" cùng máy và toàn bộ dữ liệu liên quan.`);
      setDeleteTarget(null);
      setDeleteConfirmText('');
      await load();
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) {
        // Agent đã bị xóa ở nơi khác: đóng hộp thoại và làm mới danh sách
        setNotice(`Agent "${target.agentId}" không còn tồn tại, có thể đã được xóa trước đó.`);
        setDeleteTarget(null);
        setDeleteConfirmText('');
        await load();
      } else {
        setDeleteError(error instanceof Error ? error.message : 'Không thể xóa Agent.');
      }
    } finally {
      setIsDeleting(false);
    }
  };

  // ----------------------------------------------------------
  // Dữ liệu dẫn xuất
  // ----------------------------------------------------------
  const counts = useMemo(() => countRows(rows, totalMachines), [rows, totalMachines]);
  const otherCount = rows.length - counts.online - counts.pending - counts.offline;
  const percent = (value: number) => (rows.length === 0 ? 0 : Math.round((value / rows.length) * 1000) / 10);

  const statusData = useMemo(() => {
    const items = [
      { name: 'Hoạt động', value: counts.online, color: STATUS_COLOR.online },
      { name: 'Chờ duyệt', value: counts.pending, color: STATUS_COLOR.pending },
      { name: 'Ngoại tuyến', value: counts.offline, color: STATUS_COLOR.offline },
    ];
    if (otherCount > 0) {
      items.push({ name: 'Thu hồi / Từ chối', value: otherCount, color: STATUS_COLOR.other });
    }
    return items;
  }, [counts, otherCount]);

  const osData = useMemo(() => {
    const families: OsFamily[] = ['Ubuntu', 'Debian', 'Windows', 'Khác'];
    return families
      .map((name) => ({
        name,
        value: rows.filter((r) => r.osFamily === name).length,
        color: OS_CHART_COLOR[name],
      }))
      .filter((item) => item.value > 0);
  }, [rows]);

  // Số Agent đã đăng ký tích lũy và đăng ký mới trong 7 ngày gần nhất
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
        total: rows.filter((r) => !r.registeredAt || r.registeredAt < end).length,
        added: rows.filter((r) => r.registeredAt && r.registeredAt >= start && r.registeredAt < end).length,
      });
    }
    return result;
  }, [rows]);

  // Hoạt động gần đây: suy ra từ trạng thái và mốc thời gian của từng Agent
  const activities = useMemo(() => {
    const describe = (agent: AgentRow) => {
      switch (agent.status) {
        case 'ONLINE':
          return { text: 'đã gửi dữ liệu hệ thống', color: STATUS_COLOR.online, time: agent.lastUpdate ?? agent.registeredAt };
        case 'PENDING_APPROVAL':
          return { text: 'đang chờ duyệt', color: STATUS_COLOR.pending, time: agent.registeredAt };
        case 'OFFLINE':
          return { text: 'bị mất kết nối', color: STATUS_COLOR.offline, time: agent.lastUpdate ?? agent.registeredAt };
        case 'DISABLED':
          return { text: 'đã bị thu hồi', color: STATUS_COLOR.other, time: agent.lastUpdate ?? agent.registeredAt };
        default:
          return { text: 'đã bị từ chối', color: STATUS_COLOR.other, time: agent.registeredAt };
      }
    };

    return rows
      .map((agent) => ({ agent, ...describe(agent) }))
      .sort((a, b) => (b.time?.getTime() ?? 0) - (a.time?.getTime() ?? 0))
      .slice(0, 6);
  }, [rows]);

  const osOptions = useMemo(
    () => Array.from(new Set(rows.map((r) => r.osFamily))),
    [rows]
  );

  const filteredRows = useMemo(() => {
    const keyword = searchTerm.trim().toLowerCase();
    return rows.filter((r) => {
      const matchesSearch =
        keyword === '' ||
        r.agentId.toLowerCase().includes(keyword) ||
        r.computer.toLowerCase().includes(keyword) ||
        r.ip.toLowerCase().includes(keyword) ||
        r.os.toLowerCase().includes(keyword);
      const matchesStatus = statusFilter === 'all' || r.status === statusFilter;
      const matchesOs = osFilter === 'all' || r.osFamily === osFilter;
      return matchesSearch && matchesStatus && matchesOs;
    });
  }, [rows, searchTerm, statusFilter, osFilter]);

  useEffect(() => {
    setPage(1);
  }, [searchTerm, statusFilter, osFilter]);

  const totalPages = Math.max(1, Math.ceil(filteredRows.length / PAGE_SIZE));
  const currentPage = Math.min(page, totalPages);
  const pageStart = (currentPage - 1) * PAGE_SIZE;
  const pageRows = filteredRows.slice(pageStart, pageStart + PAGE_SIZE);

  const hasFilter = searchTerm !== '' || statusFilter !== 'all' || osFilter !== 'all';
  const clearFilters = () => {
    setSearchTerm('');
    setStatusFilter('all');
    setOsFilter('all');
  };

  // ----------------------------------------------------------
  // Style
  // ----------------------------------------------------------
  const card = `rounded-2xl backdrop-blur-xl border transition-colors duration-500 ${
    isDark
      ? 'bg-[#081226]/80 border-blue-500/20 text-white shadow-[0_0_32px_-10px_rgba(37,99,235,0.45)]'
      : 'bg-white border-slate-200 text-slate-900 shadow-sm'
  }`;
  const muted = isDark ? 'text-slate-400' : 'text-slate-500';
  const subtle = isDark ? 'text-slate-300' : 'text-slate-600';
  const fieldClass = `rounded-xl border px-3 py-2 text-xs font-medium focus:outline-none focus:ring-1 focus:ring-cyan-400 ${
    isDark ? 'bg-[#050c1c] border-blue-500/25 text-slate-200' : 'bg-slate-50 border-slate-300 text-slate-700'
  }`;
  const iconButton = `p-1.5 rounded-lg border transition-colors disabled:opacity-40 disabled:cursor-not-allowed ${
    isDark
      ? 'bg-blue-600/20 border-blue-500/40 text-cyan-300 hover:bg-blue-600/40'
      : 'bg-blue-50 border-blue-200 text-blue-600 hover:bg-blue-100'
  }`;
  const dangerIconButton = `p-1.5 rounded-lg border transition-colors disabled:opacity-40 disabled:cursor-not-allowed ${
    isDark
      ? 'bg-red-600/15 border-red-500/40 text-red-300 hover:bg-red-600/35'
      : 'bg-red-50 border-red-200 text-red-600 hover:bg-red-100'
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
      label: 'Tổng số máy',
      value: counts.total,
      icon: <Monitor className="w-7 h-7" />,
      color: '#3b82f6',
      iconBg: 'from-blue-500 to-blue-700 shadow-[0_0_26px_rgba(59,130,246,0.65)]',
    },
    {
      key: 'online' as const,
      label: 'Agent hoạt động',
      value: counts.online,
      icon: <CheckCircle2 className="w-7 h-7" />,
      color: '#22c55e',
      iconBg: 'from-emerald-500 to-emerald-700 shadow-[0_0_26px_rgba(34,197,94,0.6)]',
    },
    {
      key: 'pending' as const,
      label: 'Đang chờ duyệt',
      value: counts.pending,
      icon: <Clock className="w-7 h-7" />,
      color: '#f59e0b',
      iconBg: 'from-amber-500 to-orange-600 shadow-[0_0_26px_rgba(245,158,11,0.6)]',
    },
    {
      key: 'offline' as const,
      label: 'Ngoại tuyến',
      value: counts.offline,
      icon: <ShieldAlert className="w-7 h-7" />,
      color: '#ef4444',
      iconBg: 'from-rose-500 to-red-700 shadow-[0_0_26px_rgba(239,68,68,0.6)]',
    },
  ];

  // ----------------------------------------------------------
  // Giao diện
  // ----------------------------------------------------------
  return (
    <div className="space-y-5 pb-12 relative">

      {/* Hình Tux trang trí (đặt file /public/tux.png) */}
      <div className="hidden xl:block absolute -top-6 right-56 w-36 h-36 pointer-events-none select-none opacity-90">
        <TuxImage className="w-full h-full object-contain drop-shadow-[0_0_28px_rgba(56,189,248,0.55)]" fallback={null} />
      </div>

      {/* 1. BREADCRUMB + TIÊU ĐỀ */}
      <div className="space-y-3">
        <div className={`flex items-center gap-2 text-xs ${muted}`}>
          <LayoutDashboard className="w-3.5 h-3.5" />
          <ChevronRight className="w-3 h-3" />
          <span>Agents</span>
          <ChevronRight className="w-3 h-3" />
          <span className={isDark ? 'text-slate-200' : 'text-slate-700'}>Quản lý Agent</span>
        </div>

        <div className="flex flex-col lg:flex-row justify-between items-start lg:items-center gap-4">
          <div className="flex items-center gap-4">
            <div className="p-3 rounded-2xl bg-gradient-to-br from-blue-500/30 to-cyan-400/10 border border-cyan-400/30 shadow-[0_0_24px_rgba(34,211,238,0.3)]">
              <Server className="w-8 h-8 text-cyan-400" />
            </div>
            <div>
              <h1 className="text-2xl sm:text-3xl font-black tracking-tight">Quản lý Agent</h1>
              <p className={`text-xs sm:text-sm mt-1 ${muted}`}>
                Theo dõi và quản lý các Agent đang hoạt động trên các máy tính Linux
                {updatedAt && ` · Cập nhật lúc ${updatedAt.toLocaleTimeString('vi-VN')}`}
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
              onClick={() => setShowInstall(true)}
              className="px-5 py-2.5 rounded-xl bg-gradient-to-r from-blue-600 to-cyan-500 text-white text-xs font-bold shadow-[0_0_24px_rgba(34,211,238,0.45)] hover:from-blue-500 hover:to-cyan-400 transition-all flex items-center gap-2"
            >
              <Download className="w-4 h-4" />
              Cài đặt Agent
            </button>
          </div>
        </div>
      </div>

      {/* Thông báo thành công / lỗi */}
      {notice && (
        <div
          role="status"
          className="flex items-start gap-3 p-4 rounded-2xl border bg-emerald-500/10 border-emerald-500/30 text-emerald-400 text-sm"
        >
          <CheckCircle2 className="w-5 h-5 shrink-0 mt-0.5" />
          <p className="flex-1">{notice}</p>
          <button onClick={() => setNotice(null)} aria-label="Đóng"><X className="w-4 h-4" /></button>
        </div>
      )}
      {loadError && (
        <div className="flex items-start gap-3 p-4 rounded-2xl border bg-red-500/10 border-red-500/30 text-red-400 text-sm">
          <ShieldAlert className="w-5 h-5 shrink-0 mt-0.5" />
          <div className="flex-1">
            <p className="font-semibold">Không thể tải danh sách Agent</p>
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
        {statCards.map((item) => {
          const d = delta(item.key);
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
                  <p className={`text-xs mt-1.5 font-medium ${subtle}`}>{item.label}</p>
                </div>
                <div className="flex flex-col items-end gap-1">
                  <span className="text-[11px] font-bold font-mono" style={{ color: item.color }}>
                    {formatDelta(d)}
                  </span>
                  <Sparkline values={series.map((s) => s[item.key])} color={item.color} />
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {/* 3. BA BIỂU ĐỒ */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-5">

        {/* Trạng thái Agent */}
        <div className={`${card} lg:col-span-4 p-5`}>
          <PanelTitle icon={<Activity className="w-4 h-4" />} title="Thống kê trạng thái Agent" />
          <div className="flex items-center gap-4">
            <div className="relative w-40 h-40 shrink-0">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={rows.length === 0 ? [{ name: 'Chưa có dữ liệu', value: 1, color: '#334155' }] : statusData}
                    dataKey="value"
                    innerRadius="68%"
                    outerRadius="95%"
                    paddingAngle={rows.length > 0 ? 3 : 0}
                    startAngle={90}
                    endAngle={-270}
                    stroke="none"
                  >
                    {(rows.length === 0 ? [{ name: 'x', color: '#334155' }] : statusData).map((d) => (
                      <Cell key={d.name} fill={d.color} />
                    ))}
                  </Pie>
                  {rows.length > 0 && <Tooltip contentStyle={tooltipStyle} />}
                </PieChart>
              </ResponsiveContainer>
              <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
                <span className="text-2xl font-black">{percent(counts.online)}%</span>
                <span className={`text-[10px] ${muted}`}>Đang hoạt động</span>
              </div>
            </div>

            <div className="flex-1 space-y-3 text-xs">
              {statusData.map((item) => (
                <div key={item.name} className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <span className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: item.color }} />
                    <span className={subtle}>{item.name}</span>
                  </div>
                  <span className="font-mono font-bold">
                    {item.value} <span className={`font-normal ${muted}`}>{percent(item.value)}%</span>
                  </span>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Agent 7 ngày qua */}
        <div className={`${card} lg:col-span-5 p-5`}>
          <PanelTitle
            icon={<Activity className="w-4 h-4" />}
            title="Agent 7 ngày qua"
            right={
              <div className="flex items-center gap-4 text-[11px]">
                <span className="flex items-center gap-1.5"><span className="w-2.5 h-2.5 rounded-full border-2 border-cyan-400" /><span className={subtle}>Tổng Agent</span></span>
                <span className="flex items-center gap-1.5"><span className="w-2.5 h-2.5 rounded-full border-2 border-violet-400" /><span className={subtle}>Đăng ký mới</span></span>
              </div>
            }
          />
          <div className="h-44 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={weekData} margin={{ top: 5, right: 10, left: -24, bottom: 0 }}>
                <defs>
                  <linearGradient id="agentsTotalFill" x1="0" y1="0" x2="0" y2="1">
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
                  dataKey="total"
                  name="Tổng Agent"
                  stroke="#22d3ee"
                  strokeWidth={2}
                  fill="url(#agentsTotalFill)"
                  dot={{ r: 3, fill: '#0b1527', strokeWidth: 2 }}
                  isAnimationActive={false}
                />
                <Area
                  type="monotone"
                  dataKey="added"
                  name="Đăng ký mới"
                  stroke="#a78bfa"
                  strokeWidth={2}
                  fill="none"
                  dot={{ r: 3, fill: '#0b1527', strokeWidth: 2 }}
                  isAnimationActive={false}
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Hệ điều hành */}
        <div className={`${card} lg:col-span-3 p-5`}>
          <PanelTitle icon={<Server className="w-4 h-4" />} title="Hệ điều hành" />
          <div className="flex flex-col items-center gap-4">
            <div className="relative w-36 h-36">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={osData.length === 0 ? [{ name: 'Chưa có dữ liệu', value: 1, color: '#334155' }] : osData}
                    dataKey="value"
                    innerRadius="62%"
                    outerRadius="95%"
                    paddingAngle={osData.length > 1 ? 3 : 0}
                    stroke="none"
                  >
                    {(osData.length === 0 ? [{ name: 'x', color: '#334155' }] : osData).map((d) => (
                      <Cell key={d.name} fill={d.color} />
                    ))}
                  </Pie>
                  {osData.length > 0 && <Tooltip contentStyle={tooltipStyle} />}
                </PieChart>
              </ResponsiveContainer>
              <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
                <TuxImage
                  className="w-14 h-14 object-contain"
                  fallback={<Server className={`w-8 h-8 ${muted}`} />}
                />
              </div>
            </div>

            <div className="w-full space-y-2 text-xs">
              {osData.length === 0 ? (
                <p className={`text-center ${muted}`}>Chưa có dữ liệu</p>
              ) : (
                osData.map((item) => (
                  <div key={item.name} className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <span className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: item.color }} />
                      <span className={subtle}>{item.name}</span>
                    </div>
                    <span className="font-mono font-bold">{percent(item.value)}%</span>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>
      </div>

      {/* 4. DANH SÁCH AGENTS + HOẠT ĐỘNG GẦN ĐÂY */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-5">

        {/* Bảng Agents */}
        <div className={`${card} lg:col-span-8 p-5`}>
          <PanelTitle
            icon={<Monitor className="w-4 h-4" />}
            title="Danh sách Agents"
            right={
              <div className="flex flex-wrap items-center justify-end gap-2">
                <select
                  value={statusFilter}
                  onChange={(e) => setStatusFilter(e.target.value as 'all' | AgentStatus)}
                  className={fieldClass}
                >
                  <option value="all">Tất cả trạng thái</option>
                  <option value="ONLINE">Online</option>
                  <option value="PENDING_APPROVAL">Chờ duyệt</option>
                  <option value="OFFLINE">Ngoại tuyến</option>
                  <option value="DISABLED">Đã thu hồi</option>
                  <option value="REJECTED">Đã từ chối</option>
                </select>

                <select
                  value={osFilter}
                  onChange={(e) => setOsFilter(e.target.value as 'all' | OsFamily)}
                  className={fieldClass}
                >
                  <option value="all">Tất cả OS</option>
                  {osOptions.map((os) => (
                    <option key={os} value={os}>{os}</option>
                  ))}
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
                  <th className="p-3">Tên Agent</th>
                  <th className="p-3">Máy tính</th>
                  <th className="p-3">Hệ điều hành</th>
                  <th className="p-3">IP Address</th>
                  <th className="p-3">Trạng thái</th>
                  <th className="p-3">Cập nhật cuối</th>
                  <th className="p-3 rounded-r-xl text-right">Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {isLoading ? (
                  <tr>
                    <td colSpan={8} className={`text-center py-10 ${muted}`}>
                      <Loader2 className="w-5 h-5 animate-spin inline mr-2 text-cyan-400" />
                      Đang tải danh sách Agent...
                    </td>
                  </tr>
                ) : pageRows.length === 0 ? (
                  <tr>
                    <td colSpan={8} className={`text-center py-10 ${muted}`}>
                      {rows.length === 0
                        ? 'Chưa có Agent nào đăng ký với hệ thống.'
                        : 'Không tìm thấy Agent phù hợp.'}
                    </td>
                  </tr>
                ) : (
                  pageRows.map((agent, idx) => {
                    const pill = STATUS_PILL[agent.status];
                    const os = OS_STYLE[agent.osFamily];
                    const isBusy = busyAgentId === agent.agentId;
                    const isPending = agent.status === 'PENDING_APPROVAL';

                    return (
                      <tr
                        key={agent.agentId}
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
                        <td className="p-3 font-bold">{agent.agentId}</td>
                        <td className={`p-3 ${subtle}`}>{agent.computer}</td>
                        <td className="p-3">
                          <span className="inline-flex items-center gap-2">
                            <span
                              className="w-6 h-6 rounded-full flex items-center justify-center text-[11px] font-bold text-white shrink-0"
                              style={{ backgroundColor: os.color }}
                            >
                              {os.letter}
                            </span>
                            <span className={subtle}>{agent.os}</span>
                          </span>
                        </td>
                        <td className={`p-3 font-mono ${muted}`}>{agent.ip}</td>
                        <td className="p-3">
                          <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[10px] font-semibold border ${pill.className}`}>
                            <span className={`w-1.5 h-1.5 rounded-full ${pill.dot} ${agent.status === 'ONLINE' ? 'animate-pulse' : ''}`} />
                            {pill.label}
                          </span>
                        </td>
                        <td className={`p-3 font-mono text-[11px] ${muted}`}>{formatDateTime(agent.lastUpdate)}</td>
                        <td className="p-3">
                          <div className="flex items-center justify-end gap-1.5">
                            {isBusy ? (
                              <Loader2 className="w-4 h-4 animate-spin text-cyan-400" />
                            ) : (
                              <>
                                <button
                                  className={iconButton}
                                  title="Xem chi tiết máy"
                                  disabled={!agent.clientId}
                                  onClick={() => agent.clientId && navigate(`/computers/${encodeURIComponent(agent.clientId)}`)}
                                >
                                  <Eye className="w-3.5 h-3.5" />
                                </button>

                                {isPending && canManage ? (
                                  <>
                                    <button className={iconButton} title="Duyệt Agent" onClick={() => handleApprove(agent)}>
                                      <Check className="w-3.5 h-3.5 text-emerald-400" />
                                    </button>
                                    <button className={iconButton} title="Từ chối Agent" onClick={() => handleReject(agent)}>
                                      <X className="w-3.5 h-3.5 text-red-400" />
                                    </button>
                                  </>
                                ) : (
                                  <button
                                    className={iconButton}
                                    title="Xem giám sát"
                                    disabled={!agent.clientId}
                                    onClick={() => agent.clientId && navigate(`/computers/${encodeURIComponent(agent.clientId)}`)}
                                  >
                                    <Activity className="w-3.5 h-3.5" />
                                  </button>
                                )}

                                {canManage && (agent.status === 'ONLINE' || agent.status === 'OFFLINE') && (
                                  <button
                                    className={dangerIconButton}
                                    title="Thu hồi Agent"
                                    onClick={() => handleRevoke(agent)}
                                  >
                                    <ShieldOff className="w-3.5 h-3.5" />
                                  </button>
                                )}

                                {/* Xóa Agent: áp dụng cho mọi trạng thái */}
                                {canManage && (
                                  <button
                                    className={dangerIconButton}
                                    title="Xóa Agent và dữ liệu liên quan"
                                    aria-label={`Xóa Agent ${agent.agentId}`}
                                    onClick={() => openDelete(agent)}
                                  >
                                    <Trash2 className="w-3.5 h-3.5" />
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
              {filteredRows.length === 0
                ? 'Không có kết quả'
                : `Hiển thị ${pageStart + 1}–${pageStart + pageRows.length} / ${filteredRows.length} Agent`}
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
                {activities.map(({ agent, text, color, time }) => (
                  <li key={agent.agentId} className="relative pl-6 flex items-start justify-between gap-3">
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
                      <p className="text-xs font-bold truncate">Agent {agent.agentId}</p>
                    </div>
                    <p className={`text-[11px] text-right shrink-0 max-w-[45%] ${muted}`}>{text}</p>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      </div>

      {/* MODAL XÁC NHẬN XÓA AGENT */}
      {deleteTarget && (
        <div
          className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm flex items-center justify-center p-4"
          onClick={closeDelete}
        >
          <div
            role="dialog"
            aria-modal="true"
            aria-labelledby="delete-agent-title"
            className={`${card} w-full max-w-lg p-6 space-y-4`}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-start justify-between gap-3">
              <div className="flex items-center gap-3">
                <span className="p-2 rounded-xl bg-red-500/15 border border-red-500/40 text-red-400">
                  <AlertTriangle className="w-5 h-5" />
                </span>
                <h3 id="delete-agent-title" className="text-base font-bold">
                  Xóa Agent &ldquo;{deleteTarget.agentId}&rdquo;
                </h3>
              </div>
              <button onClick={closeDelete} disabled={isDeleting} aria-label="Đóng" className="disabled:opacity-40">
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className={`rounded-xl border p-3 text-xs grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 ${
              isDark ? 'bg-[#050c1c] border-blue-500/20' : 'bg-slate-50 border-slate-200'
            }`}>
              <span className={muted}>Máy tính</span>
              <span className="font-semibold">{deleteTarget.computer}</span>
              <span className={muted}>IP</span>
              <span className="font-mono">{deleteTarget.ip}</span>
              <span className={muted}>Trạng thái</span>
              <span>{STATUS_PILL[deleteTarget.status].label}</span>
            </div>

            <div className={`text-xs space-y-2 ${subtle}`}>
              <p className="font-semibold text-red-400">Hành động này không thể hoàn tác. Hệ thống sẽ xóa:</p>
              <ul className="list-disc pl-5 space-y-1">
                <li>Agent, khóa truy cập và yêu cầu đăng ký</li>
                <li>Máy tính liên kết với Agent này</li>
                <li>Lịch sử giám sát: CPU, RAM, ổ đĩa, mạng, tiến trình</li>
                <li>Thông tin phần cứng, cảnh báo, lệnh từ xa và phiên chia sẻ màn hình</li>
              </ul>
              <p className={muted}>Nhật ký hệ thống được giữ lại nhưng không còn liên kết tới máy này.</p>
            </div>

            {deleteTarget.status === 'ONLINE' && (
              <div className="p-3 rounded-xl border text-xs bg-amber-500/10 border-amber-500/30 text-amber-400">
                Agent đang online. Phiên chia sẻ màn hình đang mở sẽ bị đóng, và Agent trên máy sẽ bị từ chối ở lần kết nối
                tiếp theo, sau đó có thể đăng ký lại như một Agent mới.
              </div>
            )}

            <div className="space-y-1.5">
              <label htmlFor="delete-agent-confirm" className={`text-xs ${subtle}`}>
                Nhập <span className="font-mono font-bold">{deleteTarget.agentId}</span> để xác nhận
              </label>
              <input
                id="delete-agent-confirm"
                type="text"
                autoFocus
                autoComplete="off"
                value={deleteConfirmText}
                onChange={(e) => setDeleteConfirmText(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') void handleDelete();
                }}
                disabled={isDeleting}
                className={`${fieldClass} w-full font-mono`}
              />
            </div>

            {deleteError && (
              <div role="alert" className="flex items-start gap-2 p-3 rounded-xl border text-xs bg-red-500/10 border-red-500/30 text-red-400">
                <ShieldAlert className="w-4 h-4 shrink-0 mt-0.5" />
                <p>{deleteError}</p>
              </div>
            )}

            <div className="flex justify-end gap-2 pt-1">
              <button
                onClick={closeDelete}
                disabled={isDeleting}
                className={`px-4 py-2 rounded-xl border text-xs font-bold disabled:opacity-50 ${
                  isDark
                    ? 'border-blue-500/30 text-slate-200 hover:bg-blue-600/20'
                    : 'border-slate-300 text-slate-700 hover:bg-slate-100'
                }`}
              >
                Hủy
              </button>
              <button
                onClick={() => void handleDelete()}
                disabled={!deleteConfirmed || isDeleting}
                className="px-4 py-2 rounded-xl bg-gradient-to-r from-red-600 to-rose-500 text-white text-xs font-bold flex items-center gap-2 disabled:opacity-40 disabled:cursor-not-allowed hover:from-red-500 hover:to-rose-400 transition-all"
              >
                {isDeleting ? <Loader2 className="w-4 h-4 animate-spin" /> : <Trash2 className="w-4 h-4" />}
                {isDeleting ? 'Đang xóa...' : 'Xóa vĩnh viễn'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* MODAL HƯỚNG DẪN CÀI ĐẶT AGENT */}
      {showInstall && (
        <div
          className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm flex items-center justify-center p-4"
          onClick={() => setShowInstall(false)}
        >
          <div
            className={`${card} w-full max-w-xl p-6 space-y-4`}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2.5">
                <Terminal className="w-5 h-5 text-cyan-400" />
                <h3 className="text-base font-bold">Cài đặt Agent trên máy cần giám sát</h3>
              </div>
              <button onClick={() => setShowInstall(false)} aria-label="Đóng"><X className="w-5 h-5" /></button>
            </div>

            <ol className={`text-xs space-y-3 list-decimal pl-5 ${subtle}`}>
              <li>
                Build Agent (yêu cầu Java 21 và Maven):
                <pre className={`mt-1.5 p-3 rounded-xl font-mono text-[11px] overflow-x-auto ${isDark ? 'bg-[#050c1c] text-cyan-300' : 'bg-slate-100 text-slate-800'}`}>
{`cd agent
./mvnw clean package`}
                </pre>
              </li>
              <li>
                Chạy Agent và trỏ về địa chỉ máy chủ:
                <pre className={`mt-1.5 p-3 rounded-xl font-mono text-[11px] overflow-x-auto ${isDark ? 'bg-[#050c1c] text-cyan-300' : 'bg-slate-100 text-slate-800'}`}>
{`AGENT_SERVER_URL=http://<IP-may-chu>:8080 \\
java -jar target/agent-0.0.1-SNAPSHOT.jar`}
                </pre>
              </li>
              <li>
                Agent tự gửi yêu cầu đăng ký. Quay lại trang này, lọc trạng thái
                {' '}<b>Chờ duyệt</b> rồi bấm <b>Duyệt</b>.
              </li>
              <li>Sau khi được duyệt, Agent nhận khóa truy cập và bắt đầu gửi dữ liệu giám sát.</li>
            </ol>

            <div className="flex justify-end">
              <button
                onClick={() => setShowInstall(false)}
                className="px-4 py-2 rounded-xl bg-gradient-to-r from-blue-600 to-cyan-500 text-white text-xs font-bold"
              >
                Đã hiểu
              </button>
            </div>
          </div>
        </div>
      )}

    </div>
  );
}