import React, { useEffect, useState } from 'react';
import { 
  Cpu, 
  Server, 
  ShieldCheck, 
  Lock, 
  Mail, 
  User, 
  ArrowRight, 
  Activity, 
  KeyRound,
  CheckCircle2,
  ArrowLeft,
  Zap,
  Sun,
  Moon,
  HelpCircle,
  Monitor,
  Layers,
  Eye,
  EyeOff,
  Database,
  Network,
  ShieldAlert
} from 'lucide-react';

type AuthView = 'login' | 'register' | 'verify' | 'forgot' | 'reset';
type ThemeMode = 'dark' | 'light';

export interface AuthPageProps {
  onLogin?: (credentials: { identifier: string; password: string; remember?: boolean }) => Promise<void> | void;
  onRegister?: (data: { name: string; email: string; password: string }) => Promise<void> | void;
  onForgotPassword?: (email: string) => Promise<void> | void;
  onResetPassword?: (data: { email: string; code: string; newPassword: string }) => Promise<void> | void;
  onVerifyEmail?: (data: { email: string; code: string }) => Promise<void> | void;
  onResendVerification?: (email: string) => Promise<void> | void;
}

export default function LoginPage({
  onLogin,
  onRegister,
  onForgotPassword,
  onResetPassword,
  onVerifyEmail,
  onResendVerification,
}: AuthPageProps) {
  const [view, setView] = useState<AuthView>('login');
  const [theme, setTheme] = useState<ThemeMode>('dark');
  
  // Form states
  const [identifier, setIdentifier] = useState(''); // Username hoặc Email đăng nhập / Quên mật khẩu
  const [emailForReset, setEmailForReset] = useState(''); // Lưu email nhận OTP cho bước reset
  const [pendingEmail, setPendingEmail] = useState(''); // Email đang chờ xác thực OTP sau đăng ký
  const [resendCooldown, setResendCooldown] = useState(0); // Giây chờ trước khi được gửi lại mã
  const [password, setPassword] = useState('');
  const [name, setName] = useState('');
  const [otpCode, setOtpCode] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [remember, setRemember] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
  
  // Trạng thái thông báo
  const [successMessage, setSuccessMessage] = useState('');
  const [errorMessage, setErrorMessage] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  // Đếm ngược thời gian chờ gửi lại mã (backend giới hạn 60 giây / lần)
  useEffect(() => {
    if (resendCooldown <= 0) return;
    const timer = setTimeout(() => setResendCooldown((c) => c - 1), 1000);
    return () => clearTimeout(timer);
  }, [resendCooldown]);

  // Gửi lại OTP (xác thực email hoặc đặt lại mật khẩu)
  const handleResend = async () => {
    setSuccessMessage('');
    setErrorMessage('');

    const target = view === 'verify' ? pendingEmail.trim() : emailForReset;
    if (!target) {
      setErrorMessage('Vui lòng nhập email.');
      return;
    }

    setIsLoading(true);
    try {
      if (view === 'verify') {
        if (onResendVerification) await onResendVerification(target);
      } else {
        if (onForgotPassword) await onForgotPassword(target);
      }
      setSuccessMessage('Nếu email hợp lệ, mã OTP mới đã được gửi. Vui lòng kiểm tra cả mục Spam.');
      setResendCooldown(60);
    } catch (err: any) {
      setErrorMessage(err?.message || 'Không thể gửi lại mã, vui lòng thử lại.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSuccessMessage('');
    setErrorMessage('');
    setIsLoading(true);

    try {
      if (view === 'login') {
        if (onLogin) await onLogin({ identifier, password, remember });
      } else if (view === 'register') {
        if (onRegister) await onRegister({ name, email: identifier, password });
        // Tài khoản ở trạng thái PENDING cho tới khi nhập đúng OTP gửi qua email
        setPendingEmail(identifier.trim());
        setOtpCode('');
        setResendCooldown(60);
        setView('verify');
        setSuccessMessage('Đăng ký thành công! Mã OTP xác thực đã được gửi tới email của bạn. Vui lòng kiểm tra cả mục Spam.');
      } else if (view === 'verify') {
        if (onVerifyEmail) await onVerifyEmail({ email: pendingEmail.trim(), code: otpCode });
        setSuccessMessage('Xác thực email thành công! Tài khoản đã được kích hoạt, vui lòng đăng nhập.');
        setTimeout(() => {
          setView('login');
          setIdentifier(pendingEmail.trim());
          setSuccessMessage('');
          setOtpCode('');
          setPassword('');
        }, 2500);
      } else if (view === 'forgot') {
        if (onForgotPassword) await onForgotPassword(identifier);
        setEmailForReset(identifier.trim()); // Lưu lại email để chuyển sang bước nhập OTP
        setResendCooldown(60);
        setSuccessMessage('Nếu email tồn tại trong hệ thống, mã OTP đã được gửi. Vui lòng kiểm tra cả mục Spam.');
        setTimeout(() => {
          setView('reset');
          setSuccessMessage('');
        }, 2000);
      } else if (view === 'reset') {
        if (onResetPassword) await onResetPassword({ email: emailForReset, code: otpCode, newPassword });
        setSuccessMessage('Đặt lại mật khẩu thành công! Vui lòng đăng nhập lại.');
        setTimeout(() => {
          setView('login');
          setSuccessMessage('');
          setPassword('');
          setOtpCode('');
          setNewPassword('');
        }, 3000);
      }
    } catch (err: any) {
      setErrorMessage(err?.message || 'Đã có lỗi xảy ra, vui lòng thử lại.');
    } finally {
      setIsLoading(false);
    }
  };

  const isDark = theme === 'dark';

  return (
    <div className={`min-h-screen w-full transition-colors duration-700 flex items-center justify-center relative overflow-hidden font-sans p-4 lg:p-8 ${
      isDark ? 'bg-[#050B14] text-slate-100' : 'bg-slate-100 text-slate-900'
    }`}>
      
      {/* HIỆU ỨNG ÁNH SÁNG NỀN & ICON MẠNG LƯỚI */}
      <div className={`absolute top-0 left-1/4 w-[700px] h-[500px] rounded-full blur-[150px] pointer-events-none transition-all duration-700 ${
        isDark ? 'bg-blue-600/15' : 'bg-blue-400/20'
      }`} />
      <div className={`absolute bottom-0 right-1/4 w-[700px] h-[500px] rounded-full blur-[150px] pointer-events-none transition-all duration-700 ${
        isDark ? 'bg-cyan-600/10' : 'bg-cyan-400/20'
      }`} />

      {/* Các Icon trôi nổi */}
      <div className="absolute top-12 left-10 text-blue-500/15 animate-bounce duration-1000 hidden xl:block pointer-events-none">
        <Server className="w-14 h-14" />
      </div>
      <div className="absolute bottom-16 left-16 text-cyan-500/15 hidden xl:block pointer-events-none animate-pulse">
        <Network className="w-16 h-16" />
      </div>
      <div className="absolute top-24 right-16 text-indigo-500/15 hidden xl:block pointer-events-none">
        <Cpu className="w-16 h-16" />
      </div>
      <div className="absolute bottom-20 right-24 text-emerald-500/15 hidden xl:block pointer-events-none">
        <Database className="w-14 h-14" />
      </div>
      
      {/* CONTAINER CHÍNH */}
      <div className="container mx-auto z-10 max-w-7xl">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-center">
          
          {/* CỘT TRÁI: BRANDING & MINH HỌA */}
          <div className="lg:col-span-7 flex flex-col justify-between py-6 lg:pr-6">
            <div className="flex items-center space-x-3 mb-8">
              <div className="w-12 h-12 rounded-2xl bg-gradient-to-tr from-blue-600 to-cyan-400 flex items-center justify-center shadow-lg shadow-blue-500/30 ring-2 ring-cyan-400/30">
                <Cpu className="w-6 h-6 text-white animate-pulse" />
              </div>
              <div>
                <h1 className={`text-xl font-black tracking-wider flex items-center gap-2 ${isDark ? 'text-white' : 'text-slate-900'}`}>
                  Central<span className="text-cyan-500 font-bold">PC</span>
                </h1>
                <p className="text-[10px] text-slate-400 font-mono tracking-widest uppercase">HỆ THỐNG QUẢN LÝ MÁY TÍNH TẬP TRUNG</p>
              </div>
            </div>

            <div className="mb-8">
              <h2 className={`text-4xl lg:text-5xl font-extrabold tracking-tight mb-4 leading-tight ${isDark ? 'text-white' : 'text-slate-900'}`}>
                Hệ Thống Quản Lý <br />
                <span className="bg-gradient-to-r from-blue-500 to-cyan-400 bg-clip-text text-transparent">
                  Máy Tính Tập Trung
                </span>
              </h2>
              <p className={`text-sm lg:text-base leading-relaxed max-w-xl ${isDark ? 'text-slate-400' : 'text-slate-600'}`}>
                Giám sát, quản lý và điều khiển hệ thống máy tính từ xa một cách hiệu quả, an toàn và linh hoạt.
              </p>
            </div>

            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-10">
              {[
                { icon: <Activity className="w-5 h-5 text-blue-500" />, title: 'Giám sát thời gian thực' },
                { icon: <Layers className="w-5 h-5 text-cyan-500" />, title: 'Quản lý tập trung' },
                { icon: <ShieldCheck className="w-5 h-5 text-indigo-500" />, title: 'Bảo mật vượt trội' },
                { icon: <Zap className="w-5 h-5 text-amber-500" />, title: 'Truy cập mọi nơi' },
              ].map((item, index) => (
                <div key={index} className={`backdrop-blur-md p-3.5 rounded-2xl border flex flex-col items-center text-center shadow-md transition-all duration-300 hover:scale-105 ${
                  isDark ? 'bg-slate-900/60 border-slate-800/80 text-slate-200' : 'bg-white/80 border-slate-200 text-slate-700'
                }`}>
                  <div className="w-9 h-9 rounded-xl bg-blue-600/10 flex items-center justify-center mb-2">
                    {item.icon}
                  </div>
                  <span className="text-xs font-medium">{item.title}</span>
                </div>
              ))}
            </div>

            <div className={`relative rounded-3xl border p-6 backdrop-blur-sm hidden sm:block overflow-hidden shadow-2xl transition-colors duration-700 ${
              isDark ? 'bg-gradient-to-b from-blue-950/20 to-slate-900/40 border-blue-900/30' : 'bg-gradient-to-b from-blue-50 to-white border-blue-100'
            }`}>
              <div className="flex items-center justify-center space-x-6 relative z-10 py-4">
                <div className="p-4 bg-blue-600/20 border border-blue-500/40 rounded-2xl shadow-lg shadow-blue-500/20 animate-pulse">
                  <Server className="w-10 h-10 text-cyan-400" />
                </div>
                <div className="flex-1 h-0.5 bg-gradient-to-r from-blue-500/50 via-cyan-400 to-blue-500/50 relative">
                  <div className="absolute inset-0 bg-cyan-400 blur-sm animate-pulse" />
                </div>
                <div className="grid grid-cols-3 gap-3">
                  {['text-blue-500', 'text-cyan-500', 'text-emerald-500'].map((color, idx) => (
                    <div key={idx} className={`p-3 border rounded-xl flex items-center justify-center shadow transition-transform hover:scale-110 ${
                      isDark ? 'bg-slate-800/80 border-slate-700' : 'bg-white border-slate-200'
                    }`}>
                      <Monitor className={`w-6 h-6 ${color}`} />
                    </div>
                  ))}
                </div>
              </div>
            </div>

            <div className="text-xs text-slate-500 mt-6 font-mono">
              © 2026 CentralPC. Phát triển bởi đội ngũ công nghệ.
            </div>
          </div>

          {/* CỘT PHẢI: FORM AUTHENTICATION */}
          <div className="lg:col-span-5">
            <div className={`backdrop-blur-2xl border rounded-3xl shadow-2xl p-6 sm:p-8 relative overflow-hidden ring-1 transition-all duration-700 ${
              isDark 
                ? 'bg-[#0B1527]/90 border-slate-800/90 ring-white/5 text-slate-100' 
                : 'bg-white/90 border-slate-200 ring-slate-900/5 text-slate-900'
            }`}>
              
              {/* Nút đổi Theme */}
              <div className={`absolute top-6 right-6 flex items-center space-x-1.5 p-1 rounded-xl border transition-colors ${
                isDark ? 'bg-slate-900/80 border-slate-800' : 'bg-slate-100 border-slate-200'
              }`}>
                <button 
                  type="button" 
                  onClick={() => setTheme('light')}
                  className={`p-1.5 rounded-lg transition-all ${!isDark ? 'bg-white text-blue-600 shadow' : 'text-slate-400 hover:text-white'}`}
                >
                  <Sun className="w-3.5 h-3.5" />
                </button>
                <button 
                  type="button" 
                  onClick={() => setTheme('dark')}
                  className={`p-1.5 rounded-lg transition-all ${isDark ? 'bg-blue-600 text-white shadow' : 'text-slate-500 hover:text-slate-900'}`}
                >
                  <Moon className="w-3.5 h-3.5" />
                </button>
              </div>

              {/* Icon bảo mật */}
              <div className="w-12 h-12 rounded-2xl bg-gradient-to-tr from-blue-600 to-cyan-500 flex items-center justify-center shadow-lg shadow-blue-500/30 mb-6">
                <ShieldCheck className="w-6 h-6 text-white" />
              </div>

              {/* Header form */}
              <div className="mb-6">
                <h3 className={`text-2xl font-bold transition-colors ${isDark ? 'text-white' : 'text-slate-900'}`}>
                  {view === 'login' && 'Đăng Nhập Hệ Thống'}
                  {view === 'register' && 'Đăng Ký Tài Khoản Monitor'}
                  {view === 'verify' && 'Xác Thực Email'}
                  {view === 'forgot' && 'Khôi Phục Mật Khẩu'}
                  {view === 'reset' && 'Xác Nhận Đặt Lại Mật Khẩu'}
                </h3>
                <p className={`text-xs sm:text-sm mt-1 transition-colors ${isDark ? 'text-slate-400' : 'text-slate-600'}`}>
                  {view === 'login' && 'Vui lòng đăng nhập để tiếp tục sử dụng hệ thống'}
                  {view === 'register' && 'Tạo tài khoản mới với quyền giám sát (Monitor).'}
                  {view === 'verify' && 'Nhập mã OTP 6 số đã gửi tới email để kích hoạt tài khoản.'}
                  {view === 'forgot' && 'Nhập email đã đăng ký để nhận mã OTP khôi phục.'}
                  {view === 'reset' && `Nhập mã OTP 6 số đã gửi tới ${emailForReset} và mật khẩu mới.`}
                </p>
              </div>

              {/* Tab Toggle Login / Register */}
              {(view === 'login' || view === 'register') && (
                <div className={`p-1.5 rounded-2xl flex border mb-6 transition-colors ${
                  isDark ? 'bg-slate-950/80 border-slate-800' : 'bg-slate-100 border-slate-200'
                }`}>
                  <button
                    type="button"
                    onClick={() => { setView('login'); setSuccessMessage(''); setErrorMessage(''); }}
                    className={`flex-1 py-2.5 rounded-xl text-xs font-semibold transition-all duration-500 flex items-center justify-center space-x-2 ${
                      view === 'login' 
                        ? 'bg-gradient-to-r from-blue-600 to-cyan-500 text-white shadow-lg shadow-blue-600/30' 
                        : isDark ? 'text-slate-400 hover:text-white' : 'text-slate-600 hover:text-slate-900'
                    }`}
                  >
                    <User className="w-3.5 h-3.5" />
                    <span>Đăng Nhập</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => { setView('register'); setSuccessMessage(''); setErrorMessage(''); }}
                    className={`flex-1 py-2.5 rounded-xl text-xs font-semibold transition-all duration-500 flex items-center justify-center space-x-2 ${
                      view === 'register' 
                        ? 'bg-gradient-to-r from-blue-600 to-cyan-500 text-white shadow-lg shadow-blue-600/30' 
                        : isDark ? 'text-slate-400 hover:text-white' : 'text-slate-600 hover:text-slate-900'
                    }`}
                  >
                    <Cpu className="w-3.5 h-3.5" />
                    <span>Đăng Ký</span>
                  </button>
                </div>
              )}

              {/* Thông báo thành công / lỗi */}
              {successMessage && (
                <div className="mb-5 p-3.5 bg-emerald-950/70 border border-emerald-500/40 rounded-2xl flex items-center space-x-3 text-emerald-400 text-xs sm:text-sm animate-fade-in">
                  <CheckCircle2 className="w-5 h-5 shrink-0" />
                  <span>{successMessage}</span>
                </div>
              )}

              {errorMessage && (
                <div className="mb-5 p-3.5 bg-red-950/70 border border-red-500/40 rounded-2xl flex items-center space-x-3 text-red-400 text-xs sm:text-sm animate-fade-in">
                  <ShieldAlert className="w-5 h-5 shrink-0" />
                  <span>{errorMessage}</span>
                </div>
              )}

              {/* FORM CHÍNH */}
              <form onSubmit={handleSubmit} className="space-y-4">
                
                {/* 1. Form Đăng ký: Họ tên */}
                {view === 'register' && (
                  <div>
                    <label className={`block text-xs font-medium mb-1.5 ${isDark ? 'text-slate-300' : 'text-slate-700'}`}>Username</label>
                    <div className="relative">
                      <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-400">
                        <User className="w-4 h-4" />
                      </div>
                      <input
                        type="text"
                        required
                        value={name}
                        onChange={(e) => setName(e.target.value)}
                        placeholder="Nhập họ tên của bạn..."
                        className={`w-full border rounded-xl px-3.5 py-3 pl-10 text-xs sm:text-sm transition-all focus:outline-none focus:ring-1 focus:ring-cyan-500 ${
                          isDark ? 'bg-slate-950/80 border-slate-800 text-slate-100 placeholder-slate-600' : 'bg-white border-slate-300 text-slate-900 placeholder-slate-400'
                        }`}
                      />
                    </div>
                  </div>
                )}

                {/* 2. Form Đăng nhập hoặc Quên mật khẩu: Nhập Identifier / Email */}
                {(view === 'login' || view === 'register' || view === 'forgot') && (
                  <div>
                    <label className={`block text-xs font-medium mb-1.5 ${isDark ? 'text-slate-300' : 'text-slate-700'}`}>
                      {view === 'register' ? 'Địa chỉ Email' : view === 'forgot' ? 'Email nhận mã OTP' : 'Tên đăng nhập hoặc Email'}
                    </label>
                    <div className="relative">
                      <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-400">
                        <Mail className="w-4 h-4" />
                      </div>
                      <input
                        type={view === 'register' || view === 'forgot' ? 'email' : 'text'}
                        required
                        value={identifier}
                        onChange={(e) => setIdentifier(e.target.value)}
                        placeholder={view === 'forgot' ? 'Nhập email đã đăng ký...' : 'Nhập username hoặc email...'}
                        className={`w-full border rounded-xl px-3.5 py-3 pl-10 text-xs sm:text-sm transition-all focus:outline-none focus:ring-1 focus:ring-cyan-500 ${
                          isDark ? 'bg-slate-950/80 border-slate-800 text-slate-100 placeholder-slate-600' : 'bg-white border-slate-300 text-slate-900 placeholder-slate-400'
                        }`}
                      />
                    </div>
                  </div>
                )}

                {/* 3. Form Đăng nhập hoặc Đăng ký: Mật khẩu */}
                {(view === 'login' || view === 'register') && (
                  <div>
                    <label className={`block text-xs font-medium mb-1.5 ${isDark ? 'text-slate-300' : 'text-slate-700'}`}>Mật khẩu</label>
                    <div className="relative">
                      <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-400">
                        <Lock className="w-4 h-4" />
                      </div>
                      <input
                        type={showPassword ? 'text' : 'password'}
                        required
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        placeholder="Nhập mật khẩu..."
                        className={`w-full border rounded-xl px-3.5 py-3 pl-10 pr-10 text-xs sm:text-sm transition-all focus:outline-none focus:ring-1 focus:ring-cyan-500 ${
                          isDark ? 'bg-slate-950/80 border-slate-800 text-slate-100 placeholder-slate-600' : 'bg-white border-slate-300 text-slate-900 placeholder-slate-400'
                        }`}
                      />
                      <button
                        type="button"
                        onClick={() => setShowPassword(!showPassword)}
                        className="absolute inset-y-0 right-0 pr-3.5 flex items-center text-slate-400 hover:text-slate-200 transition-colors focus:outline-none"
                      >
                        {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                      </button>
                    </div>
                  </div>
                )}

                {/* 4. Form Xác thực OTP (verify) / Đặt lại mật khẩu (reset) */}
                {(view === 'reset' || view === 'verify') && (
                  <>
                    {view === 'verify' && (
                      <div>
                        <label className={`block text-xs font-medium mb-1.5 ${isDark ? 'text-slate-300' : 'text-slate-700'}`}>Email đăng ký</label>
                        <div className="relative">
                          <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-400">
                            <Mail className="w-4 h-4" />
                          </div>
                          <input
                            type="email"
                            required
                            value={pendingEmail}
                            onChange={(e) => setPendingEmail(e.target.value)}
                            placeholder="Nhập email đã đăng ký..."
                            className={`w-full border rounded-xl px-3.5 py-3 pl-10 text-xs sm:text-sm transition-all focus:outline-none focus:ring-1 focus:ring-cyan-500 ${
                            isDark ? 'bg-slate-950/80 border-slate-800 text-slate-100 placeholder-slate-600' : 'bg-white border-slate-300 text-slate-900 placeholder-slate-400'
                          }`}
                          />
                        </div>
                      </div>
                    )}

                    <div>
                      <label className={`block text-xs font-medium mb-1.5 ${isDark ? 'text-slate-300' : 'text-slate-700'}`}>Mã xác thực OTP (6 chữ số)</label>
                      <div className="relative">
                        <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-400">
                          <KeyRound className="w-4 h-4" />
                        </div>
                        <input
                          type="text"
                          required
                          maxLength={6}
                          value={otpCode}
                          onChange={(e) => setOtpCode(e.target.value.replace(/\D/g, ''))}
                          placeholder="Nhập mã 6 số..."
                          className={`w-full border rounded-xl px-3.5 py-3 pl-10 text-xs sm:text-sm tracking-widest font-mono transition-all focus:outline-none focus:ring-1 focus:ring-cyan-500 ${
                            isDark ? 'bg-slate-950/80 border-slate-800 text-slate-100 placeholder-slate-600' : 'bg-white border-slate-300 text-slate-900 placeholder-slate-400'
                          }`}
                        />
                      </div>
                    </div>

                    {view === 'reset' && (
                    <div>
                      <label className={`block text-xs font-medium mb-1.5 ${isDark ? 'text-slate-300' : 'text-slate-700'}`}>Mật khẩu mới</label>
                      <div className="relative">
                        <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-400">
                          <Lock className="w-4 h-4" />
                        </div>
                        <input
                          type={showNewPassword ? 'text' : 'password'}
                          required
                          value={newPassword}
                          onChange={(e) => setNewPassword(e.target.value)}
                          placeholder="Nhập mật khẩu mới..."
                          className={`w-full border rounded-xl px-3.5 py-3 pl-10 pr-10 text-xs sm:text-sm transition-all focus:outline-none focus:ring-1 focus:ring-cyan-500 ${
                            isDark ? 'bg-slate-950/80 border-slate-800 text-slate-100 placeholder-slate-600' : 'bg-white border-slate-300 text-slate-900 placeholder-slate-400'
                          }`}
                        />
                        <button
                          type="button"
                          onClick={() => setShowNewPassword(!showNewPassword)}
                          className="absolute inset-y-0 right-0 pr-3.5 flex items-center text-slate-400 hover:text-slate-200 transition-colors focus:outline-none"
                        >
                          {showNewPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                        </button>
                      </div>
                    </div>
                    )}

                    {/* Nút gửi lại mã (có đếm ngược 60 giây) */}
                    <div className="flex justify-end text-xs">
                      <button
                        type="button"
                        onClick={handleResend}
                        disabled={resendCooldown > 0 || isLoading}
                        className="text-cyan-500 hover:underline focus:outline-none font-medium disabled:opacity-50 disabled:no-underline disabled:cursor-not-allowed"
                      >
                        {resendCooldown > 0 ? `Gửi lại mã sau ${resendCooldown}s` : 'Gửi lại mã'}
                      </button>
                    </div>
                  </>
                )}

                {/* Phân quyền Monitor khi đăng ký */}
                {view === 'register' && (
                  <div className="p-3 bg-blue-950/40 border border-blue-900/60 rounded-xl text-xs text-blue-300 flex items-center space-x-2 animate-fade-in">
                    <KeyRound className="w-4 h-4 shrink-0 text-cyan-400" />
                    <span>Tài khoản mới đăng ký sẽ mang vai trò <b>Monitor</b> hệ thống.</span>
                  </div>
                )}

                {/* Checkbox Ghi nhớ & Quên mật khẩu khi đăng nhập */}
                {view === 'login' && (
                  <div className="flex items-center justify-between text-xs pt-1">
                    <label className={`flex items-center space-x-2 cursor-pointer ${isDark ? 'text-slate-400' : 'text-slate-600'}`}>
                      <input 
                        type="checkbox" 
                        checked={remember}
                        onChange={(e) => setRemember(e.target.checked)}
                        className="rounded border-slate-700 text-blue-600 focus:ring-0 w-4 h-4" 
                      />
                      <span>Ghi nhớ phiên đăng nhập</span>
                    </label>
                    <button 
                      type="button" 
                      onClick={() => { setView('forgot'); setSuccessMessage(''); setErrorMessage(''); }} 
                      className="text-cyan-500 hover:underline focus:outline-none font-medium"
                    >
                      Quên mật khẩu?
                    </button>
                  </div>
                )}

                {view === 'login' && (
                  <div className="text-xs text-center">
                    <button
                      type="button"
                      onClick={() => {
                        setPendingEmail(identifier.includes('@') ? identifier.trim() : '');
                        setOtpCode('');
                        setView('verify');
                        setSuccessMessage('');
                        setErrorMessage('');
                      }}
                      className="text-cyan-500 hover:underline focus:outline-none font-medium"
                    >
                      Chưa xác thực email? Nhập mã OTP
                    </button>
                  </div>
                )}

                {/* Nút Submit chính */}
                <button
                  type="submit"
                  disabled={isLoading}
                  className="w-full mt-2 bg-gradient-to-r from-blue-600 to-cyan-500 text-white font-medium py-3.5 px-4 rounded-xl shadow-lg shadow-blue-600/30 hover:from-blue-500 hover:to-cyan-400 focus:outline-none focus:ring-2 focus:ring-cyan-400 transition-all duration-300 flex items-center justify-center space-x-2 text-sm disabled:opacity-50"
                >
                  <span>
                    {isLoading ? 'Đang xử lý...' : (
                      <>
                        {view === 'login' && 'Truy Cập Hệ Thống'}
                        {view === 'register' && 'Xác Nhận Đăng Ký Monitor'}
                        {view === 'verify' && 'Xác Thực Email'}
                        {view === 'forgot' && 'Gửi Mã OTP Khôi Phục'}
                        {view === 'reset' && 'Xác Nhận Đổi Mật Khẩu'}
                      </>
                    )}
                  </span>
                  {!isLoading && <ArrowRight className="w-4 h-4" />}
                </button>
              </form>

              {/* Đường phân cách */}
              <div className="relative my-6 text-center">
                <div className="absolute inset-0 flex items-center">
                  <div className={`w-full border-t ${isDark ? 'border-slate-800' : 'border-slate-200'}`} />
                </div>
                <span className={`relative px-3 text-[11px] font-mono uppercase tracking-wider ${
                  isDark ? 'bg-[#0B1527] text-slate-500' : 'bg-white text-slate-400'
                }`}>
                  CentralPC Security
                </span>
              </div>

              {/* Footer hỗ trợ / chuyển đổi nhanh */}
              <div className={`pt-4 border-t text-center text-xs flex items-center justify-between transition-colors ${
                isDark ? 'border-slate-800/80 text-slate-400' : 'border-slate-200 text-slate-600'
              }`}>
                <div className="flex items-center space-x-1">
                  <HelpCircle className="w-3.5 h-3.5 text-cyan-500" />
                  <span>Cần hỗ trợ quản trị?</span>
                </div>
                
                {view === 'login' && (
                  <button 
                    onClick={() => { setView('register'); setSuccessMessage(''); setErrorMessage(''); }}
                    className="text-cyan-500 font-semibold hover:underline"
                  >
                    Đăng ký ngay
                  </button>
                )}
                {view === 'register' && (
                  <button 
                    onClick={() => { setView('login'); setSuccessMessage(''); setErrorMessage(''); }}
                    className="text-cyan-500 font-semibold hover:underline"
                  >
                    Đăng nhập
                  </button>
                )}
                {(view === 'forgot' || view === 'reset' || view === 'verify') && (
                  <button 
                    onClick={() => { setView('login'); setSuccessMessage(''); setErrorMessage(''); }}
                    className="text-cyan-500 font-semibold hover:underline flex items-center"
                  >
                    <ArrowLeft className="w-3 h-3 mr-1" /> Quay lại đăng nhập
                  </button>
                )}
              </div>

            </div>
          </div>

        </div>
      </div>
    </div>
  );
}