
import { useCallback, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';

import {
  LayoutDashboard,
  Monitor,
  History,
  Bell,
  Users,
  Tv,
  Terminal,
  Settings,
  LogOut,
  Sun,
  Moon,
  Menu,
  X,
  ChevronRight,
  ShieldCheck,
  Search,
  UserCog,
  SlidersHorizontal,
} from 'lucide-react';

import {
  ApiError,
  UNAUTHORIZED_EVENT,
  clearSession,
  getAlertSummary,
  getClientAgentsPage,
  getSession,
  startPolling,
} from '../lib/api';

import { useTheme } from '../context/ThemeContext';
import TuxMascot from '../components/TuxMascot';

// ============================================================
// BACKGROUND
// ============================================================

const DARK_BACKGROUND = [
  'radial-gradient(900px 520px at 80% -8%, rgba(37,99,235,0.38), transparent 60%)',
  'radial-gradient(700px 480px at 0% 100%, rgba(14,165,233,0.22), transparent 60%)',
  'linear-gradient(180deg, #040c20 0%, #030a1c 100%)',
].join(', ');

const LIGHT_BACKGROUND = [
  'radial-gradient(900px 520px at 85% -10%, rgba(59,130,246,0.18), transparent 60%)',
  'linear-gradient(180deg, #f1f5fb, #e8eef8)',
].join(', ');

// ============================================================
// TYPES
// ============================================================

interface NavChild {
  path: string;
  label: string;
  icon?: ReactNode;
}

interface NavItem {
  path: string;
  label: string;
  icon: ReactNode;
  badge?: number;
  children?: NavChild[];
}

// ============================================================
// SERVER CLOCK
// ============================================================

function ServerClock() {
  const [now, setNow] = useState(() => new Date());

  useEffect(() => {
    const timer = window.setInterval(() => {
      setNow(new Date());
    }, 1000);

    return () => window.clearInterval(timer);
  }, []);

  const p = (n: number) => String(n).padStart(2, '0');

  return (
    <span className="font-mono text-[10px] opacity-80">
      {p(now.getHours())}:{p(now.getMinutes())}:{p(now.getSeconds())}{' '}
      {p(now.getDate())}/{p(now.getMonth() + 1)}/{now.getFullYear()}
    </span>
  );
}

// ============================================================
// MAIN LAYOUT
// ============================================================

export default function MainLayout() {
  const navigate = useNavigate();
  const location = useLocation();

  const session = getSession();
  const { toggleTheme, isDark } = useTheme();

  const [sidebarOpen, setSidebarOpen] = useState(false);

  const [settingsOpen, setSettingsOpen] = useState(
    location.pathname.startsWith('/settings'),
  );

  const [serverOnline, setServerOnline] = useState(true);
  const [pendingAgents, setPendingAgents] = useState(0);
  const [activeAlerts, setActiveAlerts] = useState(0);

  const username =
    session?.username || session?.name || 'Thành viên';

  // ==========================================================
  // LOGOUT
  // ==========================================================

  const handleLogout = () => {
    clearSession();
    navigate('/login', { replace: true });
  };

  // ==========================================================
  // UNAUTHORIZED
  // ==========================================================

  useEffect(() => {
    const onUnauthorized = () => {
      navigate('/login', { replace: true });
    };

    window.addEventListener(UNAUTHORIZED_EVENT, onUnauthorized);

    return () => {
      window.removeEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
    };
  }, [navigate]);

  // ==========================================================
  // MOBILE SIDEBAR
  // ==========================================================

  useEffect(() => {
    setSidebarOpen(false);
  }, [location.pathname]);

  // ==========================================================
  // SETTINGS MENU
  // ==========================================================

  useEffect(() => {
    if (location.pathname.startsWith('/settings')) {
      setSettingsOpen(true);
    }
  }, [location.pathname]);

  // ==========================================================
  // SERVER STATUS + BADGES
  // ==========================================================

  const refreshBadges = useCallback(async () => {
    const [agents, alerts] = await Promise.allSettled([
      getClientAgentsPage({
        status: 'PENDING_APPROVAL',
        page: 0,
        size: 1,
      }),
      getAlertSummary(),
    ]);

    const responded = [agents, alerts].some(
      (result) =>
        result.status === 'fulfilled' ||
        (
          result.reason instanceof ApiError &&
          result.reason.status !== 0
        ),
    );

    setServerOnline(responded);

    if (agents.status === 'fulfilled') {
      setPendingAgents(agents.value.totalElements);
    }

    if (alerts.status === 'fulfilled') {
      setActiveAlerts(alerts.value.active);
    }
  }, []);

  useEffect(() => {
    return startPolling(refreshBadges, 15_000);
  }, [refreshBadges]);

  // ==========================================================
  // MENU ITEMS
  // ==========================================================

  const menuItems: NavItem[] = [
    {
      path: '/dashboard',
      label: 'Dashboard',
      icon: <LayoutDashboard className="w-5 h-5" />,
    },

    {
      path: '/computers',
      label: 'Computers',
      icon: <Monitor className="w-5 h-5" />,
      children: [
        {
          path: '/computers',
          label: 'Danh sách máy',
        },
      ],
    },

    {
      path: '/agents',
      label: 'Agents',
      icon: <Users className="w-5 h-5" />,
      badge: pendingAgents,
      children: [
        {
          path: '/agents',
          label: 'Quản lý Agent',
        },
      ],
    },

    {
      path: '/remote-desktop',
      label: 'Remote Desktop',
      icon: <Tv className="w-5 h-5" />,
    },

    {
      path: '/remote-commands',
      label: 'Remote Commands',
      icon: <Terminal className="w-5 h-5" />,
    },

    {
      path: '/settings',
      label: 'Settings',
      icon: <Settings className="w-5 h-5" />,
      children: [
        {
          path: '/settings/accounts',
          label: 'Quản lý tài khoản',
          icon: <UserCog className="w-4 h-4" />,
        },
        {
          path: '/settings/system',
          label: 'Cấu hình hệ thống',
          icon: <SlidersHorizontal className="w-4 h-4" />,
        },
      ],
    },

    {
      path: '/alerts',
      label: 'Alerts',
      icon: <Bell className="w-5 h-5" />,
      badge: activeAlerts,
    },

    {
      path: '/history',
      label: 'History',
      icon: <History className="w-5 h-5" />,
    },
  ];

  // ==========================================================
  // ACTIVE MENU
  // ==========================================================

  const isMenuActive = (path: string) => {
    if (path === '/settings') {
      return location.pathname.startsWith('/settings');
    }

    return (
      location.pathname === path ||
      location.pathname.startsWith(`${path}/`)
    );
  };

  // ==========================================================
  // CURRENT PAGE TITLE
  // ==========================================================

  const getCurrentLabel = () => {
    const pathname = location.pathname;

    const settingChild = menuItems
      .find((item) => item.path === '/settings')
      ?.children?.find((child) => child.path === pathname);

    if (settingChild) {
      return settingChild.label;
    }

    const normalItem = menuItems.find(
      (item) =>
        !item.children &&
        (
          pathname === item.path ||
          pathname.startsWith(`${item.path}/`)
        ),
    );

    if (normalItem) {
      return normalItem.label;
    }

    const parentItem = menuItems.find(
      (item) =>
        item.children &&
        item.path !== '/settings' &&
        isMenuActive(item.path),
    );

    if (parentItem) {
      return parentItem.children?.find(
        (child) => child.path === pathname,
      )?.label || parentItem.label;
    }

    return 'Dashboard';
  };

  const currentLabel = getCurrentLabel();

  // ==========================================================
  // THEME CLASSES
  // ==========================================================

  const sidebarTheme = isDark
    ? 'bg-[#050f27]/90 border-blue-500/20 text-slate-100 shadow-[4px_0_40px_-12px_rgba(37,99,235,0.5)]'
    : 'bg-white/95 border-slate-200 text-slate-900';

  const headerTheme = isDark
    ? 'bg-[#030a1c]/60 border-blue-500/15'
    : 'bg-white/70 border-slate-200';

  // ==========================================================
  // RENDER
  // ==========================================================

  return (
    <div
      className={`min-h-screen flex transition-colors duration-500 ${
        isDark ? 'text-slate-100' : 'text-slate-900'
      }`}
      style={{
        backgroundColor: isDark ? '#030a1c' : '#eef2f9',
        backgroundImage: isDark
          ? DARK_BACKGROUND
          : LIGHT_BACKGROUND,
        backgroundAttachment: 'fixed',
      }}
    >

      {/* MOBILE OVERLAY */}

      {sidebarOpen && (
        <div
          className="fixed inset-0 z-40 bg-black/50 lg:hidden"
          onClick={() => setSidebarOpen(false)}
        />
      )}

      {/* ================================================== */}
      {/* SIDEBAR */}
      {/* ================================================== */}

      <aside
        className={`fixed inset-y-0 left-0 z-50 w-64 transition-transform duration-300 border-r flex flex-col backdrop-blur-xl overflow-hidden ${sidebarTheme} ${
          sidebarOpen
            ? 'translate-x-0'
            : '-translate-x-full'
        } lg:translate-x-0`}
      >

        {/* LOGO */}

        <div
          className={`h-20 shrink-0 flex items-center px-5 border-b gap-3 ${
            isDark
              ? 'border-blue-500/15'
              : 'border-slate-200'
          }`}
        >
          <TuxMascot className="w-10 h-10 shrink-0" />

          <div>
            <h1
              className={`font-black tracking-wide text-lg leading-none ${
                isDark ? 'text-white' : 'text-slate-900'
              }`}
            >
              PBL4
            </h1>

            <p
              className={`text-[11px] mt-1 ${
                isDark ? 'text-slate-400' : 'text-slate-500'
              }`}
            >
              Resource Monitoring
            </p>
          </div>
        </div>

        {/* NAVIGATION */}

        <nav className="relative z-10 flex-1 px-3 py-5 space-y-1 overflow-y-auto">

          {menuItems.map((item) => {
            const isActive = isMenuActive(item.path);

            const showChildren =
              !!item.children &&
              (
                item.path === '/settings'
                  ? settingsOpen
                  : isActive
              );

            const isSettings = item.path === '/settings';

            return (
              <div key={item.path}>

                {/* PARENT MENU */}

                <button
                  type="button"
                  onClick={() => {
                    if (item.children) {
                      if (isSettings) {
                        setSettingsOpen((previous) => !previous);
                      } else {
                        navigate(item.path);
                      }
                    } else {
                      navigate(item.path);
                    }
                  }}
                  aria-expanded={
                    item.children ? showChildren : undefined
                  }
                  className={`w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-sm font-medium transition-all ${
                    isActive
                      ? 'bg-gradient-to-r from-blue-600 to-cyan-500 text-white shadow-[0_0_22px_rgba(34,211,238,0.35)]'
                      : isDark
                        ? 'text-slate-300 hover:bg-blue-600/15 hover:text-white'
                        : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
                  }`}
                >
                  <div className="flex items-center gap-3">
                    {item.icon}

                    <span>{item.label}</span>
                  </div>

                  <div className="flex items-center gap-1.5">

                    {typeof item.badge === 'number' &&
                      item.badge > 0 && (
                        <span className="min-w-5 h-5 px-1.5 inline-flex items-center justify-center text-[10px] font-bold bg-red-500 text-white rounded-full">
                          {item.badge > 99
                            ? '99+'
                            : item.badge}
                        </span>
                      )}

                    {item.children && (
                      <ChevronRight
                        className={`w-4 h-4 opacity-70 transition-transform ${
                          showChildren ? 'rotate-90' : ''
                        }`}
                      />
                    )}
                  </div>
                </button>

                {/* CHILD MENU */}

                {showChildren && item.children && (
                  <div className="mt-1 mb-1 ml-5 pl-3 border-l border-blue-500/25 space-y-0.5">

                    {item.children.map((child) => {
                      const childActive =
                        location.pathname === child.path;

                      return (
                        <button
                          type="button"
                          key={child.path}
                          onClick={() => navigate(child.path)}
                          className={`w-full flex items-center gap-2.5 px-3 py-2.5 rounded-lg text-xs transition-colors ${
                            childActive
                              ? 'bg-blue-600/25 text-cyan-300 font-semibold'
                              : isDark
                                ? 'text-slate-400 hover:text-slate-200 hover:bg-white/5'
                                : 'text-slate-500 hover:text-slate-800 hover:bg-slate-100'
                          }`}
                        >
                          {child.icon ? (
                            child.icon
                          ) : (
                            <span
                              className={`w-1.5 h-1.5 rounded-full ${
                                childActive
                                  ? 'bg-cyan-400 shadow-[0_0_8px_#22d3ee]'
                                  : 'bg-slate-500'
                              }`}
                            />
                          )}

                          <span>{child.label}</span>
                        </button>
                      );
                    })}
                  </div>
                )}
              </div>
            );
          })}
        </nav>

        {/* DECORATIVE TUX */}

        <TuxMascot className="absolute bottom-16 left-1/2 -translate-x-1/2 w-40 h-40 opacity-25 pointer-events-none" />

        {/* SERVER STATUS */}

        <div className="relative z-10 p-4 shrink-0">
          <div
            className={`p-3 rounded-2xl border flex items-center justify-between gap-2 ${
              isDark
                ? 'bg-[#081226]/80 border-blue-500/25'
                : 'bg-slate-50 border-slate-200'
            }`}
          >
            <div className="flex items-center gap-2.5">

              <span
                className={`w-2.5 h-2.5 rounded-full animate-pulse ${
                  serverOnline
                    ? 'bg-emerald-400 shadow-[0_0_10px_#34d399]'
                    : 'bg-red-500 shadow-[0_0_10px_#ef4444]'
                }`}
              />

              <div>
                <p
                  className={`text-xs font-semibold ${
                    serverOnline
                      ? 'text-emerald-400'
                      : 'text-red-400'
                  }`}
                >
                  {serverOnline
                    ? 'Server Online'
                    : 'Server Offline'}
                </p>

                <div
                  className={
                    isDark
                      ? 'text-slate-400'
                      : 'text-slate-500'
                  }
                >
                  <ServerClock />
                </div>
              </div>
            </div>

            <Settings
              className={`w-4 h-4 ${
                isDark
                  ? 'text-slate-400'
                  : 'text-slate-500'
              }`}
            />
          </div>
        </div>
      </aside>

      {/* ================================================== */}
      {/* MAIN CONTENT */}
      {/* ================================================== */}

      <div className="flex-1 flex flex-col lg:pl-64 min-w-0">

        {/* HEADER */}

        <header
          className={`h-16 sticky top-0 z-30 border-b backdrop-blur-xl px-4 sm:px-6 flex items-center gap-4 transition-colors ${headerTheme}`}
        >

          {/* MOBILE MENU BUTTON */}

          <button
            type="button"
            onClick={() => setSidebarOpen((previous) => !previous)}
            className={`p-2 rounded-xl border lg:hidden ${
              isDark
                ? 'border-blue-500/25 text-slate-300 hover:text-white bg-[#081226]/70'
                : 'border-slate-300 text-slate-600 bg-white'
            }`}
            aria-label="Mở menu"
          >
            {sidebarOpen ? (
              <X className="w-5 h-5" />
            ) : (
              <Menu className="w-5 h-5" />
            )}
          </button>

          {/* PAGE TITLE */}

          <h2
            className={`hidden md:block text-sm font-bold tracking-tight shrink-0 ${
              isDark ? 'text-white' : 'text-slate-900'
            }`}
          >
            {currentLabel}
          </h2>

          {/* SEARCH */}

          <div className="relative flex-1 max-w-xl">
            <Search className="w-4 h-4 text-slate-400 absolute left-4 top-1/2 -translate-y-1/2" />

            <input
              type="text"
              placeholder="Tìm kiếm máy tính, Agent, hoặc Client..."
              className={`w-full rounded-full border pl-11 pr-4 py-2.5 text-xs focus:outline-none focus:ring-1 focus:ring-cyan-400 ${
                isDark
                  ? 'bg-[#081226]/70 border-blue-500/25 text-slate-200 placeholder-slate-500'
                  : 'bg-white border-slate-300 text-slate-700'
              }`}
            />
          </div>

          {/* RIGHT HEADER */}

          <div className="ml-auto flex items-center gap-2 sm:gap-3">

            {/* ALERT */}

            <button
              type="button"
              onClick={() => navigate('/alerts')}
              title="Cảnh báo"
              className={`relative p-2.5 rounded-full transition-colors ${
                isDark
                  ? 'text-slate-300 hover:bg-blue-600/20 hover:text-white'
                  : 'text-slate-600 hover:bg-slate-100'
              }`}
            >
              <Bell className="w-5 h-5" />

              {activeAlerts > 0 && (
                <span className="absolute -top-0.5 -right-0.5 min-w-4 h-4 px-1 inline-flex items-center justify-center text-[9px] font-bold bg-red-500 text-white rounded-full">
                  {activeAlerts > 99
                    ? '99+'
                    : activeAlerts}
                </span>
              )}
            </button>

            {/* THEME */}

            <button
              type="button"
              onClick={() =>
                toggleTheme(isDark ? 'light' : 'dark')
              }
              title={
                isDark
                  ? 'Chuyển sang giao diện sáng'
                  : 'Chuyển sang giao diện tối'
              }
              className={`p-2.5 rounded-full transition-colors ${
                isDark
                  ? 'text-slate-300 hover:bg-blue-600/20 hover:text-white'
                  : 'text-slate-600 hover:bg-slate-100'
              }`}
            >
              {isDark ? (
                <Sun className="w-5 h-5" />
              ) : (
                <Moon className="w-5 h-5" />
              )}
            </button>

            {/* USER */}

            <div className="flex items-center gap-2.5 pl-2">

              <div className="w-9 h-9 rounded-full bg-gradient-to-br from-blue-500 to-cyan-400 flex items-center justify-center text-white font-bold text-sm shadow-[0_0_14px_rgba(34,211,238,0.5)]">
                {username.charAt(0).toUpperCase()}
              </div>

              <div className="hidden sm:block leading-tight">
                <p
                  className={`text-xs font-semibold ${
                    isDark ? 'text-white' : 'text-slate-800'
                  }`}
                >
                  {username}
                </p>

                <span className="text-[10px] text-cyan-400 font-mono flex items-center gap-1">
                  <ShieldCheck className="w-3 h-3" />

                  {session?.role || 'MONITOR'}
                </span>
              </div>

              {/* LOGOUT */}

              <button
                type="button"
                onClick={handleLogout}
                title="Đăng xuất"
                className="p-2 text-slate-400 hover:text-red-400 transition-colors rounded-lg hover:bg-red-500/10"
              >
                <LogOut className="w-4 h-4" />
              </button>
            </div>
          </div>
        </header>

        {/* PAGE CONTENT */}

        <main className="flex-1 p-4 sm:p-6 lg:p-8 overflow-y-auto">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
