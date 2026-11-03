import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  AlertTriangle,
  CheckCircle2,
  Clock3,
  Database,
  HeartPulse,
  Info,
  Loader2,
  RefreshCw,
  RotateCcw,
  Save,
  ShieldCheck,
  Undo2,
  X,
} from 'lucide-react';
import {
  ApiError,
  SYSTEM_CONFIG_DEFAULTS,
  SYSTEM_CONFIG_LIMITS,
  UNAUTHORIZED_EVENT,
  getSession,
  getSystemConfig,
  hasPermission,
  toDate,
  updateSystemConfig,
} from '../../lib/api';
import type { SystemConfigInfo, UpdateSystemConfigPayload } from '../../lib/api';
import { useTheme } from '../../context/ThemeContext';

// ============================================================
// KIỂU DỮ LIỆU VÀ HẰNG SỐ
// ============================================================

type FieldKey = keyof UpdateSystemConfigPayload;
type FormState = Record<FieldKey, string>;
type FieldErrors = Partial<Record<FieldKey, string>>;

const FIELD_ORDER: FieldKey[] = [
  'collectionIntervalSeconds',
  'heartbeatIntervalSeconds',
  'heartbeatTimeoutSeconds',
];

const FIELD_LABEL: Record<FieldKey, string> = {
  collectionIntervalSeconds: 'Chu kỳ thu thập dữ liệu',
  heartbeatIntervalSeconds: 'Chu kỳ Heartbeat',
  heartbeatTimeoutSeconds: 'Heartbeat Timeout',
};

// ============================================================
// HÀM TIỆN ÍCH
// ============================================================

function toForm(config: UpdateSystemConfigPayload): FormState {
  return {
    collectionIntervalSeconds: String(config.collectionIntervalSeconds),
    heartbeatIntervalSeconds: String(config.heartbeatIntervalSeconds),
    heartbeatTimeoutSeconds: String(config.heartbeatTimeoutSeconds),
  };
}

// Kiểm tra form theo cùng quy tắc với SystemConfigService của backend.
// values chỉ có khi cả ba trường đều hợp lệ.
function validateForm(form: FormState): { errors: FieldErrors; values: UpdateSystemConfigPayload | null } {
  const errors: FieldErrors = {};
  const parsed: Partial<Record<FieldKey, number>> = {};

  for (const key of FIELD_ORDER) {
    const text = form[key].trim();
    const { min, max } = SYSTEM_CONFIG_LIMITS[key];

    if (text === '') {
      errors[key] = 'Không được để trống.';
    } else if (!/^\d+$/.test(text)) {
      errors[key] = 'Nhập một số nguyên dương.';
    } else {
      const value = Number(text);
      if (value < min || value > max) {
        errors[key] = `Phải từ ${min} đến ${max} giây.`;
      } else {
        parsed[key] = value;
      }
    }
  }

  const interval = parsed.heartbeatIntervalSeconds;
  const timeout = parsed.heartbeatTimeoutSeconds;
  if (!errors.heartbeatTimeoutSeconds && interval !== undefined && timeout !== undefined && timeout <= interval) {
    errors.heartbeatTimeoutSeconds = 'Phải lớn hơn Chu kỳ Heartbeat.';
  }

  if (Object.keys(errors).length > 0) {
    return { errors, values: null };
  }

  return {
    errors,
    values: {
      collectionIntervalSeconds: parsed.collectionIntervalSeconds as number,
      heartbeatIntervalSeconds: parsed.heartbeatIntervalSeconds as number,
      heartbeatTimeoutSeconds: parsed.heartbeatTimeoutSeconds as number,
    },
  };
}

function describeError(error: unknown, fallback: string): string {
  if (error instanceof ApiError && error.status === 403) {
    return 'Tài khoản không có quyền SYSTEM_CONFIG để xem hoặc thay đổi cấu hình hệ thống.';
  }
  return error instanceof Error ? error.message : fallback;
}

function formatDateTime(value: Date | null): string {
  if (!value) return '—';
  return value.toLocaleString('vi-VN');
}

// ============================================================
// TRANG CẤU HÌNH HỆ THỐNG
// ============================================================

export default function SystemConfigurationPage() {
  const { isDark } = useTheme();
  const navigate = useNavigate();
  const session = getSession();
  const canEdit = hasPermission(session, 'SYSTEM_CONFIG');

  const [config, setConfig] = useState<SystemConfigInfo | null>(null);
  const [form, setForm] = useState<FormState>({
    collectionIntervalSeconds: '',
    heartbeatIntervalSeconds: '',
    heartbeatTimeoutSeconds: '',
  });

  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  // ----------------------------------------------------------
  // Tải dữ liệu thật từ GET /api/system-config
  // ----------------------------------------------------------
  const load = useCallback(async () => {
    setIsLoading(true);
    try {
      const data = await getSystemConfig();
      setConfig(data);
      setForm(toForm(data));
      setLoadError(null);
      setSaveError(null);
    } catch (error) {
      setLoadError(describeError(error, 'Không thể tải cấu hình hệ thống.'));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

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

  // ----------------------------------------------------------
  // Dữ liệu dẫn xuất
  // ----------------------------------------------------------
  const { errors, values } = useMemo(() => validateForm(form), [form]);

  const isDirty = useMemo(() => {
    if (!config) return false;
    const original = toForm(config);
    return FIELD_ORDER.some((key) => form[key].trim() !== original[key]);
  }, [config, form]);

  const isDefaultForm = useMemo(() => {
    const defaults = toForm(SYSTEM_CONFIG_DEFAULTS);
    return FIELD_ORDER.every((key) => form[key].trim() === defaults[key]);
  }, [form]);

  const canSave = canEdit && config !== null && isDirty && values !== null && !isSaving;

  // Timeout chỉ hơn chu kỳ gửi một chút: Agent trễ một nhịp là bị đánh dấu OFFLINE.
  // Server vẫn cho lưu (chỉ cần timeout > interval) nên đây chỉ là gợi ý.
  const tightTimeout =
    values !== null && values.heartbeatTimeoutSeconds < values.heartbeatIntervalSeconds * 2;

  const updatedAt = config ? toDate(config.updatedAt) : null;
  const neverSaved = config !== null && config.updatedBy === null && updatedAt === null;

  // ----------------------------------------------------------
  // Thao tác
  // ----------------------------------------------------------
  const handleChange = (key: FieldKey, text: string) => {
    setForm((prev) => ({ ...prev, [key]: text }));
    setSaveError(null);
    setNotice(null);
  };

  const handleReset = () => {
    if (!config) return;
    setForm(toForm(config));
    setSaveError(null);
  };

  // Điền giá trị mặc định của server vào form (vẫn cần bấm Lưu để áp dụng)
  const handleDefaults = () => {
    setForm(toForm(SYSTEM_CONFIG_DEFAULTS));
    setSaveError(null);
    setNotice(null);
  };

  const handleSave = async () => {
    if (!canSave || !values) return;

    setIsSaving(true);
    setSaveError(null);
    setNotice(null);

    try {
      const saved = await updateSystemConfig(values);
      setConfig(saved);
      setForm(toForm(saved));
      setNotice('Đã lưu cấu hình hệ thống.');
    } catch (error) {
      setSaveError(describeError(error, 'Không thể lưu cấu hình hệ thống.'));
    } finally {
      setIsSaving(false);
    }
  };

  // ----------------------------------------------------------
  // Style
  // ----------------------------------------------------------
  const pageText = isDark ? 'text-slate-100' : 'text-slate-800';

  const card = isDark
    ? 'rounded-2xl border border-white/10 bg-slate-900/55 p-5 shadow-lg shadow-black/5'
    : 'rounded-2xl border border-slate-200 bg-white p-5 shadow-sm shadow-slate-200/60';

  const headingText = isDark ? 'text-white' : 'text-slate-900';
  const mutedText = isDark ? 'text-slate-400' : 'text-slate-500';

  const innerCard = isDark
    ? 'rounded-xl border border-white/10 bg-slate-950/40 p-4'
    : 'rounded-xl border border-slate-200 bg-slate-50 p-4';

  const infoCard = isDark
    ? 'rounded-xl bg-white/[.035] p-4'
    : 'rounded-xl border border-slate-100 bg-slate-50 p-4';

  const inputBase =
    'min-w-0 flex-1 rounded-lg border px-3 py-2 text-sm font-mono focus:outline-none focus:ring-1 disabled:cursor-not-allowed';
  const inputNormal = isDark
    ? 'border-white/10 bg-slate-900 text-slate-100 focus:ring-blue-400 disabled:text-slate-500'
    : 'border-slate-300 bg-white text-slate-800 focus:ring-blue-500 disabled:bg-slate-100 disabled:text-slate-500';
  const inputInvalid = isDark
    ? 'border-red-400/60 bg-slate-900 text-slate-100 focus:ring-red-400'
    : 'border-red-400 bg-white text-slate-800 focus:ring-red-400';

  const infoBox = isDark
    ? 'flex gap-3 rounded-2xl border border-blue-400/30 bg-blue-400/10 p-4 text-blue-100'
    : 'flex gap-3 rounded-2xl border border-blue-200 bg-blue-50 p-4 text-blue-900';
  const infoDescription = isDark
    ? 'mt-1 text-sm leading-6 text-blue-100/80'
    : 'mt-1 text-sm leading-6 text-blue-800';

  const warnBox = isDark
    ? 'mt-4 flex items-start gap-2 rounded-xl border border-amber-400/30 bg-amber-400/10 p-3 text-xs text-amber-200'
    : 'mt-4 flex items-start gap-2 rounded-xl border border-amber-300 bg-amber-50 p-3 text-xs text-amber-800';

  const secondaryButton = isDark
    ? 'inline-flex items-center gap-2 rounded-lg border border-white/15 px-3 py-2 text-xs font-semibold text-slate-200 hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-40'
    : 'inline-flex items-center gap-2 rounded-lg border border-slate-300 px-3 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-40';

  const fields = [
    {
      label: FIELD_LABEL.collectionIntervalSeconds,
      key: 'collectionIntervalSeconds' as const,
      unit: 'giây',
      icon: Database,
      help: 'Khoảng thời gian Agent gửi dữ liệu giám sát về Server.',
    },
    {
      label: FIELD_LABEL.heartbeatIntervalSeconds,
      key: 'heartbeatIntervalSeconds' as const,
      unit: 'giây',
      icon: HeartPulse,
      help: 'Khoảng thời gian Agent gửi tín hiệu còn hoạt động.',
    },
    {
      label: FIELD_LABEL.heartbeatTimeoutSeconds,
      key: 'heartbeatTimeoutSeconds' as const,
      unit: 'giây',
      icon: Clock3,
      help: 'Thời gian tối đa chờ Heartbeat trước khi xác định Agent mất kết nối.',
    },
  ];

  // ----------------------------------------------------------
  // Giao diện
  // ----------------------------------------------------------
  return (
    <div className={`space-y-6 ${pageText}`}>
      {/* Tiêu đề */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[.22em] text-blue-500">
            <ShieldCheck size={15} />
            Administration
          </div>

          <h1 className={`mt-2 text-2xl font-bold ${headingText}`}>
            Cấu hình hệ thống
          </h1>

          <p className={`mt-1 text-sm ${mutedText}`}>
            Các tham số vận hành của hệ thống giám sát tập trung PBL4.
          </p>
        </div>

        <button
          type="button"
          onClick={() => void load()}
          disabled={isLoading || isSaving}
          className={secondaryButton}
        >
          <RefreshCw size={14} className={isLoading ? 'animate-spin' : ''} />
          Tải lại từ Server
        </button>
      </div>

      {/* Thông báo */}
      {notice && (
        <div
          role="status"
          className="flex items-start gap-3 rounded-2xl border border-emerald-500/30 bg-emerald-500/10 p-4 text-sm text-emerald-500"
        >
          <CheckCircle2 className="mt-0.5 shrink-0" size={18} />
          <p className="flex-1">{notice}</p>
          <button type="button" onClick={() => setNotice(null)} aria-label="Đóng">
            <X size={16} />
          </button>
        </div>
      )}

      {loadError && (
        <div
          role="alert"
          className="flex items-start gap-3 rounded-2xl border border-red-500/30 bg-red-500/10 p-4 text-sm text-red-500"
        >
          <AlertTriangle className="mt-0.5 shrink-0" size={18} />
          <div className="flex-1">
            <p className="font-semibold">Không thể tải cấu hình hệ thống</p>
            <p className="mt-0.5 text-xs opacity-90">{loadError}</p>
          </div>
          <button
            type="button"
            onClick={() => void load()}
            className="text-xs font-bold underline underline-offset-2"
          >
            Thử lại
          </button>
        </div>
      )}

      {/* Phạm vi áp dụng của từng thông số */}
      <div className={infoBox}>
        <Info className="mt-0.5 shrink-0" size={20} />

        <div>
          <div className="font-semibold">Phạm vi áp dụng</div>

          <p className={infoDescription}>
            Heartbeat Timeout được Server áp dụng ngay ở lần kiểm tra Agent kế tiếp (mỗi 5 giây).
            Chu kỳ thu thập dữ liệu và Chu kỳ Heartbeat được lưu trên Server, nhưng hiện
            Server chưa có API để Agent lấy hai giá trị này, nên Agent chưa tự đổi theo.
          </p>
        </div>
      </div>

      {/* Thông số Agent */}
      <section className={card}>
        <div className="mb-5 flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <h2 className={`font-semibold ${headingText}`}>
              Thông số kết nối Agent
            </h2>

            <p className={`mt-1 text-xs ${mutedText}`}>
              Tên trường khớp trực tiếp với entity SystemConfig của Server.
            </p>
          </div>

          {isLoading && (
            <span className={`inline-flex items-center gap-2 text-xs ${mutedText}`}>
              <Loader2 size={14} className="animate-spin text-blue-500" />
              Đang tải cấu hình...
            </span>
          )}
        </div>

        <form
          onSubmit={(event) => {
            event.preventDefault();
            void handleSave();
          }}
          noValidate
        >
          <div className="grid gap-4 lg:grid-cols-3">
            {fields.map(({ label, key, unit, icon: Icon, help }) => {
              const { min, max } = SYSTEM_CONFIG_LIMITS[key];
              // Chỉ báo lỗi khi người dùng đã sửa giá trị
              const showError = isDirty && errors[key];
              const inputId = `config-${key}`;

              return (
                <div key={key} className={innerCard}>
                  <label
                    htmlFor={inputId}
                    className="flex items-center gap-2 text-sm font-medium"
                  >
                    <Icon size={17} className="text-blue-500" />
                    <span className={headingText}>{label}</span>
                  </label>

                  <div className="mt-4 flex items-center gap-2">
                    <input
                      id={inputId}
                      type="text"
                      inputMode="numeric"
                      autoComplete="off"
                      value={form[key]}
                      onChange={(event) => handleChange(key, event.target.value)}
                      disabled={isLoading || !config || !canEdit || isSaving}
                      aria-invalid={showError ? true : undefined}
                      aria-describedby={`${inputId}-help`}
                      className={`${inputBase} ${showError ? inputInvalid : inputNormal}`}
                    />

                    <span className={`text-xs ${mutedText}`}>{unit}</span>
                  </div>

                  {showError && (
                    <p role="alert" className="mt-2 text-xs text-red-500">
                      {errors[key]}
                    </p>
                  )}

                  <p id={`${inputId}-help`} className={`mt-3 text-xs leading-5 ${mutedText}`}>
                    {help} Cho phép từ {min} đến {max} giây. Mặc định: {SYSTEM_CONFIG_DEFAULTS[key]} giây.
                  </p>

                  <p
                    className={`mt-2 font-mono text-[10px] ${
                      isDark ? 'text-slate-600' : 'text-slate-400'
                    }`}
                  >
                    {key}
                  </p>
                </div>
              );
            })}
          </div>

          {tightTimeout && (
            <div role="note" className={warnBox}>
              <AlertTriangle className="mt-0.5 shrink-0" size={14} />
              <p>
                Heartbeat Timeout đang nhỏ hơn 2 lần Chu kỳ Heartbeat. Server vẫn cho lưu, nhưng
                chỉ cần Agent trễ một nhịp là bị đánh dấu mất kết nối. Nên đặt Timeout bằng 2–3 lần chu kỳ.
              </p>
            </div>
          )}

          {saveError && (
            <div
              role="alert"
              className="mt-4 flex items-start gap-2 rounded-xl border border-red-500/30 bg-red-500/10 p-3 text-xs text-red-500"
            >
              <AlertTriangle className="mt-0.5 shrink-0" size={14} />
              <p>{saveError}</p>
            </div>
          )}

          {!canEdit && (
            <p className={`mt-4 text-xs ${mutedText}`}>
              Tài khoản của bạn không có quyền SYSTEM_CONFIG nên không thể thay đổi cấu hình.
            </p>
          )}

          <div className="mt-5 flex flex-wrap items-center gap-3">
            <button
              type="submit"
              disabled={!canSave}
              className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-xs font-bold text-white hover:bg-blue-500 disabled:cursor-not-allowed disabled:opacity-40"
            >
              {isSaving ? <Loader2 size={14} className="animate-spin" /> : <Save size={14} />}
              {isSaving ? 'Đang lưu...' : 'Lưu cấu hình'}
            </button>

            <button
              type="button"
              onClick={handleReset}
              disabled={!isDirty || isSaving}
              className={secondaryButton}
            >
              <RotateCcw size={14} />
              Hoàn tác thay đổi
            </button>

            <button
              type="button"
              onClick={handleDefaults}
              disabled={!canEdit || !config || isDefaultForm || isSaving}
              className={secondaryButton}
            >
              <Undo2 size={14} />
              Đặt về mặc định
            </button>

            {isDirty && !isSaving && (
              <span className={`text-xs ${mutedText}`}>Có thay đổi chưa lưu</span>
            )}
          </div>
        </form>
      </section>

      {/* Thông tin lưu cấu hình */}
      <section className={card}>
        <h2 className={`font-semibold ${headingText}`}>
          Thông tin lưu cấu hình
        </h2>

        <div className="mt-4 grid gap-3 sm:grid-cols-2">
          <div className={infoCard}>
            <div className={`text-xs ${mutedText}`}>
              Bảng dữ liệu
            </div>

            <div className={`mt-1 font-mono text-sm ${headingText}`}>
              system_configs
            </div>
          </div>

          <div className={infoCard}>
            <div className={`text-xs ${mutedText}`}>
              Cập nhật lần cuối
            </div>

            <div className={`mt-1 text-sm ${headingText}`}>
              {isLoading || !config
                ? '—'
                : neverSaved
                  ? 'Chưa từng lưu, đang dùng giá trị mặc định'
                  : `${config.updatedBy ?? 'Không rõ tài khoản'} · ${formatDateTime(updatedAt)}`}
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}