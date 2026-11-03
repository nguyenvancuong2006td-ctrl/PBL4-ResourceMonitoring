
import React, {
  useCallback,
  useEffect,
  useMemo,
  useState,
} from 'react';

import {
  Activity,
  AlertCircle,
  AlertTriangle,
  CheckCircle2,
  Clock3,
  Command,
  Cpu,
  History,
  Loader2,
  Monitor,
  Moon,
  Power,
  RefreshCw,
  Search,
  Server,
  Shield,
  ShieldAlert,
  Sun,
  Terminal,
  Trash2,
  X,
  XCircle,
  MemoryStick,
  ListFilter,
} from 'lucide-react';

import { useTheme } from '../../context/ThemeContext';

import {
  getClients,
  getLatestMetrics,
  getRemoteCommands,
  createRemoteCommand,
  toDate,
} from '../../lib/api';

import type {
  ClientInfo,
  LatestMetrics,
  ProcessMetric,
  RemoteCommandInfo,
  RemoteCommandStatus,
  RemoteCommandType,
} from '../../lib/api';

// ======================================================
// TYPES
// ======================================================

type CommandAction = {
  type: RemoteCommandType;
  title: string;
  description: string;
  icon: React.ElementType;
  danger: boolean;
};

type ProcessSortField = 'cpuUsage' | 'memoryUsage' | 'name';

const COMMAND_ACTIONS: CommandAction[] = [
  {
    type: 'RESTART',
    title: 'Khởi động lại máy',
    description: 'Yêu cầu Agent khởi động lại hệ điều hành Client.',
    icon: RefreshCw,
    danger: true,
  },
  {
    type: 'SHUTDOWN',
    title: 'Tắt máy tính',
    description: 'Yêu cầu Agent tắt hệ điều hành Client.',
    icon: Power,
    danger: true,
  },
  {
    type: 'KILL_PROCESS',
    title: 'Kết thúc tiến trình',
    description: 'Chọn một tiến trình đang chạy từ danh sách của Client.',
    icon: Trash2,
    danger: true,
  },
];

const STATUS_CONFIG: Record<
  RemoteCommandStatus,
  { label: string; color: string; lightColor: string }
> = {
  PENDING: {
    label: 'Đang chờ',
    color: 'bg-amber-500/10 text-amber-400 border-amber-500/20',
    lightColor: 'bg-amber-50 text-amber-700 border-amber-200',
  },
  SENT: {
    label: 'Đã gửi',
    color: 'bg-blue-500/10 text-blue-400 border-blue-500/20',
    lightColor: 'bg-blue-50 text-blue-700 border-blue-200',
  },
  RUNNING: {
    label: 'Đang thực thi',
    color: 'bg-violet-500/10 text-violet-400 border-violet-500/20',
    lightColor: 'bg-violet-50 text-violet-700 border-violet-200',
  },
  COMPLETED: {
    label: 'Thành công',
    color: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20',
    lightColor: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  },
  FAILED: {
    label: 'Thất bại',
    color: 'bg-rose-500/10 text-rose-400 border-rose-500/20',
    lightColor: 'bg-rose-50 text-rose-700 border-rose-200',
  },
  TIMEOUT: {
    label: 'Quá thời gian',
    color: 'bg-orange-500/10 text-orange-400 border-orange-500/20',
    lightColor: 'bg-orange-50 text-orange-700 border-orange-200',
  },
};

// ======================================================
// FORMAT HELPERS
// ======================================================

function commandLabel(type: RemoteCommandType | string): string {
  switch (type) {
    case 'RESTART':
      return 'Khởi động lại';
    case 'SHUTDOWN':
      return 'Tắt máy';
    case 'KILL_PROCESS':
      return 'Kết thúc tiến trình';
    default:
      return type;
  }
}

function formatDate(
  value: string | number[] | null | undefined
): string {
  const date = toDate(value);

  if (!date) return '—';

  return date.toLocaleString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
}

function formatNumber(value: number | null | undefined): string {
  if (value == null || !Number.isFinite(value)) return '—';

  return value.toFixed(1);
}

function processStatusLabel(status: string | null | undefined): string {
  if (!status) return 'Không rõ';

  const normalized = status.toUpperCase();

  const labels: Record<string, string> = {
    RUNNING: 'Đang chạy',
    SLEEPING: 'Đang chờ',
    WAITING: 'Đang chờ',
    BLOCKED: 'Bị chặn',
    STOPPED: 'Đã dừng',
    ZOMBIE: 'Zombie',
    IDLE: 'Nhàn rỗi',
  };

  return labels[normalized] || status;
}

function processStatusColor(
  status: string | null | undefined,
  isDark: boolean
): string {
  const normalized = status?.toUpperCase();

  if (normalized === 'RUNNING') {
    return isDark
      ? 'bg-emerald-500/10 text-emerald-400'
      : 'bg-emerald-50 text-emerald-700';
  }

  if (normalized === 'STOPPED' || normalized === 'ZOMBIE') {
    return isDark
      ? 'bg-rose-500/10 text-rose-400'
      : 'bg-rose-50 text-rose-700';
  }

  return isDark
    ? 'bg-slate-700/60 text-slate-300'
    : 'bg-slate-100 text-slate-600';
}

// ======================================================
// COMPONENT
// ======================================================

export default function RemoteCommandsPage() {
  const { isDark, toggleTheme } = useTheme();

  // --------------------------------------------------
  // DATA
  // --------------------------------------------------

  const [clients, setClients] = useState<ClientInfo[]>([]);
  const [commands, setCommands] = useState<RemoteCommandInfo[]>([]);

  const [processes, setProcesses] = useState<ProcessMetric[]>([]);
  const [processCollectedAt, setProcessCollectedAt] =
    useState<string | number[] | null>(null);

  // --------------------------------------------------
  // SELECTION
  // --------------------------------------------------

  const [selectedClientId, setSelectedClientId] = useState('');

  const [selectedCommand, setSelectedCommand] =
    useState<CommandAction | null>(null);

  const [selectedProcessPid, setSelectedProcessPid] =
    useState<number | null>(null);

  const [selectedDetail, setSelectedDetail] =
    useState<RemoteCommandInfo | null>(null);

  // --------------------------------------------------
  // FILTERS
  // --------------------------------------------------

  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [typeFilter, setTypeFilter] = useState('ALL');

  const [processSearch, setProcessSearch] = useState('');
  const [processSort, setProcessSort] =
    useState<ProcessSortField>('cpuUsage');

  const [showAllProcesses, setShowAllProcesses] = useState(false);

  // --------------------------------------------------
  // LOADING
  // --------------------------------------------------

  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [sending, setSending] = useState(false);

  const [processLoading, setProcessLoading] = useState(false);
  const [processRefreshing, setProcessRefreshing] = useState(false);

  // --------------------------------------------------
  // MESSAGES
  // --------------------------------------------------

  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [processError, setProcessError] = useState('');

  // --------------------------------------------------
  // THEME
  // --------------------------------------------------

  const theme = {
    page: isDark
      ? 'bg-[#070D19] text-slate-100'
      : 'bg-slate-50 text-slate-900',

    card: isDark
      ? 'bg-[#0D1627] border-slate-800'
      : 'bg-white border-slate-200',

    cardHover: isDark
      ? 'hover:bg-slate-800/60 hover:border-slate-700'
      : 'hover:bg-slate-50 hover:border-slate-300',

    input: isDark
      ? 'bg-[#080F1D] border-slate-700 text-slate-200 placeholder:text-slate-500'
      : 'bg-white border-slate-300 text-slate-800 placeholder:text-slate-400',

    muted: isDark ? 'text-slate-400' : 'text-slate-500',

    heading: isDark ? 'text-white' : 'text-slate-900',

    divider: isDark ? 'border-slate-800' : 'border-slate-200',

    tableHead: isDark
      ? 'bg-slate-950/70 text-slate-400'
      : 'bg-slate-50 text-slate-500',

    terminal: isDark
      ? 'bg-[#050A13] border-slate-800'
      : 'bg-slate-950 border-slate-800',

    selectedRow: isDark
      ? 'bg-cyan-500/10 border-l-2 border-l-cyan-500'
      : 'bg-cyan-50 border-l-2 border-l-cyan-500',
  };

  // --------------------------------------------------
  // DERIVED DATA
  // --------------------------------------------------

  const selectedClient = useMemo(
    () => clients.find(
      (client) => client.clientId === selectedClientId
    ),
    [clients, selectedClientId]
  );

  const selectedProcess = useMemo(
    () => processes.find(
      (process) => process.pid === selectedProcessPid
    ) ?? null,
    [processes, selectedProcessPid]
  );

  const onlineClients = useMemo(
    () => clients.filter(
      (client) => client.status === 'ONLINE'
    ),
    [clients]
  );

  const filteredProcesses = useMemo(() => {
    const keyword = processSearch.trim().toLowerCase();

    const result = processes.filter((process) => {
      const matchesSearch =
        !keyword ||
        process.name?.toLowerCase().includes(keyword) ||
        String(process.pid).includes(keyword);

      return matchesSearch;
    });

    return [...result].sort((a, b) => {
      if (processSort === 'name') {
        return (a.name || '').localeCompare(b.name || '');
      }

      return (b[processSort] ?? 0) - (a[processSort] ?? 0);
    });
  }, [processes, processSearch, processSort]);

  const displayedProcesses = useMemo(
    () => showAllProcesses
      ? filteredProcesses
      : filteredProcesses.slice(0, 100),
    [filteredProcesses, showAllProcesses]
  );

  const filteredCommands = useMemo(() => {
    return commands.filter((command) => {
      const matchesSearch =
        !search ||
        command.clientId?.toLowerCase().includes(search.toLowerCase()) ||
        String(command.commandId).includes(search) ||
        command.requestedBy?.toLowerCase().includes(search.toLowerCase());

      const matchesStatus =
        statusFilter === 'ALL' ||
        command.status === statusFilter;

      const matchesType =
        typeFilter === 'ALL' ||
        command.commandType === typeFilter;

      return matchesSearch && matchesStatus && matchesType;
    });
  }, [commands, search, statusFilter, typeFilter]);

  const stats = useMemo(() => ({
    total: commands.length,

    pending: commands.filter(
      (item) =>
        item.status === 'PENDING' ||
        item.status === 'SENT'
    ).length,

    running: commands.filter(
      (item) => item.status === 'RUNNING'
    ).length,

    completed: commands.filter(
      (item) => item.status === 'COMPLETED'
    ).length,

    failed: commands.filter(
      (item) =>
        item.status === 'FAILED' ||
        item.status === 'TIMEOUT'
    ).length,
  }), [commands]);

  // --------------------------------------------------
  // LOAD CLIENTS
  // --------------------------------------------------

  const loadClients = useCallback(async () => {
    const data = await getClients();
    setClients(data);
  }, []);

  // --------------------------------------------------
  // LOAD COMMAND HISTORY
  // --------------------------------------------------

  const loadCommands = useCallback(async (showLoading = false) => {
    if (showLoading) setRefreshing(true);

    try {
      const data = await getRemoteCommands({
        page: 0,
        size: 50,
      });

      setCommands(data.content ?? []);
      setError('');
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : 'Không thể tải lịch sử lệnh.'
      );
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  // --------------------------------------------------
  // LOAD PROCESS LIST
  // --------------------------------------------------

  const loadProcesses = useCallback(async (manual = false) => {
    if (!selectedClientId) {
      setProcesses([]);
      setSelectedProcessPid(null);
      setProcessCollectedAt(null);
      return;
    }

    if (manual) {
      setProcessRefreshing(true);
    } else {
      setProcessLoading(true);
    }

    try {
      const metrics: LatestMetrics | null =
        await getLatestMetrics(selectedClientId);

      const latestProcesses = metrics?.processes ?? [];

      setProcesses(latestProcesses);
      setProcessCollectedAt(metrics?.collectedAt ?? null);
      setProcessError('');

      // Nếu tiến trình đã biến mất khỏi snapshot mới,
      // bỏ chọn để tránh gửi lệnh đến PID không còn tồn tại.
      setSelectedProcessPid((currentPid) => {
        if (currentPid == null) return null;

        const stillExists = latestProcesses.some(
          (process) => process.pid === currentPid
        );

        return stillExists ? currentPid : null;
      });
    } catch (err) {
      setProcesses([]);
      setSelectedProcessPid(null);

      setProcessError(
        err instanceof Error
          ? err.message
          : 'Không thể tải danh sách tiến trình.'
      );
    } finally {
      setProcessLoading(false);
      setProcessRefreshing(false);
    }
  }, [selectedClientId]);

  // --------------------------------------------------
  // INITIAL LOAD + COMMAND POLLING
  // --------------------------------------------------

  const loadData = useCallback(async () => {
    try {
      await Promise.all([
        loadClients(),
        loadCommands(),
      ]);
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : 'Không thể tải dữ liệu Remote Commands.'
      );

      setLoading(false);
    }
  }, [loadClients, loadCommands]);

  useEffect(() => {
    void loadData();

    const timer = window.setInterval(() => {
      void loadCommands();
    }, 5000);

    return () => window.clearInterval(timer);
  }, [loadData, loadCommands]);

  // --------------------------------------------------
  // PROCESS POLLING
  // --------------------------------------------------

  useEffect(() => {
    if (!selectedClientId) {
      setProcesses([]);
      setSelectedProcessPid(null);
      setProcessCollectedAt(null);
      setProcessError('');
      return;
    }

    // Tải ngay khi đổi Client.
    void loadProcesses();

    // Cập nhật danh sách tiến trình định kỳ.
    const timer = window.setInterval(() => {
      if (!document.hidden) {
        void loadProcesses();
      }
    }, 5000);

    return () => window.clearInterval(timer);
  }, [selectedClientId, loadProcesses]);

  // --------------------------------------------------
  // COMMAND EXECUTION
  // --------------------------------------------------

  const executeCommand = async () => {
    if (!selectedClientId || !selectedCommand) {
      setError('Vui lòng chọn máy Client và loại lệnh.');
      return;
    }

    if (!selectedClient || selectedClient.status !== 'ONLINE') {
      setError('Máy Client hiện không ONLINE. Không thể gửi lệnh.');
      return;
    }

    let parameters: { pid: number } | undefined;

    if (selectedCommand.type === 'KILL_PROCESS') {
      if (!selectedProcess) {
        setError('Vui lòng chọn một tiến trình trong danh sách.');
        return;
      }

      // Kiểm tra tiến trình vẫn tồn tại trong snapshot hiện tại.
      const processExists = processes.some(
        (process) => process.pid === selectedProcess.pid
      );

      if (!processExists) {
        setError(
          'Tiến trình không còn trong danh sách. Vui lòng tải lại danh sách.'
        );
        setSelectedProcessPid(null);
        return;
      }

      parameters = {
        pid: selectedProcess.pid,
      };
    }

    setSending(true);
    setError('');
    setSuccess('');

    try {
      const response = await createRemoteCommand(
        selectedClientId,
        selectedCommand.type,
        parameters
      );

      setSuccess(
        `Đã tạo lệnh #${response.commandId}. Đang chờ Agent xử lý.`
      );

      setSelectedCommand(null);
      setSelectedProcessPid(null);

      await Promise.all([
        loadCommands(),
        selectedCommand.type === 'KILL_PROCESS'
          ? loadProcesses(true)
          : Promise.resolve(),
      ]);
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : 'Không thể gửi lệnh đến máy Client.'
      );
    } finally {
      setSending(false);
    }
  };

  // --------------------------------------------------
  // STATISTICS CARDS
  // --------------------------------------------------

  const statCards = [
    {
      title: 'Tổng lệnh',
      value: stats.total,
      icon: Command,
      color: 'text-cyan-500',
      bg: 'bg-cyan-500/10',
    },
    {
      title: 'Đang chờ',
      value: stats.pending,
      icon: Clock3,
      color: 'text-amber-500',
      bg: 'bg-amber-500/10',
    },
    {
      title: 'Đang thực thi',
      value: stats.running,
      icon: Activity,
      color: 'text-violet-500',
      bg: 'bg-violet-500/10',
    },
    {
      title: 'Thành công',
      value: stats.completed,
      icon: CheckCircle2,
      color: 'text-emerald-500',
      bg: 'bg-emerald-500/10',
    },
    {
      title: 'Thất bại',
      value: stats.failed,
      icon: XCircle,
      color: 'text-rose-500',
      bg: 'bg-rose-500/10',
    },
  ];

  // ======================================================
  // RENDER
  // ======================================================

  return (
    <div
      className={`min-h-screen p-4 md:p-6 xl:p-8 transition-colors duration-200 ${theme.page}`}
    >
      {/* =================================================
          HEADER
      ================================================= */}

      <div className="flex flex-col xl:flex-row xl:items-center justify-between gap-5 mb-7">
        <div>
          <div className="flex items-center gap-2 mb-2">
            <div className="p-2 rounded-xl bg-cyan-500/10 text-cyan-500">
              <Terminal size={22} />
            </div>

            <span className="text-xs font-bold tracking-[0.2em] text-cyan-500 uppercase">
              System Administration
            </span>
          </div>

          <h1 className={`text-2xl md:text-3xl font-bold ${theme.heading}`}>
            Remote Commands
          </h1>

          <p className={`mt-2 text-sm ${theme.muted}`}>
            Quản trị máy Client, theo dõi tiến trình và thực hiện lệnh từ xa.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <div
            className={`flex items-center gap-2 px-4 py-2.5 rounded-xl border ${theme.card}`}
          >
            <span className="relative flex h-2.5 w-2.5">
              <span className="absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-60 animate-ping" />
              <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-emerald-500" />
            </span>

            <span className="text-sm font-medium">
              {onlineClients.length} máy trực tuyến
            </span>
          </div>

          <button
            onClick={() => toggleTheme(isDark ? 'light' : 'dark')}
            className={`p-2.5 rounded-xl border transition-colors ${theme.card} ${theme.cardHover}`}
            title={isDark ? 'Chuyển sang giao diện sáng' : 'Chuyển sang giao diện tối'}
          >
            {isDark
              ? <Sun size={19} className="text-amber-400" />
              : <Moon size={19} className="text-slate-600" />
            }
          </button>

          <button
            onClick={() => void loadCommands(true)}
            disabled={refreshing}
            className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white text-sm font-semibold transition-colors disabled:opacity-50"
          >
            <RefreshCw
              size={16}
              className={refreshing ? 'animate-spin' : ''}
            />
            Làm mới
          </button>
        </div>
      </div>

      {/* =================================================
          GLOBAL NOTIFICATIONS
      ================================================= */}

      {error && (
        <div className="flex items-start gap-3 p-4 mb-5 rounded-xl border border-rose-500/30 bg-rose-500/10 text-rose-500">
          <AlertCircle size={19} className="mt-0.5 shrink-0" />

          <div className="flex-1 text-sm">{error}</div>

          <button onClick={() => setError('')}>
            <X size={17} />
          </button>
        </div>
      )}

      {success && (
        <div className="flex items-start gap-3 p-4 mb-5 rounded-xl border border-emerald-500/30 bg-emerald-500/10 text-emerald-500">
          <CheckCircle2 size={19} className="mt-0.5 shrink-0" />

          <div className="flex-1 text-sm">{success}</div>

          <button onClick={() => setSuccess('')}>
            <X size={17} />
          </button>
        </div>
      )}

      {/* =================================================
          STATISTICS
      ================================================= */}

      <div className="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-5 gap-4 mb-7">
        {statCards.map((item) => {
          const Icon = item.icon;

          return (
            <div
              key={item.title}
              className={`rounded-2xl border p-4 md:p-5 transition-colors ${theme.card}`}
            >
              <div className="flex items-center justify-between gap-2">
                <span className={`text-xs md:text-sm ${theme.muted}`}>
                  {item.title}
                </span>

                <div className={`p-2 rounded-xl ${item.bg} ${item.color}`}>
                  <Icon size={18} />
                </div>
              </div>

              <div className={`text-2xl md:text-3xl font-bold mt-3 ${theme.heading}`}>
                {loading ? '—' : item.value}
              </div>
            </div>
          );
        })}
      </div>

      {/* =================================================
          CLIENT SELECTION + COMMAND ACTIONS
      ================================================= */}

      <div className="grid grid-cols-1 xl:grid-cols-3 gap-6 mb-7">
        {/* CLIENT SELECTION */}

        <section className={`xl:col-span-1 rounded-2xl border p-5 ${theme.card}`}>
          <div className="flex items-center gap-2 mb-5">
            <div className="p-2 rounded-lg bg-cyan-500/10 text-cyan-500">
              <Server size={19} />
            </div>

            <div>
              <h2 className={`font-bold ${theme.heading}`}>
                Máy Client mục tiêu
              </h2>

              <p className={`text-xs mt-0.5 ${theme.muted}`}>
                Chọn máy cần quản trị
              </p>
            </div>
          </div>

          <label className={`block text-xs font-semibold mb-2 ${theme.muted}`}>
            DANH SÁCH MÁY TÍNH
          </label>

          <select
            value={selectedClientId}
            onChange={(e) => {
              setSelectedClientId(e.target.value);
              setSelectedProcessPid(null);
              setSelectedCommand(null);
              setProcessSearch('');
              setProcessError('');
              setError('');
              setSuccess('');
            }}
            className={`w-full rounded-xl border px-3 py-3 text-sm outline-none focus:border-cyan-500 ${theme.input}`}
          >
            <option value="">-- Chọn máy Client --</option>

            {clients.map((client) => (
              <option key={client.clientId} value={client.clientId}>
                {client.hostname} - {client.status}
              </option>
            ))}
          </select>

          {selectedClient ? (
            <div
              className={`mt-4 p-4 rounded-xl border ${theme.divider} ${isDark ? 'bg-slate-950/60' : 'bg-slate-50'}`}
            >
              <div className="flex items-center justify-between gap-2 mb-3">
                <div className="flex items-center gap-2 min-w-0">
                  <Monitor size={18} className="text-cyan-500 shrink-0" />

                  <span className={`font-semibold text-sm truncate ${theme.heading}`}>
                    {selectedClient.hostname}
                  </span>
                </div>

                <span
                  className={`text-[10px] font-bold px-2 py-1 rounded-full ${
                    selectedClient.status === 'ONLINE'
                      ? 'bg-emerald-500/10 text-emerald-500'
                      : 'bg-rose-500/10 text-rose-500'
                  }`}
                >
                  {selectedClient.status}
                </span>
              </div>

              <div className={`space-y-2 text-xs ${theme.muted}`}>
                <div className="flex justify-between gap-3">
                  <span>Client ID</span>

                  <span className="font-mono text-right break-all">
                    {selectedClient.clientId}
                  </span>
                </div>

                <div className="flex justify-between gap-3">
                  <span>Địa chỉ IP</span>

                  <span className={theme.heading}>
                    {selectedClient.ipAddress || '—'}
                  </span>
                </div>

                <div className="flex justify-between gap-3">
                  <span>Hệ điều hành</span>

                  <span className={`${theme.heading} text-right`}>
                    {[selectedClient.osName, selectedClient.osVersion]
                      .filter(Boolean)
                      .join(' ') || '—'}
                  </span>
                </div>
              </div>
            </div>
          ) : (
            <div className={`mt-4 p-6 rounded-xl border border-dashed text-center ${theme.divider}`}>
              <Monitor size={28} className={`mx-auto mb-2 ${theme.muted}`} />

              <p className={`text-xs ${theme.muted}`}>
                Chưa chọn máy tính mục tiêu
              </p>
            </div>
          )}

          <div className={`mt-4 flex items-start gap-2 text-xs ${theme.muted}`}>
            <Shield size={15} className="text-cyan-500 shrink-0 mt-0.5" />

            <span>
              Chỉ gửi lệnh khi Client đang ONLINE. Backend vẫn kiểm tra
              quyền và trạng thái Agent trước khi thực thi.
            </span>
          </div>
        </section>

        {/* COMMAND ACTIONS */}

        <section className={`xl:col-span-2 rounded-2xl border p-5 ${theme.card}`}>
          <div className="flex items-center gap-2 mb-5">
            <div className="p-2 rounded-lg bg-violet-500/10 text-violet-500">
              <Command size={19} />
            </div>

            <div>
              <h2 className={`font-bold ${theme.heading}`}>
                Thao tác quản trị
              </h2>

              <p className={`text-xs mt-0.5 ${theme.muted}`}>
                Chọn thao tác cần thực hiện trên máy Client
              </p>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            {COMMAND_ACTIONS.map((action) => {
              const Icon = action.icon;
              const active = selectedCommand?.type === action.type;

              return (
                <button
                  key={action.type}
                  onClick={() => {
                    setSelectedCommand(
                      active ? null : action
                    );

                    setError('');
                    setSuccess('');
                  }}
                  className={`text-left p-4 rounded-xl border transition-all ${
                    active
                      ? 'border-cyan-500 bg-cyan-500/10 ring-1 ring-cyan-500/30'
                      : `${theme.divider} ${theme.cardHover}`
                  }`}
                >
                  <div className="flex items-center justify-between mb-4">
                    <div
                      className={`p-2.5 rounded-xl ${
                        action.type === 'RESTART'
                          ? 'bg-amber-500/10 text-amber-500'
                          : action.type === 'SHUTDOWN'
                            ? 'bg-rose-500/10 text-rose-500'
                            : 'bg-violet-500/10 text-violet-500'
                      }`}
                    >
                      <Icon size={21} />
                    </div>

                    {active && (
                      <CheckCircle2
                        size={18}
                        className="text-cyan-500"
                      />
                    )}
                  </div>

                  <h3 className={`text-sm font-bold ${theme.heading}`}>
                    {action.title}
                  </h3>

                  <p className={`text-xs leading-relaxed mt-2 ${theme.muted}`}>
                    {action.description}
                  </p>

                  <div className="mt-4 text-[10px] font-bold tracking-wider text-rose-500">
                    CẦN XÁC NHẬN
                  </div>
                </button>
              );
            })}
          </div>

          {/* KILL PROCESS SELECTED INFORMATION */}

          {selectedCommand?.type === 'KILL_PROCESS' && (
            <div
              className={`mt-5 p-4 rounded-xl border ${
                selectedProcess
                  ? 'border-rose-500/30 bg-rose-500/5'
                  : theme.divider
              }`}
            >
              <div className="flex items-start gap-3">
                <div className="p-2 rounded-lg bg-rose-500/10 text-rose-500">
                  <Trash2 size={19} />
                </div>

                <div className="flex-1 min-w-0">
                  <h3 className={`text-sm font-bold ${theme.heading}`}>
                    Tiến trình cần kết thúc
                  </h3>

                  <p className={`text-xs mt-1 ${theme.muted}`}>
                    Chọn tiến trình ở danh sách bên dưới. PID sẽ được
                    điền tự động.
                  </p>

                  {selectedProcess ? (
                    <div className="mt-3 grid grid-cols-1 sm:grid-cols-3 gap-3">
                      <div>
                        <p className={`text-[10px] ${theme.muted}`}>
                          TÊN TIẾN TRÌNH
                        </p>

                        <p className={`mt-1 text-sm font-semibold break-all ${theme.heading}`}>
                          {selectedProcess.name}
                        </p>
                      </div>

                      <div>
                        <p className={`text-[10px] ${theme.muted}`}>
                          PROCESS ID
                        </p>

                        <p className="mt-1 text-sm font-mono font-bold text-cyan-500">
                          {selectedProcess.pid}
                        </p>
                      </div>

                      <div>
                        <p className={`text-[10px] ${theme.muted}`}>
                          TRẠNG THÁI
                        </p>

                        <p className={`mt-1 text-sm font-semibold ${theme.heading}`}>
                          {processStatusLabel(selectedProcess.status)}
                        </p>
                      </div>
                    </div>
                  ) : (
                    <div className="mt-3 text-xs text-amber-500">
                      Chưa chọn tiến trình. Hãy chọn một dòng trong bảng
                      tiến trình bên dưới.
                    </div>
                  )}
                </div>
              </div>
            </div>
          )}

          {/* COMMAND CONFIRMATION */}

          {selectedCommand && (
            <div
              className={`mt-5 p-4 rounded-xl border ${
                isDark
                  ? 'border-amber-500/20 bg-amber-500/5'
                  : 'border-amber-200 bg-amber-50'
              }`}
            >
              <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
                <div className="flex items-start gap-3">
                  <AlertTriangle
                    size={19}
                    className="text-amber-500 shrink-0 mt-0.5"
                  />

                  <div>
                    <p className={`text-sm font-bold ${theme.heading}`}>
                      Xác nhận gửi: {selectedCommand.title}
                    </p>

                    <p className={`text-xs mt-1 ${theme.muted}`}>
                      {selectedCommand.type === 'KILL_PROCESS'
                        ? selectedProcess
                          ? `Kết thúc ${selectedProcess.name} (PID ${selectedProcess.pid}) trên ${selectedClient?.hostname || 'Client'}.`
                          : 'Bạn cần chọn tiến trình trước khi gửi lệnh.'
                        : `${selectedClient?.hostname || 'Chưa chọn Client'}. Agent sẽ nhận và thực thi lệnh nếu đủ điều kiện.`
                      }
                    </p>
                  </div>
                </div>

                <div className="flex items-center gap-2 shrink-0">
                  <button
                    onClick={() => {
                      setSelectedCommand(null);
                      setSelectedProcessPid(null);
                    }}
                    disabled={sending}
                    className={`px-4 py-2.5 rounded-lg border text-sm font-medium ${theme.divider} ${theme.cardHover}`}
                  >
                    Hủy
                  </button>

                  <button
                    onClick={() => void executeCommand()}
                    disabled={
                      sending ||
                      !selectedClientId ||
                      selectedClient?.status !== 'ONLINE' ||
                      (
                        selectedCommand.type === 'KILL_PROCESS' &&
                        !selectedProcess
                      )
                    }
                    className="flex items-center gap-2 px-4 py-2.5 rounded-lg bg-rose-600 hover:bg-rose-500 text-white text-sm font-semibold disabled:opacity-40 disabled:cursor-not-allowed"
                  >
                    {sending
                      ? <Loader2 size={16} className="animate-spin" />
                      : <ShieldAlert size={16} />
                    }

                    {sending ? 'Đang gửi...' : 'Xác nhận gửi lệnh'}
                  </button>
                </div>
              </div>
            </div>
          )}
        </section>
      </div>

      {/* =================================================
          RUNNING PROCESS LIST
      ================================================= */}

      <section className={`rounded-2xl border overflow-hidden mb-7 ${theme.card}`}>
        {/* PROCESS HEADER */}

        <div className={`p-5 border-b ${theme.divider}`}>
          <div className="flex flex-col xl:flex-row xl:items-center justify-between gap-4">
            <div className="flex items-center gap-3">
              <div className="p-2 rounded-lg bg-violet-500/10 text-violet-500">
                <Activity size={20} />
              </div>

              <div>
                <h2 className={`font-bold ${theme.heading}`}>
                  Tiến trình đang hoạt động
                </h2>

                <p className={`text-xs mt-1 ${theme.muted}`}>
                  Danh sách tiến trình do Agent thu thập từ máy Client.
                </p>
              </div>
            </div>

            <div className="flex flex-wrap items-center gap-2">
              <div
                className={`flex items-center gap-2 px-3 py-2 rounded-lg border text-xs ${theme.divider}`}
              >
                <Activity size={14} className="text-emerald-500" />

                <span className={theme.muted}>
                  {processes.length} tiến trình
                </span>
              </div>

              <button
                onClick={() => void loadProcesses(true)}
                disabled={!selectedClientId || processRefreshing || processLoading}
                className="flex items-center gap-2 px-3 py-2 rounded-lg bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-semibold disabled:opacity-40"
              >
                <RefreshCw
                  size={14}
                  className={
                    processRefreshing || processLoading
                      ? 'animate-spin'
                      : ''
                  }
                />
                Làm mới tiến trình
              </button>
            </div>
          </div>
        </div>

        {/* PROCESS TOOLBAR */}

        <div className={`p-4 border-b ${theme.divider}`}>
          <div className="flex flex-col md:flex-row gap-3 md:items-center md:justify-between">
            <div className="relative flex-1 max-w-xl">
              <Search
                size={16}
                className={`absolute left-3 top-1/2 -translate-y-1/2 ${theme.muted}`}
              />

              <input
                value={processSearch}
                onChange={(e) => setProcessSearch(e.target.value)}
                disabled={!selectedClientId}
                placeholder="Tìm theo tên tiến trình hoặc PID..."
                className={`w-full pl-9 pr-3 py-2.5 rounded-lg border text-xs outline-none focus:border-cyan-500 disabled:opacity-50 ${theme.input}`}
              />
            </div>

            <div className="flex flex-wrap items-center gap-2">
              <ListFilter size={15} className={theme.muted} />

              <span className={`text-xs ${theme.muted}`}>
                Sắp xếp:
              </span>

              <select
                value={processSort}
                onChange={(e) =>
                  setProcessSort(e.target.value as ProcessSortField)
                }
                disabled={!selectedClientId}
                className={`px-3 py-2.5 rounded-lg border text-xs outline-none ${theme.input}`}
              >
                <option value="cpuUsage">CPU cao nhất</option>
                <option value="memoryUsage">Bộ nhớ cao nhất</option>
                <option value="name">Tên tiến trình</option>
              </select>
            </div>
          </div>
        </div>

        {/* PROCESS ERROR */}

        {processError && (
          <div className="p-4 border-b border-rose-500/20 bg-rose-500/5 text-rose-500 flex items-start gap-2">
            <AlertCircle size={17} className="shrink-0 mt-0.5" />

            <div className="flex-1 text-xs">
              {processError}
            </div>

            <button onClick={() => setProcessError('')}>
              <X size={15} />
            </button>
          </div>
        )}

        {/* PROCESS TABLE */}

        <div className="overflow-x-auto">
          <table className="w-full min-w-[850px] text-left">
            <thead className={theme.tableHead}>
              <tr className="text-[11px] uppercase tracking-wider">
                <th className="px-5 py-4 font-semibold w-12">
                  Chọn
                </th>

                <th className="px-5 py-4 font-semibold">
                  Tiến trình
                </th>

                <th className="px-5 py-4 font-semibold">
                  PID
                </th>

                <th className="px-5 py-4 font-semibold">
                  CPU
                </th>

                <th className="px-5 py-4 font-semibold">
                  Bộ nhớ
                </th>

                <th className="px-5 py-4 font-semibold">
                  Trạng thái
                </th>

                <th className="px-5 py-4 font-semibold text-right">
                  Thao tác
                </th>
              </tr>
            </thead>

            <tbody>
              {!selectedClientId ? (
                <tr>
                  <td
                    colSpan={7}
                    className={`py-14 text-center ${theme.muted}`}
                  >
                    <Monitor size={30} className="mx-auto mb-3 opacity-40" />

                    <p className="text-sm font-medium">
                      Chưa chọn máy Client
                    </p>

                    <p className="text-xs mt-1">
                      Chọn một máy tính ở phía trên để xem tiến trình.
                    </p>
                  </td>
                </tr>
              ) : selectedClient?.status !== 'ONLINE' ? (
                <tr>
                  <td
                    colSpan={7}
                    className={`py-14 text-center ${theme.muted}`}
                  >
                    <AlertTriangle
                      size={30}
                      className="mx-auto mb-3 text-amber-500"
                    />

                    <p className="text-sm font-medium">
                      Client hiện không trực tuyến
                    </p>

                    <p className="text-xs mt-1">
                      Không thể lấy danh sách tiến trình từ Agent.
                    </p>
                  </td>
                </tr>
              ) : processLoading && processes.length === 0 ? (
                <tr>
                  <td
                    colSpan={7}
                    className={`py-14 text-center ${theme.muted}`}
                  >
                    <Loader2
                      size={25}
                      className="animate-spin mx-auto mb-3 text-cyan-500"
                    />

                    <span className="text-sm">
                      Đang tải danh sách tiến trình...
                    </span>
                  </td>
                </tr>
              ) : processes.length === 0 ? (
                <tr>
                  <td
                    colSpan={7}
                    className={`py-14 text-center ${theme.muted}`}
                  >
                    <Activity size={30} className="mx-auto mb-3 opacity-40" />

                    <p className="text-sm font-medium">
                      Chưa có dữ liệu tiến trình
                    </p>

                    <p className="text-xs mt-1">
                      Agent chưa gửi danh sách Process trong dữ liệu giám sát mới nhất.
                    </p>

                    <button
                      onClick={() => void loadProcesses(true)}
                      className="mt-4 px-4 py-2 rounded-lg bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-semibold"
                    >
                      Thử tải lại
                    </button>
                  </td>
                </tr>
              ) : displayedProcesses.length === 0 ? (
                <tr>
                  <td
                    colSpan={7}
                    className={`py-12 text-center ${theme.muted}`}
                  >
                    <Search size={27} className="mx-auto mb-3 opacity-40" />

                    <p className="text-sm font-medium">
                      Không tìm thấy tiến trình
                    </p>

                    <p className="text-xs mt-1">
                      Thử tìm kiếm bằng tên khác hoặc PID.
                    </p>
                  </td>
                </tr>
              ) : (
                displayedProcesses.map((process) => {
                  const isSelected =
                    selectedProcessPid === process.pid;

                  return (
                    <tr
                      key={process.pid}
                      onClick={() => {
                        setSelectedProcessPid(process.pid);
                        setSelectedCommand(
                          COMMAND_ACTIONS.find(
                            (action) => action.type === 'KILL_PROCESS'
                          ) ?? null
                        );
                        setError('');
                        setSuccess('');
                      }}
                      className={`border-t ${theme.divider} cursor-pointer transition-colors ${
                        isSelected
                          ? theme.selectedRow
                          : theme.cardHover
                      }`}
                    >
                      <td className="px-5 py-3.5">
                        <div
                          className={`w-4 h-4 rounded-full border flex items-center justify-center ${
                            isSelected
                              ? 'border-cyan-500 bg-cyan-500'
                              : isDark
                                ? 'border-slate-600'
                                : 'border-slate-300'
                          }`}
                        >
                          {isSelected && (
                            <CheckCircle2
                              size={12}
                              className="text-white"
                            />
                          )}
                        </div>
                      </td>

                      <td className="px-5 py-3.5">
                        <div className="flex items-center gap-2">
                          <div
                            className={`p-2 rounded-lg ${
                              isSelected
                                ? 'bg-cyan-500/10 text-cyan-500'
                                : isDark
                                  ? 'bg-slate-800 text-slate-400'
                                  : 'bg-slate-100 text-slate-500'
                            }`}
                          >
                            <Terminal size={15} />
                          </div>

                          <div className="min-w-0">
                            <p className={`text-sm font-semibold truncate max-w-[260px] ${theme.heading}`}>
                              {process.name || 'Unknown process'}
                            </p>

                            <p className={`text-[10px] mt-0.5 ${theme.muted}`}>
                              {isSelected
                                ? 'Đang được chọn'
                                : 'Nhấn để chọn tiến trình'
                              }
                            </p>
                          </div>
                        </div>
                      </td>

                      <td className="px-5 py-3.5">
                        <span className="font-mono text-xs font-bold text-cyan-500">
                          {process.pid}
                        </span>
                      </td>

                      <td className="px-5 py-3.5">
                        <div className="flex items-center gap-2">
                          <Cpu size={14} className={theme.muted} />

                          <span className={`text-xs font-semibold ${theme.heading}`}>
                            {formatNumber(process.cpuUsage)}%
                          </span>
                        </div>
                      </td>

                      <td className="px-5 py-3.5">
                        <div className="flex items-center gap-2">
                          <MemoryStick size={14} className={theme.muted} />

                          <span className={`text-xs font-semibold ${theme.heading}`}>
                            {formatNumber(process.memoryUsage)}
                          </span>
                        </div>
                      </td>

                      <td className="px-5 py-3.5">
                        <span
                          className={`inline-flex px-2.5 py-1 rounded-full text-[10px] font-bold ${processStatusColor(process.status, isDark)}`}
                        >
                          {processStatusLabel(process.status)}
                        </span>
                      </td>

                      <td className="px-5 py-3.5 text-right">
                        <button
                          onClick={(e) => {
                            e.stopPropagation();

                            setSelectedProcessPid(process.pid);

                            setSelectedCommand(
                              COMMAND_ACTIONS.find(
                                (action) => action.type === 'KILL_PROCESS'
                              ) ?? null
                            );

                            setError('');
                            setSuccess('');
                          }}
                          className="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg text-xs font-semibold text-rose-500 hover:bg-rose-500/10 transition-colors"
                        >
                          <Trash2 size={13} />
                          Chọn để kết thúc
                        </button>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>

        {/* PROCESS FOOTER */}

        <div className={`px-5 py-3 border-t ${theme.divider} flex flex-col md:flex-row md:items-center justify-between gap-2`}>
          <div className={`text-xs ${theme.muted}`}>
            Hiển thị {displayedProcesses.length} / {filteredProcesses.length} tiến trình phù hợp
          </div>

          <div className={`flex flex-wrap items-center gap-3 text-xs ${theme.muted}`}>
            {filteredProcesses.length > 100 && (
              <button
                onClick={() => setShowAllProcesses((current) => !current)}
                className="text-cyan-500 hover:text-cyan-400 font-semibold"
              >
                {showAllProcesses
                  ? 'Thu gọn danh sách'
                  : 'Hiển thị tất cả'
                }
              </button>
            )}

            <span>
              Dữ liệu thu thập: {formatDate(processCollectedAt)}
            </span>

            <span>
              Tự cập nhật mỗi 5 giây
            </span>
          </div>
        </div>
      </section>

      {/* =================================================
          COMMAND HISTORY
      ================================================= */}

      <section className={`rounded-2xl border overflow-hidden ${theme.card}`}>
        <div className={`p-5 border-b ${theme.divider}`}>
          <div className="flex flex-col xl:flex-row xl:items-center justify-between gap-4">
            <div className="flex items-center gap-3">
              <div className="p-2 rounded-lg bg-cyan-500/10 text-cyan-500">
                <History size={20} />
              </div>

              <div>
                <h2 className={`font-bold ${theme.heading}`}>
                  Lịch sử thực thi lệnh
                </h2>

                <p className={`text-xs mt-1 ${theme.muted}`}>
                  Dữ liệu được lấy trực tiếp từ Backend PBL4.
                </p>
              </div>
            </div>

            <div className="flex flex-col sm:flex-row gap-2">
              <div className="relative">
                <Search
                  size={16}
                  className={`absolute left-3 top-1/2 -translate-y-1/2 ${theme.muted}`}
                />

                <input
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  placeholder="Tìm ID, Client, người gửi..."
                  className={`w-full sm:w-60 pl-9 pr-3 py-2.5 rounded-lg border text-xs outline-none focus:border-cyan-500 ${theme.input}`}
                />
              </div>

              <select
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value)}
                className={`px-3 py-2.5 rounded-lg border text-xs outline-none ${theme.input}`}
              >
                <option value="ALL">Tất cả trạng thái</option>
                <option value="PENDING">Đang chờ</option>
                <option value="SENT">Đã gửi</option>
                <option value="RUNNING">Đang thực thi</option>
                <option value="COMPLETED">Thành công</option>
                <option value="FAILED">Thất bại</option>
                <option value="TIMEOUT">Quá thời gian</option>
              </select>

              <select
                value={typeFilter}
                onChange={(e) => setTypeFilter(e.target.value)}
                className={`px-3 py-2.5 rounded-lg border text-xs outline-none ${theme.input}`}
              >
                <option value="ALL">Tất cả loại lệnh</option>
                <option value="RESTART">Restart</option>
                <option value="SHUTDOWN">Shutdown</option>
                <option value="KILL_PROCESS">Kill Process</option>
              </select>
            </div>
          </div>
        </div>

        {/* HISTORY TABLE */}

        <div className="overflow-x-auto">
          <table className="w-full min-w-[850px] text-left">
            <thead className={theme.tableHead}>
              <tr className="text-[11px] uppercase tracking-wider">
                <th className="px-5 py-4 font-semibold">Mã lệnh</th>
                <th className="px-5 py-4 font-semibold">Client</th>
                <th className="px-5 py-4 font-semibold">Loại lệnh</th>
                <th className="px-5 py-4 font-semibold">Người gửi</th>
                <th className="px-5 py-4 font-semibold">Thời gian</th>
                <th className="px-5 py-4 font-semibold">Trạng thái</th>
                <th className="px-5 py-4 font-semibold text-right">Chi tiết</th>
              </tr>
            </thead>

            <tbody>
              {loading ? (
                <tr>
                  <td
                    colSpan={7}
                    className={`py-14 text-center ${theme.muted}`}
                  >
                    <Loader2
                      size={25}
                      className="animate-spin mx-auto mb-3 text-cyan-500"
                    />

                    <span className="text-sm">
                      Đang tải lịch sử lệnh...
                    </span>
                  </td>
                </tr>
              ) : filteredCommands.length === 0 ? (
                <tr>
                  <td
                    colSpan={7}
                    className={`py-14 text-center ${theme.muted}`}
                  >
                    <History size={30} className="mx-auto mb-3 opacity-40" />

                    <p className="text-sm font-medium">
                      Chưa có dữ liệu lệnh phù hợp
                    </p>

                    <p className="text-xs mt-1">
                      Thử thay đổi bộ lọc hoặc gửi một lệnh quản trị mới.
                    </p>
                  </td>
                </tr>
              ) : (
                filteredCommands.map((item) => {
                  const status = STATUS_CONFIG[item.status];

                  return (
                    <tr
                      key={item.commandId}
                      className={`border-t ${theme.divider} ${theme.cardHover} transition-colors`}
                    >
                      <td className="px-5 py-4">
                        <span className="font-mono text-xs font-bold text-cyan-500">
                          #{item.commandId}
                        </span>
                      </td>

                      <td className="px-5 py-4">
                        <div className={`text-sm font-semibold ${theme.heading}`}>
                          {clients.find(
                            (client) => client.clientId === item.clientId
                          )?.hostname || item.clientId}
                        </div>

                        <div className={`text-[10px] mt-1 font-mono ${theme.muted}`}>
                          {item.clientId}
                        </div>
                      </td>

                      <td className="px-5 py-4">
                        <span className={`text-xs font-semibold ${theme.heading}`}>
                          {commandLabel(item.commandType)}
                        </span>

                        {item.commandType === 'KILL_PROCESS' && (
                          <div className={`text-[10px] mt-1 font-mono ${theme.muted}`}>
                            PID: {(() => {
                              try {
                                const data = JSON.parse(item.parameters || '{}');
                                return data.pid ?? '—';
                              } catch {
                                return '—';
                              }
                            })()}
                          </div>
                        )}
                      </td>

                      <td className={`px-5 py-4 text-xs ${theme.muted}`}>
                        {item.requestedBy || '—'}
                      </td>

                      <td className={`px-5 py-4 text-xs ${theme.muted}`}>
                        {formatDate(item.createdAt)}
                      </td>

                      <td className="px-5 py-4">
                        <span
                          className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full border text-[10px] font-bold ${
                            isDark ? status.color : status.lightColor
                          }`}
                        >
                          {item.status === 'RUNNING' && (
                            <Loader2 size={11} className="animate-spin" />
                          )}

                          {item.status === 'COMPLETED' && (
                            <CheckCircle2 size={11} />
                          )}

                          {item.status === 'FAILED' && (
                            <XCircle size={11} />
                          )}

                          {item.status === 'PENDING' && (
                            <Clock3 size={11} />
                          )}

                          {status.label}
                        </span>
                      </td>

                      <td className="px-5 py-4 text-right">
                        <button
                          onClick={() => setSelectedDetail(item)}
                          className="px-3 py-2 rounded-lg text-xs font-semibold text-cyan-500 hover:bg-cyan-500/10 transition-colors"
                        >
                          Xem chi tiết
                        </button>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>

        <div className={`px-5 py-3 border-t ${theme.divider} flex flex-col sm:flex-row justify-between gap-2`}>
          <span className={`text-xs ${theme.muted}`}>
            Hiển thị {filteredCommands.length} / {commands.length} lệnh đã tải
          </span>

          <span className={`text-xs ${theme.muted}`}>
            Tự động cập nhật mỗi 5 giây
          </span>
        </div>
      </section>

      {/* =================================================
          COMMAND DETAIL MODAL
      ================================================= */}

      {selectedDetail && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm"
          onMouseDown={(e) => {
            if (e.target === e.currentTarget) {
              setSelectedDetail(null);
            }
          }}
        >
          <div className={`w-full max-w-2xl rounded-2xl border shadow-2xl overflow-hidden ${theme.card}`}>
            <div className={`p-5 border-b flex items-center justify-between ${theme.divider}`}>
              <div className="flex items-center gap-3">
                <div className="p-2.5 rounded-xl bg-cyan-500/10 text-cyan-500">
                  <Terminal size={20} />
                </div>

                <div>
                  <h3 className={`font-bold ${theme.heading}`}>
                    Chi tiết lệnh #{selectedDetail.commandId}
                  </h3>

                  <p className={`text-xs mt-1 ${theme.muted}`}>
                    Thông tin thực thi từ Server và Agent
                  </p>
                </div>
              </div>

              <button
                onClick={() => setSelectedDetail(null)}
                className={`p-2 rounded-lg ${theme.cardHover}`}
              >
                <X size={19} />
              </button>
            </div>

            <div className="p-5 space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                {[
                  ['Client ID', selectedDetail.clientId],
                  ['Loại lệnh', commandLabel(selectedDetail.commandType)],
                  ['Người gửi', selectedDetail.requestedBy || '—'],
                  [
                    'Trạng thái',
                    STATUS_CONFIG[selectedDetail.status]?.label ||
                      selectedDetail.status,
                  ],
                  ['Thời điểm tạo', formatDate(selectedDetail.createdAt)],
                  ['Thời điểm gửi', formatDate(selectedDetail.sentAt)],
                  ['Bắt đầu thực thi', formatDate(selectedDetail.startedAt)],
                  ['Hoàn thành', formatDate(selectedDetail.completedAt)],
                ].map(([label, value]) => (
                  <div
                    key={label}
                    className={`p-3 rounded-xl border ${theme.divider} ${isDark ? 'bg-slate-950/50' : 'bg-slate-50'}`}
                  >
                    <p className={`text-[10px] uppercase font-bold tracking-wider ${theme.muted}`}>
                      {label}
                    </p>

                    <p className={`mt-2 text-sm font-medium break-all ${theme.heading}`}>
                      {value}
                    </p>
                  </div>
                ))}
              </div>

              {selectedDetail.parameters && (
                <div>
                  <p className={`text-xs font-bold mb-2 ${theme.muted}`}>
                    THAM SỐ LỆNH
                  </p>

                  <pre className={`p-4 rounded-xl border text-xs font-mono whitespace-pre-wrap break-all ${theme.terminal} text-cyan-400`}>
                    {selectedDetail.parameters}
                  </pre>
                </div>
              )}

              <div>
                <p className={`text-xs font-bold mb-2 ${theme.muted}`}>
                  KẾT QUẢ TỪ AGENT
                </p>

                <pre
                  className={`p-4 rounded-xl border text-xs font-mono whitespace-pre-wrap break-words max-h-52 overflow-y-auto ${
                    theme.terminal
                  } ${
                    selectedDetail.status === 'FAILED'
                      ? 'text-rose-400'
                      : 'text-emerald-400'
                  }`}
                >
                  {selectedDetail.result ||
                    selectedDetail.errorMessage ||
                    'Agent chưa gửi kết quả thực thi.'}
                </pre>
              </div>

              {selectedDetail.errorMessage && selectedDetail.result && (
                <div className="p-3 rounded-xl bg-rose-500/10 border border-rose-500/20 text-rose-500 text-xs">
                  <div className="flex items-center gap-2 font-bold mb-1">
                    <AlertTriangle size={15} />
                    Thông báo lỗi
                  </div>

                  {selectedDetail.errorMessage}
                </div>
              )}
            </div>

            <div className={`p-4 border-t flex justify-end ${theme.divider}`}>
              <button
                onClick={() => setSelectedDetail(null)}
                className="px-5 py-2.5 rounded-lg bg-cyan-600 hover:bg-cyan-500 text-white text-sm font-semibold"
              >
                Đóng
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
