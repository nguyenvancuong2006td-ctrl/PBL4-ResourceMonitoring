import { useCallback, useEffect, useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import type { LucideIcon } from 'lucide-react';
import {
  Activity,
  Check,
  CircleAlert,
  Eye,
  EyeOff,
  Lock,
  Mail,
  Pencil,
  Plus,
  RefreshCw,
  Search,
  Shield,
  ShieldCheck,
  Trash2,
  Unlock,
  Users,
  X,
} from 'lucide-react';

import {
  getAccounts,
  createAccount,
  updateAccount,
  updateAccountRole,
  lockAccount,
  unlockAccount,
  deleteAccount,
  getSession,
} from '../../lib/api';

import type {
  AccountInfo,
  AccountRole,
  AccountStatus,
} from '../../lib/api';

import { useTheme } from '../../context/ThemeContext';

type FilterStatus = 'ALL' | AccountStatus;
type FilterRole = 'ALL' | AccountRole;
type AccountAction = 'lock' | 'unlock' | 'delete' | AccountRole;

// Chế độ của form: tạo mới hoặc chỉnh sửa một tài khoản
type FormMode = { type: 'create' } | { type: 'edit'; account: AccountInfo };

const statusLabel: Record<AccountStatus, string> = {
  ACTIVE: 'Đang hoạt động',
  PENDING: 'Chờ xác thực',
  INACTIVE: 'Không hoạt động',
  LOCKED: 'Đã khóa',
};

// Quy tắc kiểm tra dữ liệu, khớp với backend (CreateAccountRequest / PasswordPolicy)
const USERNAME_REGEX = /^[A-Za-z0-9._-]{3,50}$/;
const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
// 8-64 ký tự ASCII hiển thị được, có ít nhất 1 chữ cái và 1 chữ số
const PASSWORD_REGEX = /^(?=.*[A-Za-z])(?=.*\d)[\x21-\x7E]{8,64}$/;

const dateText = (
  value: AccountInfo['createAt'] | null | undefined,
): string => {
  if (!value) return '—';

  let date: Date;

  if (Array.isArray(value)) {
    const parts = value as number[];

    date = new Date(
      parts[0] ?? 1970,
      (parts[1] ?? 1) - 1,
      parts[2] ?? 1,
      parts[3] ?? 0,
      parts[4] ?? 0,
      parts[5] ?? 0,
    );
  } else {
    date = new Date(value as string);
  }

  return Number.isNaN(date.getTime())
    ? '—'
    : date.toLocaleString('vi-VN');
};

const statusClass = (status: AccountStatus): string => {
  switch (status) {
    case 'ACTIVE':
      return 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-300';
    case 'LOCKED':
      return 'bg-rose-500/10 text-rose-600 dark:text-rose-300';
    case 'PENDING':
      return 'bg-amber-500/10 text-amber-600 dark:text-amber-300';
    case 'INACTIVE':
      return 'bg-slate-500/10 text-slate-600 dark:text-slate-300';
    default:
      return 'bg-slate-500/10 text-slate-600 dark:text-slate-300';
  }
};

export default function AccountManagementPage() {
  const { isDark } = useTheme();

  const [accounts, setAccounts] = useState<AccountInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);

  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState<FilterStatus>('ALL');
  const [roleFilter, setRoleFilter] = useState<FilterRole>('ALL');

  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [selected, setSelected] = useState<AccountInfo | null>(null);

  // ---------- Form tạo / sửa tài khoản ----------
  const [formMode, setFormMode] = useState<FormMode | null>(null);
  const [formUsername, setFormUsername] = useState('');
  const [formEmail, setFormEmail] = useState('');
  const [formPassword, setFormPassword] = useState('');
  const [formRole, setFormRole] = useState<AccountRole>('MONITOR');
  const [showPassword, setShowPassword] = useState(false);
  const [formError, setFormError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const session = getSession();
  const isAdmin = session?.role === 'ADMIN';

  const canView =
    isAdmin ||
    Boolean(session?.permissions?.includes('ACCOUNT_VIEW'));

  // =========================================================
  // THEME STYLES
  // =========================================================

  const card = isDark
    ? 'rounded-2xl border border-white/10 bg-slate-900/55 shadow-xl shadow-black/10'
    : 'rounded-2xl border border-slate-200 bg-white shadow-lg shadow-slate-200/40';

  const textPrimary = isDark
    ? 'text-slate-100'
    : 'text-slate-800';

  const textSecondary = isDark
    ? 'text-slate-400'
    : 'text-slate-500';

  const input = isDark
    ? 'w-full rounded-xl border border-white/10 bg-slate-950/50 px-3 py-2.5 text-sm text-slate-100 outline-none transition focus:border-blue-400'
    : 'w-full rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm text-slate-800 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/10';

  const buttonSecondary = isDark
    ? 'border-white/10 bg-white/5 text-slate-200 hover:bg-white/10'
    : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-100';

  const buttonPrimary =
    'bg-blue-600 text-white hover:bg-blue-500 disabled:cursor-not-allowed disabled:opacity-50';

  const tableHeader = isDark
    ? 'bg-white/[.035] text-slate-400'
    : 'bg-slate-50 text-slate-500';

  const tableRow = isDark
    ? 'divide-y divide-white/[.06]'
    : 'divide-y divide-slate-100';

  const modal = isDark
    ? 'border-white/10 bg-[#0b1730] text-slate-100'
    : 'border-slate-200 bg-white text-slate-800';

  const modalItem = isDark
    ? 'bg-white/[.04]'
    : 'bg-slate-50';

  // =========================================================
  // LOAD ACCOUNTS
  // =========================================================

  const load = useCallback(async (): Promise<AccountInfo[] | null> => {
    setLoading(true);
    setError('');

    try {
      const data = await getAccounts();

      setAccounts(data);

      setSelected((current) => {
        if (!current) return null;

        return (
          data.find(
            (account) => account.accountId === current.accountId,
          ) ?? null
        );
      });

      return data;
    } catch (e) {
      setError(
        e instanceof Error
          ? e.message
          : 'Không thể tải danh sách tài khoản.',
      );

      return null;
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  // =========================================================
  // FILTER ACCOUNTS
  // =========================================================

  const visible = useMemo(() => {
    const keyword = query.trim().toLowerCase();

    return accounts.filter((account) => {
      const matchesKeyword =
        !keyword ||
        account.username?.toLowerCase().includes(keyword) ||
        account.email?.toLowerCase().includes(keyword) ||
        String(account.accountId).includes(keyword);

      const matchesStatus =
        filter === 'ALL' || account.status === filter;

      const matchesRole =
        roleFilter === 'ALL' || account.role === roleFilter;

      return matchesKeyword && matchesStatus && matchesRole;
    });
  }, [accounts, query, filter, roleFilter]);

  // =========================================================
  // STATISTICS
  // =========================================================

  const counts = useMemo(
    () => ({
      total: accounts.length,
      active: accounts.filter(
        (account) => account.status === 'ACTIVE',
      ).length,
      pending: accounts.filter(
        (account) => account.status === 'PENDING',
      ).length,
      locked: accounts.filter(
        (account) => account.status === 'LOCKED',
      ).length,
    }),
    [accounts],
  );

  const statistics: {
    label: string;
    value: number;
    Icon: LucideIcon;
  }[] = [
    { label: 'Tổng tài khoản', value: counts.total, Icon: Users },
    { label: 'Đang hoạt động', value: counts.active, Icon: Activity },
    { label: 'Chờ xác thực', value: counts.pending, Icon: Mail },
    { label: 'Đã khóa', value: counts.locked, Icon: Lock },
  ];

  // =========================================================
  // ACCOUNT ACTIONS
  // =========================================================

  const runAction = async (
    account: AccountInfo,
    action: AccountAction,
  ) => {
    if (!isAdmin) return;

    if (account.username === session?.username) {
      setError(
        'Bạn không thể thực hiện thao tác trên tài khoản của chính mình.',
      );
      return;
    }

    if (action === 'delete') {
      const confirmed = window.confirm(
        `Bạn có chắc chắn muốn xóa tài khoản "${account.username}"?\n\nThao tác này không thể hoàn tác.`,
      );

      if (!confirmed) return;
    }

    if (action === 'lock') {
      const confirmed = window.confirm(
        `Bạn có chắc chắn muốn khóa tài khoản "${account.username}"?`,
      );

      if (!confirmed) return;
    }

    setBusyId(account.accountId);
    setError('');
    setNotice('');

    try {
      if (action === 'lock') {
        await lockAccount(account.accountId);
        setNotice(`Đã khóa tài khoản ${account.username}.`);
      } else if (action === 'unlock') {
        await unlockAccount(account.accountId);
        setNotice(`Đã mở khóa tài khoản ${account.username}.`);
      } else if (action === 'delete') {
        await deleteAccount(account.accountId);

        setNotice(`Đã xóa tài khoản ${account.username}.`);

        if (selected?.accountId === account.accountId) {
          setSelected(null);
        }
      } else {
        await updateAccountRole(account.accountId, action);

        setNotice(
          `Đã thay đổi vai trò tài khoản ${account.username} thành ${action}.`,
        );
      }

      await load();
    } catch (e) {
      setError(
        e instanceof Error
          ? e.message
          : 'Thao tác thất bại. Vui lòng thử lại.',
      );
    } finally {
      setBusyId(null);
    }
  };

  // =========================================================
  // FORM TẠO / CẬP NHẬT TÀI KHOẢN
  // =========================================================

  const openCreateForm = () => {
    setFormMode({ type: 'create' });
    setFormUsername('');
    setFormEmail('');
    setFormPassword('');
    setFormRole('MONITOR');
    setShowPassword(false);
    setFormError('');
  };

  const openEditForm = (account: AccountInfo) => {
    setSelected(null);
    setFormMode({ type: 'edit', account });
    setFormUsername(account.username);
    setFormEmail(account.email);
    setFormPassword('');
    setFormRole(account.role);
    setShowPassword(false);
    setFormError('');
  };

  const closeForm = () => {
    if (submitting) return;
    setFormMode(null);
    setFormError('');
  };

  const editingAccount =
    formMode?.type === 'edit' ? formMode.account : null;

  const handleSubmitForm = async (e: FormEvent) => {
    e.preventDefault();

    if (!formMode || !isAdmin || submitting) return;

    const username = formUsername.trim();
    const email = formEmail.trim().toLowerCase();
    const password = formPassword;
    const isCreate = formMode.type === 'create';

    // ----- Kiểm tra dữ liệu phía giao diện (backend vẫn kiểm tra lại) -----
    if (!USERNAME_REGEX.test(username)) {
      setFormError(
        'Username phải từ 3 đến 50 ký tự, chỉ gồm chữ, số, dấu chấm, gạch dưới và gạch ngang.',
      );
      return;
    }

    if (!EMAIL_REGEX.test(email) || email.length > 255) {
      setFormError('Email không hợp lệ.');
      return;
    }

    if (isCreate && !password) {
      setFormError('Mật khẩu không được để trống.');
      return;
    }

    if (password && !PASSWORD_REGEX.test(password)) {
      setFormError(
        'Mật khẩu phải từ 8 đến 64 ký tự, có ít nhất một chữ cái và một chữ số, không chứa dấu hoặc khoảng trắng.',
      );
      return;
    }

    setSubmitting(true);
    setFormError('');
    setError('');
    setNotice('');

    try {
      if (formMode.type === 'create') {
        await createAccount({
          username,
          email,
          password,
          roleName: formRole,
        });

        setNotice(
          `Đã tạo tài khoản ${username}. Tài khoản có thể đăng nhập ngay.`,
        );
      } else {
        await updateAccount(formMode.account.accountId, {
          username,
          email,
          roleName: formRole,
          password: password || undefined,
        });

        setNotice(`Đã cập nhật tài khoản ${username}.`);
      }

      setFormMode(null);
      await load();
    } catch (err) {
      // Giữ nguyên form để người dùng sửa lại
      setFormError(
        err instanceof Error
          ? err.message
          : 'Thao tác thất bại. Vui lòng thử lại.',
      );
    } finally {
      setSubmitting(false);
    }
  };

  // =========================================================
  // PERMISSION CHECK
  // =========================================================

  if (!canView) {
    return (
      <div
        className={`rounded-2xl border p-6 ${
          isDark
            ? 'border-amber-400/30 bg-amber-400/10 text-amber-200'
            : 'border-amber-200 bg-amber-50 text-amber-800'
        }`}
      >
        <div className="flex items-center gap-3">
          <Shield size={22} />

          <div>
            <h2 className="font-semibold">
              Không có quyền truy cập
            </h2>

            <p className="mt-1 text-sm opacity-80">
              Tài khoản của bạn không có quyền ACCOUNT_VIEW
              để xem danh sách tài khoản.
            </p>
          </div>
        </div>
      </div>
    );
  }

  // =========================================================
  // RENDER
  // =========================================================

  return (
    <div className={`space-y-6 ${textPrimary}`}>

      {/* HEADER */}

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[.22em] text-blue-500 dark:text-blue-300">
            <ShieldCheck size={15} />
            Administration
          </div>

          <h1 className="mt-2 text-2xl font-bold">
            Quản lý tài khoản
          </h1>

          <p className={`mt-1 text-sm ${textSecondary}`}>
            Quản lý người dùng, vai trò và trạng thái truy cập hệ thống.
          </p>
        </div>

        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => void load()}
            disabled={loading}
            className={`inline-flex items-center justify-center gap-2 rounded-xl border px-4 py-2.5 text-sm transition disabled:cursor-not-allowed disabled:opacity-50 ${buttonSecondary}`}
          >
            <RefreshCw
              size={16}
              className={loading ? 'animate-spin' : ''}
            />
            Làm mới dữ liệu
          </button>

          {isAdmin && (
            <button
              type="button"
              onClick={openCreateForm}
              className={`inline-flex items-center justify-center gap-2 rounded-xl px-4 py-2.5 text-sm font-medium transition ${buttonPrimary}`}
            >
              <Plus size={16} />
              Tạo tài khoản
            </button>
          )}
        </div>
      </div>

      {/* STATISTICS */}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {statistics.map(({ label, value, Icon }) => (
          <div key={label} className={`${card} p-5`}>
            <div className="flex items-center justify-between">
              <span className={`text-sm ${textSecondary}`}>
                {label}
              </span>

              <Icon
                size={19}
                className="text-blue-500 dark:text-blue-300"
              />
            </div>

            <div className="mt-3 text-3xl font-bold tabular-nums">
              {value}
            </div>
          </div>
        ))}
      </div>

      {/* ERROR */}

      {error && (
        <div
          className={`flex items-start gap-2 rounded-xl border p-3 text-sm ${
            isDark
              ? 'border-rose-400/30 bg-rose-400/10 text-rose-200'
              : 'border-rose-200 bg-rose-50 text-rose-700'
          }`}
        >
          <CircleAlert size={17} className="mt-0.5 shrink-0" />

          <span>{error}</span>

          <button
            type="button"
            onClick={() => setError('')}
            className="ml-auto rounded p-1 hover:bg-black/5 dark:hover:bg-white/10"
            title="Đóng thông báo"
          >
            <X size={15} />
          </button>
        </div>
      )}

      {/* SUCCESS */}

      {notice && (
        <div
          className={`flex items-center gap-2 rounded-xl border p-3 text-sm ${
            isDark
              ? 'border-emerald-400/30 bg-emerald-400/10 text-emerald-200'
              : 'border-emerald-200 bg-emerald-50 text-emerald-700'
          }`}
        >
          <Check size={17} />

          <span>{notice}</span>

          <button
            type="button"
            onClick={() => setNotice('')}
            className="ml-auto rounded p-1 hover:bg-black/5 dark:hover:bg-white/10"
            title="Đóng thông báo"
          >
            <X size={15} />
          </button>
        </div>
      )}

      {/* ACCOUNT TABLE */}

      <section className={`${card} overflow-hidden`}>

        {/* TABLE HEADER */}

        <div
          className={`flex flex-col gap-3 border-b p-5 xl:flex-row xl:items-center xl:justify-between ${
            isDark ? 'border-white/10' : 'border-slate-200'
          }`}
        >
          <div>
            <h2 className="font-semibold">
              Danh sách người dùng
            </h2>

            <p className={`mt-1 text-xs ${textSecondary}`}>
              Dữ liệu trực tiếp từ GET /api/accounts
            </p>
          </div>

          <div className="grid grid-cols-1 gap-2 sm:grid-cols-3 xl:w-[680px]">

            {/* SEARCH */}

            <div className="relative">
              <Search
                size={16}
                className="absolute left-3 top-3 text-slate-400"
              />

              <input
                type="text"
                className={`${input} pl-9`}
                placeholder="Tìm username, email, ID..."
                value={query}
                onChange={(e) => setQuery(e.target.value)}
              />
            </div>

            {/* STATUS FILTER */}

            <select
              className={input}
              value={filter}
              onChange={(e) =>
                setFilter(e.target.value as FilterStatus)
              }
            >
              <option value="ALL">Tất cả trạng thái</option>
              <option value="ACTIVE">Đang hoạt động</option>
              <option value="PENDING">Chờ xác thực</option>
              <option value="LOCKED">Đã khóa</option>
              <option value="INACTIVE">Không hoạt động</option>
            </select>

            {/* ROLE FILTER */}

            <select
              className={input}
              value={roleFilter}
              onChange={(e) =>
                setRoleFilter(e.target.value as FilterRole)
              }
            >
              <option value="ALL">Tất cả vai trò</option>
              <option value="ADMIN">ADMIN</option>
              <option value="MONITOR">MONITOR</option>
            </select>
          </div>
        </div>

        {/* TABLE */}

        <div className="overflow-x-auto">
          <table className="w-full min-w-[900px] text-left text-sm">

            <thead className={`${tableHeader} text-xs uppercase tracking-wider`}>
              <tr>
                <th className="px-5 py-4">Tài khoản</th>
                <th className="px-4 py-4">Vai trò</th>
                <th className="px-4 py-4">Trạng thái</th>
                <th className="px-4 py-4">Email</th>
                <th className="px-4 py-4">Ngày tạo</th>
                <th className="px-5 py-4 text-right">Thao tác</th>
              </tr>
            </thead>

            <tbody className={tableRow}>

              {loading ? (
                <tr>
                  <td
                    colSpan={6}
                    className={`px-5 py-12 text-center ${textSecondary}`}
                  >
                    <RefreshCw
                      size={20}
                      className="mx-auto mb-2 animate-spin"
                    />
                    Đang tải dữ liệu...
                  </td>
                </tr>
              ) : visible.length === 0 ? (
                <tr>
                  <td
                    colSpan={6}
                    className={`px-5 py-12 text-center ${textSecondary}`}
                  >
                    Không tìm thấy tài khoản phù hợp.
                  </td>
                </tr>
              ) : (
                visible.map((account) => (
                  <tr
                    key={account.accountId}
                    className={`transition ${
                      isDark
                        ? 'hover:bg-white/[.025]'
                        : 'hover:bg-slate-50'
                    }`}
                  >

                    {/* USERNAME */}

                    <td className="px-5 py-4">
                      <button
                        type="button"
                        onClick={() => setSelected(account)}
                        className="text-left"
                      >
                        <div className="font-semibold transition hover:text-blue-500">
                          {account.username}
                        </div>

                        <div className={`mt-1 text-xs ${textSecondary}`}>
                          ID #{account.accountId}
                        </div>
                      </button>
                    </td>

                    {/* ROLE */}

                    <td className="px-4 py-4">
                      <span
                        className={`rounded-lg px-2.5 py-1 text-xs font-semibold ${
                          account.role === 'ADMIN'
                            ? 'bg-violet-500/10 text-violet-600 dark:text-violet-200'
                            : 'bg-blue-500/10 text-blue-600 dark:text-blue-200'
                        }`}
                      >
                        {account.role}
                      </span>
                    </td>

                    {/* STATUS */}

                    <td className="px-4 py-4">
                      <span
                        className={`inline-flex rounded-lg px-2.5 py-1 text-xs ${statusClass(account.status)}`}
                      >
                        {statusLabel[account.status]}
                      </span>
                    </td>

                    {/* EMAIL */}

                    <td className="px-4 py-4">
                      <div className={isDark ? 'text-slate-300' : 'text-slate-700'}>
                        {account.email}
                      </div>

                      <div className={`mt-1 text-xs ${textSecondary}`}>
                        {account.emailVerified
                          ? 'Email đã xác thực'
                          : 'Email chưa xác thực'}
                      </div>
                    </td>

                    {/* CREATED DATE */}

                    <td className={`px-4 py-4 text-xs ${textSecondary}`}>
                      {dateText(account.createAt)}
                    </td>

                    {/* ACTIONS */}

                    <td className="px-5 py-4">
                      <div className="flex justify-end gap-1">

                        <button
                          type="button"
                          title="Xem chi tiết"
                          onClick={() => setSelected(account)}
                          className={`rounded-lg p-2 transition ${
                            isDark
                              ? 'text-slate-300 hover:bg-white/10 hover:text-blue-300'
                              : 'text-slate-600 hover:bg-blue-50 hover:text-blue-600'
                          }`}
                        >
                          <Shield size={16} />
                        </button>

                        {isAdmin &&
                          account.username !== session?.username && (
                            <>

                              {/* EDIT */}

                              <button
                                type="button"
                                title="Chỉnh sửa tài khoản"
                                disabled={busyId === account.accountId}
                                onClick={() => openEditForm(account)}
                                className={`rounded-lg p-2 transition disabled:cursor-not-allowed disabled:opacity-30 ${
                                  isDark
                                    ? 'text-slate-300 hover:bg-white/10 hover:text-blue-300'
                                    : 'text-slate-600 hover:bg-blue-50 hover:text-blue-600'
                                }`}
                              >
                                <Pencil size={16} />
                              </button>

                              {/* LOCK / UNLOCK */}

                              <button
                                type="button"
                                title={
                                  account.status === 'LOCKED'
                                    ? 'Mở khóa'
                                    : 'Khóa'
                                }
                                disabled={
                                  busyId === account.accountId ||
                                  !['ACTIVE', 'LOCKED'].includes(account.status)
                                }
                                onClick={() =>
                                  void runAction(
                                    account,
                                    account.status === 'LOCKED'
                                      ? 'unlock'
                                      : 'lock',
                                  )
                                }
                                className="rounded-lg p-2 text-amber-500 transition hover:bg-amber-500/10 disabled:cursor-not-allowed disabled:opacity-30"
                              >
                                {account.status === 'LOCKED' ? (
                                  <Unlock size={16} />
                                ) : (
                                  <Lock size={16} />
                                )}
                              </button>

                              {/* DELETE */}

                              <button
                                type="button"
                                title="Xóa tài khoản"
                                disabled={busyId === account.accountId}
                                onClick={() =>
                                  void runAction(account, 'delete')
                                }
                                className="rounded-lg p-2 text-rose-500 transition hover:bg-rose-500/10 disabled:cursor-not-allowed disabled:opacity-30"
                              >
                                <Trash2 size={16} />
                              </button>
                            </>
                          )}
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {/* TABLE FOOTER */}

        <div
          className={`flex flex-col gap-2 border-t px-5 py-3 text-xs sm:flex-row sm:items-center sm:justify-between ${
            isDark
              ? 'border-white/10 text-slate-500'
              : 'border-slate-200 text-slate-500'
          }`}
        >
          <span>
            Hiển thị {visible.length} / {accounts.length} tài khoản
          </span>

          <span>Quản lý quyền bởi ADMIN</span>
        </div>
      </section>

      {/* ACCOUNT DETAIL MODAL */}

      {selected && (
        <div
          className="fixed inset-0 z-[100] flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm"
          onMouseDown={(e) => {
            if (e.target === e.currentTarget) {
              setSelected(null);
            }
          }}
        >
          <div
            className={`w-full max-w-lg rounded-2xl border p-6 shadow-2xl ${modal}`}
          >

            {/* MODAL HEADER */}

            <div className="flex items-start justify-between">
              <div>
                <p className="text-xs uppercase tracking-widest text-blue-500 dark:text-blue-300">
                  Account details
                </p>

                <h3 className="mt-1 text-xl font-bold">
                  {selected.username}
                </h3>

                <p className={`text-sm ${textSecondary}`}>
                  {selected.email}
                </p>
              </div>

              <button
                type="button"
                onClick={() => setSelected(null)}
                className={`rounded-lg p-2 transition ${
                  isDark ? 'hover:bg-white/10' : 'hover:bg-slate-100'
                }`}
                title="Đóng"
              >
                <X size={18} />
              </button>
            </div>

            {/* ACCOUNT INFORMATION */}

            <div className="mt-5 grid grid-cols-1 gap-3 text-sm sm:grid-cols-2">
              {[
                ['Account ID', selected.accountId],
                ['Vai trò', selected.role],
                ['Trạng thái', statusLabel[selected.status]],
                [
                  'Email xác thực',
                  selected.emailVerified
                    ? 'Đã xác thực'
                    : 'Chưa xác thực',
                ],
                ['Ngày tạo', dateText(selected.createAt)],
                ['Cập nhật gần nhất', dateText(selected.updateAt)],
                ['Đăng nhập gần nhất', dateText(selected.lastLoginAt)],
              ].map(([label, value]) => (
                <div
                  key={String(label)}
                  className={`rounded-xl p-3 ${modalItem}`}
                >
                  <div className={`text-xs ${textSecondary}`}>
                    {label}
                  </div>

                  <div className="mt-1 break-words font-medium">
                    {value ?? '—'}
                  </div>
                </div>
              ))}
            </div>

            {/* ROLE MANAGEMENT */}

            {isAdmin &&
              selected.username !== session?.username && (
                <div
                  className={`mt-5 border-t pt-5 ${
                    isDark ? 'border-white/10' : 'border-slate-200'
                  }`}
                >
                  <label className={`text-xs ${textSecondary}`}>
                    Thay đổi vai trò tài khoản
                  </label>

                  <div className="mt-2 flex gap-2">
                    {(['ADMIN', 'MONITOR'] as AccountRole[]).map((role) => (
                      <button
                        key={role}
                        type="button"
                        disabled={
                          busyId === selected.accountId ||
                          selected.role === role
                        }
                        onClick={() => void runAction(selected, role)}
                        className={`flex-1 rounded-xl border px-3 py-2 text-sm transition disabled:cursor-not-allowed disabled:opacity-40 ${
                          isDark
                            ? 'border-white/10 bg-white/5 hover:bg-blue-500/20'
                            : 'border-slate-200 bg-slate-50 text-slate-700 hover:bg-blue-50 hover:text-blue-700'
                        }`}
                      >
                        {busyId === selected.accountId &&
                        selected.role !== role
                          ? 'Đang xử lý...'
                          : `Đặt ${role}`}
                      </button>
                    ))}
                  </div>
                </div>
              )}

            {/* MODAL FOOTER */}

            <div className="mt-5 flex justify-end gap-2">
              {isAdmin &&
                selected.username !== session?.username && (
                  <button
                    type="button"
                    onClick={() => openEditForm(selected)}
                    className={`inline-flex items-center gap-2 rounded-xl px-4 py-2 text-sm font-medium transition ${buttonPrimary}`}
                  >
                    <Pencil size={15} />
                    Chỉnh sửa
                  </button>
                )}

              <button
                type="button"
                onClick={() => setSelected(null)}
                className={`rounded-xl border px-4 py-2 text-sm transition ${buttonSecondary}`}
              >
                Đóng
              </button>
            </div>
          </div>
        </div>
      )}

      {/* CREATE / EDIT ACCOUNT MODAL */}

      {formMode && isAdmin && (
        <div
          className="fixed inset-0 z-[110] flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm"
          onMouseDown={(e) => {
            if (e.target === e.currentTarget) {
              closeForm();
            }
          }}
        >
          <form
            onSubmit={(e) => void handleSubmitForm(e)}
            noValidate
            className={`w-full max-w-md rounded-2xl border p-6 shadow-2xl ${modal}`}
          >

            {/* FORM HEADER */}

            <div className="flex items-start justify-between">
              <div>
                <p className="text-xs uppercase tracking-widest text-blue-500 dark:text-blue-300">
                  {editingAccount ? 'Update account' : 'New account'}
                </p>

                <h3 className="mt-1 text-xl font-bold">
                  {editingAccount
                    ? 'Chỉnh sửa tài khoản'
                    : 'Tạo tài khoản mới'}
                </h3>

                {editingAccount && (
                  <p className={`text-sm ${textSecondary}`}>
                    ID #{editingAccount.accountId}
                  </p>
                )}
              </div>

              <button
                type="button"
                onClick={closeForm}
                disabled={submitting}
                className={`rounded-lg p-2 transition disabled:opacity-40 ${
                  isDark ? 'hover:bg-white/10' : 'hover:bg-slate-100'
                }`}
                title="Đóng"
              >
                <X size={18} />
              </button>
            </div>

            {/* FORM ERROR */}

            {formError && (
              <div
                className={`mt-4 flex items-start gap-2 rounded-xl border p-3 text-sm ${
                  isDark
                    ? 'border-rose-400/30 bg-rose-400/10 text-rose-200'
                    : 'border-rose-200 bg-rose-50 text-rose-700'
                }`}
              >
                <CircleAlert size={17} className="mt-0.5 shrink-0" />
                <span>{formError}</span>
              </div>
            )}

            {/* FORM FIELDS */}

            <div className="mt-5 space-y-4">

              {/* USERNAME */}

              <div>
                <label className={`text-xs ${textSecondary}`}>
                  Username
                </label>

                <input
                  type="text"
                  className={`${input} mt-1`}
                  placeholder="monitor01"
                  autoComplete="off"
                  maxLength={50}
                  value={formUsername}
                  onChange={(e) => setFormUsername(e.target.value)}
                  disabled={submitting}
                />
              </div>

              {/* EMAIL */}

              <div>
                <label className={`text-xs ${textSecondary}`}>
                  Email
                </label>

                <input
                  type="email"
                  className={`${input} mt-1`}
                  placeholder="monitor01@gmail.com"
                  autoComplete="off"
                  maxLength={255}
                  value={formEmail}
                  onChange={(e) => setFormEmail(e.target.value)}
                  disabled={submitting}
                />

              </div>

              {/* PASSWORD */}

              <div>
                <label className={`text-xs ${textSecondary}`}>
                  {editingAccount
                    ? 'Mật khẩu mới (để trống nếu không đổi)'
                    : 'Mật khẩu'}
                </label>

                <div className="relative mt-1">
                  <input
                    type={showPassword ? 'text' : 'password'}
                    className={`${input} pr-10`}
                    placeholder={
                      editingAccount ? 'Giữ nguyên mật khẩu hiện tại' : '••••••••'
                    }
                    autoComplete="new-password"
                    maxLength={64}
                    value={formPassword}
                    onChange={(e) => setFormPassword(e.target.value)}
                    disabled={submitting}
                  />

                  <button
                    type="button"
                    tabIndex={-1}
                    onClick={() => setShowPassword((v) => !v)}
                    className="absolute right-2 top-2 rounded p-1 text-slate-400 transition hover:text-blue-500"
                    title={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                  >
                    {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                </div>

                <p className={`mt-1.5 text-xs ${textSecondary}`}>
                  8–64 ký tự, có ít nhất một chữ cái và một chữ số, không dấu,
                  không khoảng trắng.
                </p>
              </div>

              {/* ROLE */}

              <div>
                <label className={`text-xs ${textSecondary}`}>
                  Vai trò
                </label>

                <div className="mt-1 flex gap-2">
                  {(['ADMIN', 'MONITOR'] as AccountRole[]).map((role) => (
                    <button
                      key={role}
                      type="button"
                      disabled={submitting}
                      onClick={() => setFormRole(role)}
                      className={`flex-1 rounded-xl border px-3 py-2 text-sm transition disabled:cursor-not-allowed disabled:opacity-50 ${
                        formRole === role
                          ? 'border-blue-500 bg-blue-500/15 font-semibold text-blue-600 dark:text-blue-300'
                          : isDark
                            ? 'border-white/10 bg-white/5 hover:bg-white/10'
                            : 'border-slate-200 bg-slate-50 text-slate-700 hover:bg-slate-100'
                      }`}
                    >
                      {role}
                    </button>
                  ))}
                </div>
              </div>

              {/* NOTE KHI TẠO MỚI */}

              {!editingAccount && (
                <p className={`text-xs ${textSecondary}`}>
                  Tài khoản được kích hoạt ngay, không cần xác thực OTP.
                  Hãy kiểm tra kỹ email vì người dùng cần email này để khôi
                  phục mật khẩu.
                </p>
              )}
            </div>

            {/* FORM FOOTER */}

            <div className="mt-6 flex justify-end gap-2">
              <button
                type="button"
                onClick={closeForm}
                disabled={submitting}
                className={`rounded-xl border px-4 py-2 text-sm transition disabled:cursor-not-allowed disabled:opacity-50 ${buttonSecondary}`}
              >
                Hủy
              </button>

              <button
                type="submit"
                disabled={submitting}
                className={`inline-flex items-center gap-2 rounded-xl px-4 py-2 text-sm font-medium transition ${buttonPrimary}`}
              >
                {submitting && (
                  <RefreshCw size={15} className="animate-spin" />
                )}
                {submitting
                  ? 'Đang xử lý...'
                  : editingAccount
                    ? 'Lưu thay đổi'
                    : 'Tạo tài khoản'}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}