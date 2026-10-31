#!/usr/bin/python3
# -*- coding: utf-8 -*-
"""
PBL4 - Script thu màn hình cho Agent.

Agent (Java) khởi chạy script này, đọc khung hình JPEG từ stdout
và gửi lên Server.

Giao thức:
  stdout : [4 byte độ dài big-endian][dữ liệu JPEG] lặp lại
  stderr : dòng sự kiện   PBL4_EVENT {"type":"STATUS","state":"...","message":"..."}
           (các dòng khác là log thường)
  stdin  : không dùng. Khi stdin đóng (Agent chết) script tự thoát.

Backend thu màn hình:
  portal : xdg-desktop-portal ScreenCast + PipeWire (Wayland, cũng chạy được trên X11 GNOME)
  x11    : GStreamer ximagesrc (phiên X11, không cần xác nhận)
  test   : videotestsrc (hình giả, dùng để kiểm tra đường truyền)
  auto   : x11 nếu phiên là X11, ngược lại portal

Với backend portal, người dùng ở máy Client phải bấm "Share" lần đầu.
Script lưu restore_token để các lần sau không phải xác nhận lại
(nếu Portal hỗ trợ).

Phụ thuộc (Ubuntu):
  sudo apt install python3-dbus python3-gi gir1.2-gstreamer-1.0 \
       gstreamer1.0-pipewire gstreamer1.0-plugins-base \
       gstreamer1.0-plugins-good gstreamer1.0-x
"""

import argparse
import json
import os
import signal
import struct
import sys
import threading
import time


def emit(event_type, **fields):
    """Gửi một sự kiện cho Agent qua stderr."""
    payload = {"type": event_type}
    payload.update(fields)

    try:
        sys.stderr.write(
            "PBL4_EVENT " + json.dumps(payload, ensure_ascii=False) + "\n"
        )
        sys.stderr.flush()
    except Exception:
        pass


def log(message):
    try:
        sys.stderr.write("[capture] " + str(message) + "\n")
        sys.stderr.flush()
    except Exception:
        pass


def fail(message, code=1):
    emit("ERROR", message=message)
    log("LỖI: " + message)
    sys.exit(code)


try:
    import gi

    gi.require_version("Gst", "1.0")

    from gi.repository import GLib, Gst
except Exception as import_error:
    fail(
        "Thiếu thư viện Python GStreamer (python3-gi, "
        "gir1.2-gstreamer-1.0): " + str(import_error),
        2,
    )


PORTAL_BUS_NAME = "org.freedesktop.portal.Desktop"
PORTAL_OBJECT_PATH = "/org/freedesktop/portal/desktop"
SCREENCAST_INTERFACE = "org.freedesktop.portal.ScreenCast"
REQUEST_INTERFACE = "org.freedesktop.portal.Request"
SESSION_INTERFACE = "org.freedesktop.portal.Session"


class Terminated(Exception):
    """Script nhận yêu cầu dừng (SIGTERM hoặc stdin đóng)."""


class State:
    """Trạng thái toàn cục dùng chung giữa các callback."""

    def __init__(self):
        self.loop = None
        self.terminated = False
        self.exit_code = 0
        self.first_frame_sent = False


STATE = State()


def request_stop(exit_code=0):
    """Dừng vòng lặp GLib hiện tại (gọi được từ luồng bất kỳ)."""
    STATE.terminated = True

    if exit_code != 0 and STATE.exit_code == 0:
        STATE.exit_code = exit_code

    loop = STATE.loop

    if loop is not None:
        GLib.idle_add(loop.quit)


def on_sigterm():
    log("Nhận SIGTERM, đang dừng...")
    request_stop(0)
    return False


def install_signal_handlers():
    """Bắt SIGTERM/SIGINT bằng vòng lặp GLib để dừng gọn gàng."""
    try:
        GLib.unix_signal_add(GLib.PRIORITY_HIGH, signal.SIGTERM, on_sigterm)
        GLib.unix_signal_add(GLib.PRIORITY_HIGH, signal.SIGINT, on_sigterm)

    except Exception as error:
        log("Không đăng ký được GLib signal handler: %s" % error)

        signal.signal(signal.SIGTERM, lambda *_: request_stop(0))
        signal.signal(signal.SIGINT, lambda *_: request_stop(0))


def watch_stdin():
    """Thoát khi Agent đóng stdin (Agent chết)."""
    try:
        while sys.stdin.buffer.read(1024):
            pass
    except Exception:
        pass

    log("stdin đã đóng, đang dừng...")
    request_stop(0)


# ======================================================================
# PORTAL (Wayland / PipeWire)
# ======================================================================

class Portal:
    """Quản lý phiên ScreenCast của xdg-desktop-portal."""

    def __init__(self, restore_token_path, approval_timeout):
        import dbus
        import dbus.mainloop.glib

        self.dbus = dbus

        dbus.mainloop.glib.DBusGMainLoop(set_as_default=True)

        self.bus = dbus.SessionBus()

        portal_object = self.bus.get_object(
            PORTAL_BUS_NAME, PORTAL_OBJECT_PATH
        )

        self.screencast = dbus.Interface(
            portal_object, SCREENCAST_INTERFACE
        )

        self.sender = self.bus.get_unique_name()[1:].replace(".", "_")
        self.counter = 0
        self.session_handle = None
        self.pipewire_fd = None
        self.restore_token_path = restore_token_path
        self.approval_timeout = approval_timeout

    def _next_token(self, prefix):
        self.counter += 1
        return "pbl4_%s_%d_%d" % (prefix, os.getpid(), self.counter)

    def _call_request(self, method, args, options, timeout):
        """
        Gọi một phương thức Portal trả về Request và chờ tín hiệu Response.

        Đăng ký nhận tín hiệu TRƯỚC khi gọi để không bỏ lỡ Response
        (đường dẫn Request được suy ra từ handle_token).
        """
        token = options["handle_token"]

        request_path = (
            "/org/freedesktop/portal/desktop/request/%s/%s"
            % (self.sender, token)
        )

        result = {}
        loop = GLib.MainLoop()

        def on_response(response, results):
            if "response" not in result:
                result["response"] = int(response)
                result["results"] = results
                loop.quit()

        matches = [
            self.bus.add_signal_receiver(
                on_response,
                signal_name="Response",
                dbus_interface=REQUEST_INTERFACE,
                path=request_path,
            )
        ]

        timer = {"id": None, "fired": False}

        try:
            returned_path = str(method(*args, options))

            # Portal cũ có thể trả về đường dẫn khác dự đoán
            if returned_path != request_path:
                matches.append(
                    self.bus.add_signal_receiver(
                        on_response,
                        signal_name="Response",
                        dbus_interface=REQUEST_INTERFACE,
                        path=returned_path,
                    )
                )

            def on_timeout():
                timer["fired"] = True
                timer["id"] = None
                result["timeout"] = True
                loop.quit()
                return False

            timer["id"] = GLib.timeout_add_seconds(timeout, on_timeout)

            STATE.loop = loop

            # Chỉ chạy vòng lặp nếu chưa có Response và chưa bị yêu cầu dừng
            # (loop.quit() gọi trước loop.run() sẽ bị mất tác dụng)
            if "response" not in result and not STATE.terminated:
                loop.run()

        finally:
            STATE.loop = None

            if timer["id"] is not None:
                GLib.source_remove(timer["id"])
                timer["id"] = None

            for match in matches:
                try:
                    match.remove()
                except Exception:
                    pass

        if STATE.terminated:
            raise Terminated()

        if result.get("timeout"):
            raise RuntimeError(
                "Hết thời gian chờ người dùng xác nhận chia sẻ màn hình."
            )

        response = result.get("response", 2)

        if response == 1:
            raise RuntimeError(
                "Người dùng ở máy Client đã từ chối chia sẻ màn hình."
            )

        if response != 0:
            raise RuntimeError(
                "Portal không thể khởi tạo phiên chia sẻ màn hình."
            )

        return result["results"]

    def _load_restore_token(self):
        try:
            with open(self.restore_token_path, "r", encoding="utf-8") as f:
                token = f.read().strip()

            return token or None

        except OSError:
            return None

    def _save_restore_token(self, token):
        try:
            directory = os.path.dirname(self.restore_token_path)

            if directory:
                os.makedirs(directory, exist_ok=True)

            fd = os.open(
                self.restore_token_path,
                os.O_WRONLY | os.O_CREAT | os.O_TRUNC,
                0o600,
            )

            with os.fdopen(fd, "w", encoding="utf-8") as f:
                f.write(token)

        except OSError as error:
            log("Không lưu được restore_token: %s" % error)

    def open_stream(self):
        """
        Thực hiện CreateSession -> SelectSources -> Start -> OpenPipeWireRemote.

        Trả về (pipewire_fd, node_id).
        """
        dbus = self.dbus

        # 1. CreateSession
        emit(
            "STATUS",
            state="WAITING_APPROVAL",
            message="Đang yêu cầu Portal khởi tạo phiên chia sẻ màn hình...",
        )

        create_options = dbus.Dictionary(
            {
                "handle_token": dbus.String(self._next_token("create")),
                "session_handle_token": dbus.String(
                    self._next_token("session")
                ),
            },
            signature="sv",
        )

        create_results = self._call_request(
            self.screencast.CreateSession, (), create_options, 30
        )

        self.session_handle = str(create_results["session_handle"])

        # 2. SelectSources
        select_values = {
            "handle_token": dbus.String(self._next_token("select")),
            "types": dbus.UInt32(1),          # 1 = màn hình (monitor)
            "multiple": dbus.Boolean(False),
            "cursor_mode": dbus.UInt32(2),    # 2 = vẽ con trỏ vào hình
            "persist_mode": dbus.UInt32(2),   # nhớ quyền đến khi bị thu hồi
        }

        saved_token = self._load_restore_token()

        if saved_token:
            select_values["restore_token"] = dbus.String(saved_token)

        self._call_request(
            self.screencast.SelectSources,
            (dbus.ObjectPath(self.session_handle),),
            dbus.Dictionary(select_values, signature="sv"),
            30,
        )

        # 3. Start (người dùng có thể phải bấm Share ở máy Client)
        emit(
            "STATUS",
            state="WAITING_APPROVAL",
            message=(
                "Đang chờ người dùng ở máy Client xác nhận chia sẻ màn hình "
                "(bấm Share)..."
            ),
        )

        start_options = dbus.Dictionary(
            {"handle_token": dbus.String(self._next_token("start"))},
            signature="sv",
        )

        start_results = self._call_request(
            self.screencast.Start,
            (dbus.ObjectPath(self.session_handle), dbus.String("")),
            start_options,
            self.approval_timeout,
        )

        streams = start_results.get("streams", [])

        if not streams:
            raise RuntimeError("Portal không trả về luồng PipeWire nào.")

        node_id = int(streams[0][0])

        new_token = start_results.get("restore_token")

        if new_token:
            self._save_restore_token(str(new_token))

        # 4. OpenPipeWireRemote
        remote_fd = self.screencast.OpenPipeWireRemote(
            dbus.ObjectPath(self.session_handle),
            dbus.Dictionary({}, signature="sv"),
        )

        self.pipewire_fd = (
            remote_fd.take() if hasattr(remote_fd, "take") else int(remote_fd)
        )

        log("PipeWire node: %d" % node_id)

        return self.pipewire_fd, node_id

    def close(self):
        """Đóng phiên Portal (tắt biểu tượng đang chia sẻ màn hình)."""
        if self.session_handle:
            try:
                session_object = self.bus.get_object(
                    PORTAL_BUS_NAME, self.session_handle
                )

                self.dbus.Interface(
                    session_object, SESSION_INTERFACE
                ).Close()

                log("Đã đóng phiên Portal.")

            except Exception as error:
                log("Lỗi đóng phiên Portal: %s" % error)

            self.session_handle = None

        if self.pipewire_fd is not None:
            try:
                os.close(self.pipewire_fd)
            except OSError:
                pass

            self.pipewire_fd = None


# ======================================================================
# GSTREAMER
# ======================================================================
#
# Dùng HAI pipeline nhỏ:
#
#   Pipeline thu  : <nguồn> ! video/x-raw ! appsink(raw)
#                   appsink chỉ giữ MỘT khung hình mới nhất.
#
#   Pipeline mã hóa: appsrc ! videoconvert ! videoscale ! jpegenc ! appsink(jpeg)
#
# Cứ mỗi 1/fps giây, script lấy khung hình mới nhất (nếu có) từ pipeline thu,
# đẩy qua pipeline mã hóa rồi ghi JPEG ra stdout.
#
# Cách này đúng với nguồn PipeWire chỉ gửi hình khi màn hình thay đổi:
# khung hình cuối cùng luôn được gửi đi (không bị bỏ như khi lọc FPS),
# và việc chuyển đổi / mã hóa chỉ chạy tối đa fps lần mỗi giây.

def build_capture_description(source):
    # "video/x-raw" (không memory feature) ép dùng bộ nhớ hệ thống,
    # giống pipeline pipewiresrc ! videoconvert đã chạy được khi thử nghiệm.
    return (
        "%s "
        "! video/x-raw "
        "! appsink name=raw max-buffers=1 drop=true sync=false"
    ) % source


def build_encoder_description(args):
    if args.max_width > 0:
        scale_caps = "video/x-raw,width=[1,%d]" % args.max_width
    else:
        scale_caps = "video/x-raw"

    return (
        "appsrc name=src format=time is-live=true do-timestamp=true "
        "! videoconvert "
        "! videoscale "
        "! %s "
        "! jpegenc quality=%d "
        "! appsink name=jpeg sync=false max-buffers=2"
    ) % (scale_caps, args.quality)


def run_pipeline(source, args):
    """Chạy cho đến khi bị dừng hoặc gặp lỗi."""
    Gst.init(None)

    capture_description = build_capture_description(source)
    encoder_description = build_encoder_description(args)

    log("Pipeline thu     : " + capture_description)
    log("Pipeline mã hóa  : " + encoder_description)

    try:
        capture = Gst.parse_launch(capture_description)
        encoder = Gst.parse_launch(encoder_description)

    except GLib.Error as error:
        fail(
            "Không tạo được pipeline GStreamer (thiếu plugin?): %s" % error,
            3,
        )
        return

    raw_sink = capture.get_by_name("raw")
    app_src = encoder.get_by_name("src")
    jpeg_sink = encoder.get_by_name("jpeg")

    out = sys.stdout.buffer

    current_caps = [None]

    def on_error(message):
        error, debug = message.parse_error()

        log("GStreamer ERROR: %s (%s)" % (error, debug))

        emit("ERROR", message="Lỗi GStreamer: %s" % error)

        request_stop(4)

    def on_eos():
        log("GStreamer EOS")

        emit(
            "ERROR",
            message=(
                "Luồng màn hình đã kết thúc "
                "(người dùng ở máy Client có thể đã dừng chia sẻ)."
            ),
        )

        request_stop(5)

    def watch_bus(pipeline):
        bus = pipeline.get_bus()
        bus.add_signal_watch()

        def handler(_bus, message):
            if message.type == Gst.MessageType.ERROR:
                on_error(message)

            elif message.type == Gst.MessageType.EOS:
                on_eos()

        bus.connect("message", handler)

        return bus

    def encode_and_send(sample):
        """Mã hóa một khung hình thô thành JPEG rồi ghi ra stdout."""
        caps = sample.get_caps()
        caps_text = caps.to_string()

        if caps_text != current_caps[0]:
            app_src.set_property("caps", caps)
            current_caps[0] = caps_text

        result = app_src.emit("push-buffer", sample.get_buffer())

        if result != Gst.FlowReturn.OK:
            log("push-buffer thất bại: %s" % result)
            return

        jpeg_sample = jpeg_sink.emit("try-pull-sample", 2 * Gst.SECOND)

        if jpeg_sample is None:
            log("Không nhận được khung hình JPEG từ bộ mã hóa.")
            return

        jpeg_buffer = jpeg_sample.get_buffer()

        ok, map_info = jpeg_buffer.map(Gst.MapFlags.READ)

        if not ok:
            log("Không đọc được bộ đệm JPEG.")
            return

        try:
            data = bytes(map_info.data)
        finally:
            jpeg_buffer.unmap(map_info)

        try:
            out.write(struct.pack(">I", len(data)))
            out.write(data)
            out.flush()

        except (BrokenPipeError, OSError):
            # Agent không còn đọc nữa
            request_stop(0)
            return

        if not STATE.first_frame_sent:
            STATE.first_frame_sent = True

            emit(
                "STATUS",
                state="STREAMING",
                message="Đang truyền màn hình.",
            )

    def tick():
        if STATE.terminated:
            return False

        # 0 = không chờ: chỉ lấy khung hình mới nếu đã có
        sample = raw_sink.emit("try-pull-sample", 0)

        if sample is not None:
            try:
                encode_and_send(sample)

            except Exception as error:
                log("Lỗi mã hóa khung hình: %s" % error)

        return True

    def on_no_frames():
        if not STATE.first_frame_sent and not STATE.terminated:
            emit(
                "STATUS",
                state="WAITING_FRAMES",
                message=(
                    "Chưa nhận được khung hình nào. Màn hình có thể đang "
                    "đứng yên hoặc đang bị khóa."
                ),
            )

        return False

    capture_bus = watch_bus(capture)
    encoder_bus = watch_bus(encoder)

    loop = GLib.MainLoop()
    STATE.loop = loop

    encoder.set_state(Gst.State.PLAYING)
    capture.set_state(Gst.State.PLAYING)

    GLib.timeout_add(int(1000 / args.fps), tick)
    GLib.timeout_add_seconds(10, on_no_frames)

    try:
        if not STATE.terminated:
            loop.run()

    finally:
        STATE.loop = None

        capture.set_state(Gst.State.NULL)
        encoder.set_state(Gst.State.NULL)

        capture_bus.remove_signal_watch()
        encoder_bus.remove_signal_watch()


# ======================================================================
# MAIN
# ======================================================================

def choose_backend(requested):
    if requested != "auto":
        return requested

    session_type = os.environ.get("XDG_SESSION_TYPE", "").lower()

    if session_type == "x11":
        return "x11"

    if not os.environ.get("WAYLAND_DISPLAY") and os.environ.get("DISPLAY"):
        return "x11"

    return "portal"


def parse_args():
    parser = argparse.ArgumentParser(description="PBL4 screen capture")

    parser.add_argument("--backend", default="auto",
                        choices=["auto", "portal", "x11", "test"])
    parser.add_argument("--fps", type=int, default=8)
    parser.add_argument("--max-width", type=int, default=1280)
    parser.add_argument("--quality", type=int, default=60)
    parser.add_argument("--restore-token-file", default="")
    parser.add_argument("--approval-timeout", type=int, default=120)

    return parser.parse_args()


def main():
    args = parse_args()

    if args.fps < 1 or args.fps > 60:
        fail("FPS phải nằm trong khoảng 1-60.", 2)

    if args.quality < 1 or args.quality > 100:
        fail("Chất lượng JPEG phải nằm trong khoảng 1-100.", 2)

    install_signal_handlers()

    threading.Thread(target=watch_stdin, daemon=True).start()

    backend = choose_backend(args.backend)

    log("Backend: %s, fps=%d, max-width=%d, quality=%d" % (
        backend, args.fps, args.max_width, args.quality))

    portal = None

    try:
        if backend == "portal":
            try:
                portal = Portal(
                    args.restore_token_file, args.approval_timeout
                )
            except Exception as error:
                fail(
                    "Không kết nối được D-Bus/Portal (Agent phải chạy trong "
                    "phiên đồ họa của người dùng, cần python3-dbus): %s"
                    % error,
                    2,
                )

            fd, node_id = portal.open_stream()

            source = 'pipewiresrc fd=%d path="%d"' % (fd, node_id)

        elif backend == "x11":
            source = "ximagesrc use-damage=false show-pointer=true"

        else:
            source = (
                "videotestsrc is-live=true pattern=ball "
                "! video/x-raw,width=1280,height=720,framerate=30/1"
            )

        run_pipeline(source, args)

    except Terminated:
        log("Đã dừng trước khi bắt đầu truyền.")

    except Exception as error:
        emit("ERROR", message=str(error))
        log("LỖI: %s" % error)

        if STATE.exit_code == 0:
            STATE.exit_code = 1

    finally:
        if portal is not None:
            portal.close()

    sys.exit(STATE.exit_code)


if __name__ == "__main__":
    main()
