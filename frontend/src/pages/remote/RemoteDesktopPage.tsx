import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  Activity,
  Camera,
  CheckCircle2,
  Clock3,
  Expand,
  Keyboard,
  Loader2,
  LockKeyhole,
  Monitor,
  MousePointer2,
  RefreshCw,
  Search,
  ShieldAlert,
  Square,
  Wifi,
  XCircle,
} from 'lucide-react';
import { useTheme } from '../../context/ThemeContext';
import {
  getClients,
  getCurrentScreenSharingSession,
  getScreenStreamUrl,
  getSession,
  startScreenSharing,
  stopScreenSharing,
} from '../../lib/api';
import type { ClientInfo, ScreenSharingSession } from '../../lib/api';

type ConnectionState = 'idle' | 'starting' | 'connecting' | 'joined' | 'error';
type AgentControlState = 'unknown' | 'ready' | 'unsupported';

const ACTIVE_SESSION_STATUSES = ['REQUESTED', 'STARTING', 'ACTIVE', 'STOPPING'];

function statusLabel(status?: string): string {
  switch (status) {
    case 'REQUESTED': return 'Đang chờ Agent';
    case 'STARTING': return 'Đang khởi tạo';
    case 'ACTIVE': return 'Đang hoạt động';
    case 'STOPPING': return 'Đang dừng';
    case 'ENDED': return 'Đã kết thúc';
    case 'FAILED': return 'Thất bại';
    default: return 'Chưa kết nối';
  }
}

export default function RemoteDesktopPage() {
  const { isDark } = useTheme();
  const [clients, setClients] = useState<ClientInfo[]>([]);
  const [selectedClientId, setSelectedClientId] = useState('');
  const [session, setSession] = useState<ScreenSharingSession | null>(null);
  const [connectionState, setConnectionState] = useState<ConnectionState>('idle');
  const [controlState, setControlState] = useState<AgentControlState>('unknown');
  const [mouseEnabled, setMouseEnabled] = useState(true);
  const [keyboardEnabled, setKeyboardEnabled] = useState(true);
  const [search, setSearch] = useState('');
  const [loadingClients, setLoadingClients] = useState(true);
  const [pageError, setPageError] = useState('');
  const [streamError, setStreamError] = useState('');
  const [lastFrameUrl, setLastFrameUrl] = useState('');
  const [frameCount, setFrameCount] = useState(0);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [isStopping, setIsStopping] = useState(false);

  const socketRef = useRef<WebSocket | null>(null);
  const viewportRef = useRef<HTMLDivElement | null>(null);
  const imageRef = useRef<HTMLImageElement | null>(null);
  const frameUrlRef = useRef('');
  const lastMouseMoveAtRef = useRef(0);
  const sessionRef = useRef<ScreenSharingSession | null>(null);

  const darkCard = isDark ? 'bg-[#0b1220] border-slate-800' : 'bg-white border-slate-200';
  const muted = isDark ? 'text-slate-400' : 'text-slate-500';
  const mainText = isDark ? 'text-slate-100' : 'text-slate-900';
  const subtlePanel = isDark ? 'bg-slate-900/70 border-slate-800' : 'bg-slate-50 border-slate-200';

  const selectedClient = clients.find((client) => client.clientId === selectedClientId) || null;
  const isSessionActive = !!session && ACTIVE_SESSION_STATUSES.includes(session.status);
  const isStreamJoined = connectionState === 'joined';
  const canControl = isStreamJoined && controlState === 'ready';

  useEffect(() => {
    sessionRef.current = session;
  }, [session]);

  const closeSocket = useCallback(() => {
    const socket = socketRef.current;
    socketRef.current = null;
    if (socket && socket.readyState <= WebSocket.OPEN) socket.close(1000, 'Viewer closed');
  }, []);

  const clearFrame = useCallback(() => {
    if (frameUrlRef.current) URL.revokeObjectURL(frameUrlRef.current);
    frameUrlRef.current = '';
    setLastFrameUrl('');
    setFrameCount(0);
  }, []);

  const loadClients = useCallback(async () => {
    setLoadingClients(true);
    try {
      const result = await getClients();
      setClients(result);
      setSelectedClientId((current) => {
        if (current && result.some((client) => client.clientId === current)) return current;
        const online = result.find((client) => client.status === 'ONLINE');
        return online?.clientId || result[0]?.clientId || '';
      });
      setPageError('');
    } catch (error) {
      setPageError(error instanceof Error ? error.message : 'Không thể tải danh sách máy.');
    } finally {
      setLoadingClients(false);
    }
  }, []);

  const loadCurrentSession = useCallback(async () => {
    try {
      const current = await getCurrentScreenSharingSession();
      if (current && ACTIVE_SESSION_STATUSES.includes(current.status)) {
        setSession(current);
        setSelectedClientId(current.clientId);
        if (current.status !== 'STOPPING' && (!socketRef.current || socketRef.current.readyState > WebSocket.OPEN)) {
          // Reconnect viewer after page refresh or an unexpected socket close.
          openViewerSocket(current);
        }
      } else {
        setSession(null);
      }
    } catch (error) {
      setPageError(error instanceof Error ? error.message : 'Không thể tải phiên chia sẻ hiện tại.');
    }
  // openViewerSocket is intentionally stable through refs and is declared below.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const openViewerSocket = useCallback((activeSession: ScreenSharingSession) => {
    const token = getSession()?.token;
    if (!token) {
      setConnectionState('error');
      setStreamError('Phiên đăng nhập không có JWT. Vui lòng đăng nhập lại.');
      return;
    }

    closeSocket();
    clearFrame();
    setControlState('unknown');
    setStreamError('');
    setConnectionState('connecting');

let socket: WebSocket;

try {
  const wsUrl = getScreenStreamUrl();

  console.log('[SCREEN WS] URL:', wsUrl);
  console.log('[SCREEN WS] Session:', activeSession.sessionCode);

  socket = new WebSocket(wsUrl);
} catch (error) {
  console.error('[SCREEN WS] Không thể tạo WebSocket:', error);

  setConnectionState('error');
  setStreamError('Không thể tạo kết nối WebSocket đến máy chủ.');
  return;
}

    socket.binaryType = 'blob';
    socketRef.current = socket;

socket.onopen = () => {
  console.log('[SCREEN WS] Đã kết nối WebSocket:', socket.url);

  socket.send(JSON.stringify({
    type: 'AUTH',
    role: 'VIEWER',
    token,
    sessionCode: activeSession.sessionCode,
  }));

  console.log('[SCREEN WS] Đã gửi AUTH Viewer');
};

    socket.onmessage = (event: MessageEvent) => {
      if (event.data instanceof Blob) {
        const nextUrl = URL.createObjectURL(event.data);
        if (frameUrlRef.current) URL.revokeObjectURL(frameUrlRef.current);
        frameUrlRef.current = nextUrl;
        setLastFrameUrl(nextUrl);
        setFrameCount((count) => count + 1);
        return;
      }

      if (typeof event.data !== 'string') return;
      try {
        const message = JSON.parse(event.data) as { type?: string; state?: string; message?: string; agentConnected?: boolean };
        switch (message.type) {
          case 'JOINED':
            setConnectionState('joined');
            if (!message.agentConnected) setStreamError('Đã vào phiên. Đang chờ Agent kết nối...');
            break;
          case 'AGENT_CONNECTED':
            setStreamError('');
            break;
          case 'STATUS':
            if (message.state === 'CONTROL_READY') {
              setControlState('ready');
              setStreamError('');
            } else if (message.state === 'CONTROL_UNSUPPORTED') {
              setControlState('unsupported');
              setStreamError(message.message || 'Agent không hỗ trợ điều khiển từ xa trên phiên này.');
            } else if (message.message) {
              setStreamError(message.message);
            }
            break;
          case 'ERROR':
            setStreamError(message.message || 'Máy chủ báo lỗi luồng màn hình.');
            setConnectionState('error');
            break;
          case 'ENDED':
            setStreamError(message.message || 'Phiên chia sẻ đã kết thúc.');
            setConnectionState('idle');
            setSession(null);
            closeSocket();
            break;
          default:
            break;
        }
      } catch {
        // Ignore malformed server events; the next valid frame/event can continue.
      }
    };

socket.onerror = (event) => {
  console.error('[SCREEN WS] WebSocket error:', event);

  setConnectionState('error');
  setStreamError('Lỗi WebSocket. Kiểm tra backend, JWT và cấu hình kết nối.');
};
socket.onclose = (event) => {
  console.warn('[SCREEN WS] Đã đóng kết nối:', {
    code: event.code,
    reason: event.reason,
    wasClean: event.wasClean,
  });

  if (socketRef.current === socket) {
    socketRef.current = null;
  }

  if (sessionRef.current && ACTIVE_SESSION_STATUSES.includes(sessionRef.current.status)) {
    setConnectionState((current) => current === 'error' ? current : 'idle');
  }
};
  }, [clearFrame, closeSocket]);

  useEffect(() => {
    void loadClients();
    void loadCurrentSession();
    const timer = window.setInterval(() => {
      void loadCurrentSession();
    }, 4000);
    return () => window.clearInterval(timer);
  // Initial load + backend session polling.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // When backend session becomes ACTIVE after Agent acknowledges START, keep the viewer socket.
  useEffect(() => {
    if (!session) return;
    if (session.status === 'ENDED' || session.status === 'FAILED') {
      closeSocket();
      setConnectionState(session.status === 'FAILED' ? 'error' : 'idle');
      if (session.status === 'FAILED') setStreamError('Agent không thể khởi tạo chia sẻ màn hình.');
    }
    if (session.status === 'STOPPING') setIsStopping(true);
    else setIsStopping(false);
  }, [session, closeSocket]);


  useEffect(() => {
    const onFullscreen = () => setIsFullscreen(!!document.fullscreenElement);
    document.addEventListener('fullscreenchange', onFullscreen);
    return () => document.removeEventListener('fullscreenchange', onFullscreen);
  }, []);

  useEffect(() => {
    return () => {
      const socket = socketRef.current;
      if (socket && socket.readyState <= WebSocket.OPEN) socket.close(1000, 'Page unmounted');
      if (frameUrlRef.current) URL.revokeObjectURL(frameUrlRef.current);
    };
  }, []);

  const handleStart = async () => {
    if (!selectedClient || selectedClient.status !== 'ONLINE' || isSessionActive) return;
    setPageError('');
    setStreamError('');
    setConnectionState('starting');
    setControlState('unknown');
    try {
      const created = await startScreenSharing(selectedClient.clientId);
      setSession(created);
      setSelectedClientId(created.clientId);
      openViewerSocket(created);
    } catch (error) {
      setConnectionState('error');
      setPageError(error instanceof Error ? error.message : 'Không thể bắt đầu chia sẻ màn hình.');
    }
  };

  const handleStop = async () => {
    if (!session || isStopping) return;
    setIsStopping(true);
    setPageError('');
    try {
      const updated = await stopScreenSharing(session.sessionCode);
      setSession(updated);
      // Keep viewer connected until backend reports ENDED so the Agent stop command can complete.
    } catch (error) {
      setIsStopping(false);
      setPageError(error instanceof Error ? error.message : 'Không thể dừng phiên chia sẻ.');
    }
  };

  const sendInput = useCallback((payload: Record<string, unknown>) => {
    const socket = socketRef.current;
    if (!socket || socket.readyState !== WebSocket.OPEN || !canControl) return;
    socket.send(JSON.stringify({ type: 'INPUT', ...payload }));
  }, [canControl]);

  const getPoint = (clientX: number, clientY: number) => {
    const bounds = viewportRef.current?.getBoundingClientRect();
    const image = imageRef.current;
    if (!bounds) return { x: 0, y: 0 };
    if (!image?.naturalWidth || !image?.naturalHeight) {
      return {
        x: Math.max(0, Math.min(1, (clientX - bounds.left) / bounds.width)),
        y: Math.max(0, Math.min(1, (clientY - bounds.top) / bounds.height)),
      };
    }
    const scale = Math.min(bounds.width / image.naturalWidth, bounds.height / image.naturalHeight);
    const width = image.naturalWidth * scale;
    const height = image.naturalHeight * scale;
    const left = bounds.left + (bounds.width - width) / 2;
    const top = bounds.top + (bounds.height - height) / 2;
    return {
      x: Math.max(0, Math.min(1, (clientX - left) / width)),
      y: Math.max(0, Math.min(1, (clientY - top) / height)),
    };
  };

  const handleMouseMove = (event: React.MouseEvent<HTMLDivElement>) => {
    if (!mouseEnabled || !canControl) return;
    const now = Date.now();
    if (now - lastMouseMoveAtRef.current < 25) return;
    lastMouseMoveAtRef.current = now;
    const point = getPoint(event.clientX, event.clientY);
    sendInput({ event: 'MOUSE_MOVE', ...point });
  };

  const handlePointerDown = (event: React.PointerEvent<HTMLDivElement>) => {
    if (!mouseEnabled || !canControl) return;
    event.preventDefault();
    event.currentTarget.focus();
    event.currentTarget.setPointerCapture?.(event.pointerId);
    const point = getPoint(event.clientX, event.clientY);
    sendInput({ event: 'MOUSE_DOWN', ...point, button: event.button });
  };

  const handlePointerUp = (event: React.PointerEvent<HTMLDivElement>) => {
    if (!mouseEnabled || !canControl) return;
    const point = getPoint(event.clientX, event.clientY);
    sendInput({ event: 'MOUSE_UP', ...point, button: event.button });
  };

  const handleWheel = (event: React.WheelEvent<HTMLDivElement>) => {
    if (!mouseEnabled || !canControl) return;
    event.preventDefault();
    sendInput({ event: 'MOUSE_WHEEL', delta: Math.max(-20, Math.min(20, Math.round(event.deltaY / 100))) });
  };

  const handleKeyDown = (event: React.KeyboardEvent<HTMLDivElement>) => {
    if (!keyboardEnabled || !canControl || event.repeat) return;
    if (event.key.length === 1 || [' ', 'Tab', 'Backspace', 'ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(event.key)) {
      event.preventDefault();
    }
    sendInput({ event: 'KEY_DOWN', code: event.code });
  };

  const handleKeyUp = (event: React.KeyboardEvent<HTMLDivElement>) => {
    if (!keyboardEnabled || !canControl) return;
    sendInput({ event: 'KEY_UP', code: event.code });
  };

  const handleReleaseAll = () => sendInput({ event: 'RELEASE_ALL' });

  const handleCtrlAltDelete = () => {
    if (!canControl || !keyboardEnabled) return;
    ['ControlLeft', 'AltLeft', 'Delete'].forEach((code) => sendInput({ event: 'KEY_DOWN', code }));
    window.setTimeout(() => {
      ['Delete', 'AltLeft', 'ControlLeft'].forEach((code) => sendInput({ event: 'KEY_UP', code }));
    }, 80);
  };

  const handleScreenshot = () => {
    if (!lastFrameUrl) return;
    const link = document.createElement('a');
    link.href = lastFrameUrl;
    link.download = `screen-${session?.clientId || 'client'}-${Date.now()}.jpg`;
    link.click();
  };

  const toggleFullscreen = () => {
    if (!document.fullscreenElement) void viewportRef.current?.requestFullscreen?.();
    else void document.exitFullscreen?.();
  };

  const handleSelectClient = async (client: ClientInfo) => {
    if (client.status !== 'ONLINE' || client.clientId === selectedClientId) return;
    if (isSessionActive && session) {
      setPageError('Bạn đang có phiên chia sẻ. Hãy dừng phiên hiện tại trước khi chọn máy khác.');
      return;
    }
    setSelectedClientId(client.clientId);
    setPageError('');
  };

  const filteredClients = clients.filter((client) => {
    const query = search.trim().toLowerCase();
    return !query || [client.hostname, client.clientId, client.ipAddress].some((value) => value?.toLowerCase().includes(query));
  });
  const onlineCount = clients.filter((client) => client.status === 'ONLINE').length;
  const statusText = session ? statusLabel(session.status) : statusLabel();

  return (
    <div className="space-y-5 pb-10 animate-fade-in">
      <section className={`relative overflow-hidden rounded-2xl border p-5 lg:p-6 ${isDark ? 'bg-gradient-to-r from-[#0b1728] via-[#10233a] to-[#0b1728] border-slate-800' : 'bg-gradient-to-r from-slate-900 via-blue-950 to-slate-900 border-slate-800'}`}>
        <div className="pointer-events-none absolute -right-16 -top-28 h-72 w-72 rounded-full bg-cyan-500/10 blur-3xl" />
        <div className="relative flex flex-col justify-between gap-5 xl:flex-row xl:items-center">
          <div>
            <div className="mb-2 flex items-center gap-2 text-cyan-300">
              <div className="rounded-xl border border-cyan-400/20 bg-cyan-400/10 p-2"><Monitor className="h-5 w-5" /></div>
              <span className="text-xs font-bold uppercase tracking-[0.18em]">Remote management</span>
            </div>
            <h1 className="text-2xl font-bold tracking-tight text-white sm:text-3xl">Chia sẻ màn hình</h1>
            <p className="mt-1 max-w-2xl text-sm text-slate-300">Theo dõi và điều khiển máy trạm từ xa thông qua Agent và WebSocket bảo mật.</p>
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <div className="rounded-xl border border-white/10 bg-white/5 px-4 py-2.5">
              <p className="text-[10px] uppercase tracking-wider text-slate-400">Máy trực tuyến</p>
              <p className="mt-0.5 text-lg font-bold text-white">{onlineCount}<span className="ml-1 text-xs font-medium text-slate-400">/ {clients.length}</span></p>
            </div>
            {isSessionActive ? (
              <button onClick={handleStop} disabled={isStopping || session?.status === 'STOPPING'} className="inline-flex items-center gap-2 rounded-xl border border-rose-400/30 bg-rose-500/15 px-4 py-3 text-sm font-semibold text-rose-200 transition hover:bg-rose-500/25 disabled:cursor-not-allowed disabled:opacity-50">
                {isStopping ? <Loader2 className="h-4 w-4 animate-spin" /> : <Square className="h-4 w-4" />}
                {isStopping ? 'Đang dừng...' : 'Dừng chia sẻ'}
              </button>
            ) : (
              <button onClick={handleStart} disabled={!selectedClient || selectedClient.status !== 'ONLINE' || loadingClients || connectionState === 'starting'} className="inline-flex items-center gap-2 rounded-xl bg-cyan-400 px-5 py-3 text-sm font-bold text-slate-950 shadow-lg shadow-cyan-950/30 transition hover:bg-cyan-300 disabled:cursor-not-allowed disabled:opacity-50">
                {connectionState === 'starting' ? <Loader2 className="h-4 w-4 animate-spin" /> : <Monitor className="h-4 w-4" />}
                Bắt đầu chia sẻ
              </button>
            )}
          </div>
        </div>
      </section>

      {(pageError || streamError) && (
        <div className={`flex items-start gap-3 rounded-xl border px-4 py-3 text-sm ${isDark ? 'border-amber-500/20 bg-amber-500/10 text-amber-200' : 'border-amber-200 bg-amber-50 text-amber-800'}`}>
          <ShieldAlert className="mt-0.5 h-4 w-4 shrink-0" />
          <div className="min-w-0 flex-1">{pageError || streamError}</div>
          <button onClick={() => { setPageError(''); setStreamError(''); }} aria-label="Đóng thông báo"><XCircle className="h-4 w-4 opacity-70" /></button>
        </div>
      )}

      <div className="grid grid-cols-1 gap-5 2xl:grid-cols-[300px_minmax(0,1fr)]">
        <aside className={`overflow-hidden rounded-2xl border shadow-sm ${darkCard}`}>
          <div className={`border-b p-4 ${isDark ? 'border-slate-800' : 'border-slate-200'}`}>
            <div className="flex items-center justify-between">
              <div><h2 className={`font-bold ${mainText}`}>Máy trạm</h2><p className={`mt-0.5 text-xs ${muted}`}>Chọn máy cần truy cập</p></div>
              <button onClick={() => void loadClients()} disabled={loadingClients} className={`rounded-lg border p-2 ${subtlePanel} ${muted}`} title="Làm mới danh sách"><RefreshCw className={`h-4 w-4 ${loadingClients ? 'animate-spin' : ''}`} /></button>
            </div>
            <div className={`mt-3 flex items-center gap-2 rounded-xl border px-3 ${subtlePanel}`}>
              <Search className={`h-4 w-4 ${muted}`} />
              <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Tìm hostname, IP, Client ID..." className={`h-10 w-full bg-transparent text-xs outline-none ${mainText} placeholder:text-slate-500`} />
            </div>
          </div>
          <div className="max-h-[620px] space-y-1 overflow-y-auto p-2">
            {loadingClients ? (
              <div className={`flex items-center justify-center gap-2 py-10 text-sm ${muted}`}><Loader2 className="h-4 w-4 animate-spin" /> Đang tải máy trạm...</div>
            ) : filteredClients.length === 0 ? (
              <div className={`px-3 py-10 text-center text-sm ${muted}`}>Không tìm thấy máy trạm.</div>
            ) : filteredClients.map((client) => {
              const active = client.clientId === selectedClientId;
              const online = client.status === 'ONLINE';
              return (
                <button key={client.clientId} onClick={() => void handleSelectClient(client)} disabled={!online || isSessionActive} className={`flex w-full items-center gap-3 rounded-xl border p-3 text-left transition ${active ? 'border-cyan-500/40 bg-cyan-500/10' : 'border-transparent'} ${online ? 'hover:bg-slate-500/5' : 'cursor-not-allowed opacity-50'} ${isDark ? 'text-slate-100' : 'text-slate-800'}`}>
                  <div className={`rounded-xl p-2.5 ${active ? 'bg-cyan-500/15 text-cyan-500' : isDark ? 'bg-slate-800 text-slate-400' : 'bg-slate-100 text-slate-500'}`}><Monitor className="h-4 w-4" /></div>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-semibold">{client.hostname || client.clientId}</p>
                    <p className={`mt-0.5 truncate font-mono text-[10px] ${muted}`}>{client.ipAddress || 'Chưa có IP'} · {client.clientId}</p>
                    <p className={`mt-1 truncate text-[10px] ${muted}`}>{client.osName || 'Linux'} {client.osVersion || ''}</p>
                  </div>
                  <span className={`h-2.5 w-2.5 shrink-0 rounded-full ${online ? 'bg-emerald-400 shadow-[0_0_8px_rgba(52,211,153,.65)]' : 'bg-slate-500'}`} title={online ? 'Online' : client.status} />
                </button>
              );
            })}
          </div>
          <div className={`border-t px-4 py-3 text-[11px] ${muted} ${isDark ? 'border-slate-800' : 'border-slate-200'}`}>
            <span className="mr-3 inline-flex items-center gap-1.5"><i className="h-2 w-2 rounded-full bg-emerald-400" /> Online</span>
            <span className="inline-flex items-center gap-1.5"><i className="h-2 w-2 rounded-full bg-slate-500" /> Offline / khác</span>
          </div>
        </aside>

        <main className={`min-w-0 overflow-hidden rounded-2xl border shadow-sm ${darkCard}`}>
          <div className={`flex flex-col justify-between gap-3 border-b px-4 py-3 sm:flex-row sm:items-center ${isDark ? 'border-slate-800 bg-slate-900/50' : 'border-slate-200 bg-slate-50'}`}>
            <div className="flex min-w-0 items-center gap-3">
              <span className={`h-2.5 w-2.5 shrink-0 rounded-full ${connectionState === 'joined' ? 'animate-pulse bg-emerald-400' : connectionState === 'connecting' || connectionState === 'starting' ? 'animate-pulse bg-amber-400' : connectionState === 'error' ? 'bg-rose-500' : 'bg-slate-500'}`} />
              <div className="min-w-0"><p className={`truncate text-sm font-bold ${mainText}`}>{selectedClient?.hostname || selectedClient?.clientId || 'Chưa chọn máy trạm'}</p><p className={`truncate text-[10px] ${muted}`}>{selectedClient?.ipAddress || '—'} · {statusText}</p></div>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <span className={`rounded-lg border px-2.5 py-1.5 text-[11px] ${subtlePanel} ${muted}`}>{controlState === 'ready' ? 'Điều khiển sẵn sàng' : controlState === 'unsupported' ? 'Chỉ xem' : 'Đang kiểm tra điều khiển'}</span>
              <button onClick={handleScreenshot} disabled={!lastFrameUrl} className={`inline-flex items-center gap-1.5 rounded-lg border px-3 py-2 text-xs font-semibold ${subtlePanel} ${mainText} disabled:cursor-not-allowed disabled:opacity-40`}><Camera className="h-3.5 w-3.5" /> Chụp ảnh</button>
              <button onClick={toggleFullscreen} className={`rounded-lg border p-2 ${subtlePanel} ${mainText}`} title="Toàn màn hình"><Expand className="h-4 w-4" /></button>
            </div>
          </div>

          <div ref={viewportRef} tabIndex={0} onMouseMove={handleMouseMove} onPointerDown={handlePointerDown} onPointerUp={handlePointerUp} onWheel={handleWheel} onKeyDown={handleKeyDown} onKeyUp={handleKeyUp} onBlur={handleReleaseAll} onContextMenu={(event) => event.preventDefault()} className={`relative flex h-[420px] w-full items-center justify-center overflow-hidden outline-none focus:ring-2 focus:ring-inset focus:ring-cyan-500/50 sm:h-[540px] xl:h-[min(66vh,720px)] ${isDark ? 'bg-[#030712]' : 'bg-slate-950'} ${canControl && mouseEnabled ? 'cursor-crosshair' : 'cursor-default'} ${isFullscreen ? 'h-screen' : ''}`}>
            {lastFrameUrl ? (
              <img ref={imageRef} src={lastFrameUrl} alt={`Màn hình ${selectedClient?.hostname || selectedClientId}`} draggable={false} className="pointer-events-none max-h-full max-w-full select-none object-contain" />
            ) : (
              <div className="mx-auto flex max-w-md flex-col items-center px-6 text-center">
                {connectionState === 'starting' || connectionState === 'connecting' ? <Loader2 className="h-12 w-12 animate-spin text-cyan-400" /> : connectionState === 'error' ? <XCircle className="h-12 w-12 text-rose-400" /> : <div className="rounded-2xl border border-slate-700 bg-slate-900 p-5"><Monitor className="h-10 w-10 text-slate-500" /></div>}
                <h3 className="mt-5 text-base font-semibold text-slate-200">{connectionState === 'starting' ? 'Đang gửi yêu cầu đến Agent' : connectionState === 'connecting' ? 'Đang kết nối luồng màn hình' : connectionState === 'error' ? 'Không thể kết nối luồng màn hình' : isSessionActive ? 'Đang chờ khung hình từ máy trạm' : 'Chưa có phiên chia sẻ màn hình'}</h3>
                <p className="mt-2 text-xs leading-5 text-slate-500">{connectionState === 'idle' && !isSessionActive ? 'Chọn một máy đang Online ở danh sách bên trái, sau đó nhấn “Bắt đầu chia sẻ”.' : 'Khung hình JPEG sẽ hiển thị tại đây khi Agent bắt đầu truyền dữ liệu.'}</p>
                {connectionState === 'error' && session && <button onClick={() => openViewerSocket(session)} className="mt-4 rounded-lg bg-cyan-500 px-4 py-2 text-xs font-bold text-slate-950 hover:bg-cyan-400">Thử kết nối lại</button>}
              </div>
            )}
            {isStreamJoined && lastFrameUrl && <div className="absolute left-4 top-4 flex items-center gap-2 rounded-full border border-emerald-400/20 bg-slate-950/80 px-3 py-1.5 text-[10px] font-bold tracking-wider text-emerald-300 backdrop-blur"><span className="h-1.5 w-1.5 animate-pulse rounded-full bg-emerald-400" /> LIVE</div>}
            {lastFrameUrl && <div className="absolute bottom-3 right-3 rounded-lg border border-slate-700 bg-slate-950/80 px-2.5 py-1.5 font-mono text-[10px] text-slate-300">JPEG · {frameCount} frames</div>}
          </div>

          <div className={`flex flex-wrap items-center justify-between gap-3 border-t px-4 py-3 ${isDark ? 'border-slate-800 bg-slate-900/60' : 'border-slate-200 bg-slate-50'}`}>
            <div className="flex flex-wrap items-center gap-2">
              <button onClick={() => setMouseEnabled((value) => !value)} disabled={!canControl} className={`inline-flex items-center gap-2 rounded-lg border px-3 py-2 text-xs font-semibold disabled:opacity-40 ${mouseEnabled && canControl ? 'border-cyan-500/30 bg-cyan-500/10 text-cyan-500' : `${subtlePanel} ${muted}`}`}><MousePointer2 className="h-3.5 w-3.5" /> Chuột {mouseEnabled ? 'bật' : 'tắt'}</button>
              <button onClick={() => setKeyboardEnabled((value) => !value)} disabled={!canControl} className={`inline-flex items-center gap-2 rounded-lg border px-3 py-2 text-xs font-semibold disabled:opacity-40 ${keyboardEnabled && canControl ? 'border-cyan-500/30 bg-cyan-500/10 text-cyan-500' : `${subtlePanel} ${muted}`}`}><Keyboard className="h-3.5 w-3.5" /> Bàn phím {keyboardEnabled ? 'bật' : 'tắt'}</button>
              <button onClick={handleCtrlAltDelete} disabled={!canControl || !keyboardEnabled} className={`rounded-lg border px-3 py-2 text-xs font-semibold disabled:cursor-not-allowed disabled:opacity-40 ${subtlePanel} ${mainText}`}>Ctrl + Alt + Del</button>
              <button onClick={handleReleaseAll} disabled={!canControl} className={`rounded-lg border px-3 py-2 text-xs ${subtlePanel} ${muted} disabled:opacity-40`}>Nhả phím</button>
            </div>
            <p className={`text-[10px] ${muted}`}>{canControl ? 'Nhấp vào khung hình để điều khiển · Nhấn Tab để focus khung hình' : 'Điều khiển sẽ khả dụng khi Agent báo CONTROL_READY'}</p>
          </div>

          <div className={`grid grid-cols-2 gap-px border-t sm:grid-cols-4 ${isDark ? 'border-slate-800 bg-slate-800' : 'border-slate-200 bg-slate-200'}`}>
            <Metric icon={Activity} label="Trạng thái" value={session?.status || 'IDLE'} isDark={isDark} />
            <Metric icon={Clock3} label="Phiên chia sẻ" value={isSessionActive ? 'Đang duy trì' : 'Chưa chạy'} isDark={isDark} />
            <Metric icon={Wifi} label="Luồng hình" value={frameCount > 0 ? 'Đang nhận JPEG' : 'Chưa có dữ liệu'} isDark={isDark} />
            <Metric icon={LockKeyhole} label="Điều khiển" value={controlState === 'ready' ? 'Sẵn sàng' : controlState === 'unsupported' ? 'Không hỗ trợ' : 'Chưa xác định'} isDark={isDark} />
          </div>
        </main>
      </div>

      {session && <section className={`rounded-2xl border p-4 ${darkCard}`}>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div><p className={`text-xs font-semibold uppercase tracking-wider ${muted}`}>Thông tin phiên hiện tại</p><p className={`mt-1 font-mono text-xs ${mainText}`}>{session.sessionCode}</p></div>
          <div className="flex items-center gap-2 text-xs"><CheckCircle2 className={`h-4 w-4 ${session.status === 'ACTIVE' ? 'text-emerald-400' : muted}`} /><span className={mainText}>{statusLabel(session.status)}</span></div>
        </div>
        <div className={`mt-3 grid grid-cols-2 gap-3 text-xs sm:grid-cols-4 ${muted}`}>
          <div><p>Client ID</p><p className={`mt-1 font-semibold ${mainText}`}>{session.clientId}</p></div>
          <div><p>Agent ID</p><p className={`mt-1 break-all font-semibold ${mainText}`}>{session.agentId || '—'}</p></div>
          <div><p>Session ID</p><p className={`mt-1 font-semibold ${mainText}`}>{session.sessionId}</p></div>
          <div><p>Command ID</p><p className={`mt-1 font-semibold ${mainText}`}>{session.commandId ?? '—'}</p></div>
        </div>
      </section>}
      <p className={`text-[10px] ${muted}`}>Luồng màn hình hiện tại sử dụng WebSocket nhị phân truyền từng khung JPEG theo backend N1. Không hiển thị FPS, độ trễ hoặc băng thông khi backend chưa cung cấp số liệu thực.</p>
    </div>
  );
}

function Metric({ icon: Icon, label, value, isDark }: { icon: React.ElementType; label: string; value: string; isDark: boolean }) {
  return (
    <div className={`flex items-center gap-3 p-3 ${isDark ? 'bg-[#0b1220]' : 'bg-white'}`}>
      <div className={`rounded-lg p-2 ${isDark ? 'bg-slate-800 text-cyan-400' : 'bg-slate-100 text-cyan-600'}`}><Icon className="h-4 w-4" /></div>
      <div className="min-w-0"><p className={`text-[10px] ${isDark ? 'text-slate-500' : 'text-slate-500'}`}>{label}</p><p className={`mt-0.5 truncate text-xs font-bold ${isDark ? 'text-slate-200' : 'text-slate-800'}`}>{value}</p></div>
    </div>
  );
}