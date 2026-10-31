#!/usr/bin/python3

import os
import sys
import time

import dbus
import dbus.mainloop.glib

import gi
gi.require_version("Gst", "1.0")

from gi.repository import GLib, Gst


PORTAL_BUS_NAME = "org.freedesktop.portal.Desktop"
PORTAL_OBJECT_PATH = "/org/freedesktop/portal/desktop"

SCREENCAST_INTERFACE = "org.freedesktop.portal.ScreenCast"
REQUEST_INTERFACE = "org.freedesktop.portal.Request"
SESSION_INTERFACE = "org.freedesktop.portal.Session"

OUTPUT_FILE = "/tmp/pbl4-screen-stream.jpg"
TEST_DURATION_SECONDS = 10


def wait_for_response(bus, request_path):
    loop = GLib.MainLoop()
    result = {}

    def on_response(response, results):
        result["response"] = int(response)
        result["results"] = results
        loop.quit()

    signal = bus.add_signal_receiver(
        on_response,
        signal_name="Response",
        dbus_interface=REQUEST_INTERFACE,
        path=str(request_path)
    )

    try:
        loop.run()
    finally:
        signal.remove()

    if result.get("response") != 0:
        raise RuntimeError(
            "Portal không cấp quyền hoặc người dùng đã hủy."
        )

    return result["results"]


def call_portal_request(bus, method, *args):
    request_path = method(*args)
    return wait_for_response(bus, request_path)


def capture_continuous(pipewire_fd, node_id):
    Gst.init(None)

    pipeline_description = (
        f'pipewiresrc fd={pipewire_fd} path="{node_id}" '
        '! videoconvert '
        '! jpegenc quality=85 '
        '! appsink name=frames '
        'emit-signals=true '
        'max-buffers=2 '
        'drop=true '
        'sync=false'
    )

    pipeline = Gst.parse_launch(pipeline_description)
    appsink = pipeline.get_by_name("frames")

    loop = GLib.MainLoop()

    result = {
        "frame_count": 0,
        "error": None
    }

    started_at = time.monotonic()

    def on_new_sample(sink):
        sample = sink.emit("pull-sample")

        if sample is None:
            return Gst.FlowReturn.ERROR

        buffer = sample.get_buffer()

        success, map_info = buffer.map(Gst.MapFlags.READ)

        if not success:
            result["error"] = "Không đọc được dữ liệu khung hình."
            GLib.idle_add(loop.quit)
            return Gst.FlowReturn.ERROR

        try:
            result["frame_count"] += 1

            # Chỉ lưu khung hình đầu tiên để kiểm tra.
            if result["frame_count"] == 1:
                with open(OUTPUT_FILE, "wb") as image_file:
                    image_file.write(map_info.data)

        finally:
            buffer.unmap(map_info)

        return Gst.FlowReturn.OK

    def on_bus_message(gst_bus, message):
        if message.type == Gst.MessageType.ERROR:
            error, debug_info = message.parse_error()

            result["error"] = str(error)

            print("[GSTREAMER ERROR]", error, file=sys.stderr)

            if debug_info:
                print(debug_info, file=sys.stderr)

            loop.quit()

    def stop_test():
        loop.quit()
        return False

    appsink.connect("new-sample", on_new_sample)

    gst_bus = pipeline.get_bus()
    gst_bus.add_signal_watch()
    gst_bus.connect("message", on_bus_message)

    print(
        f"[GSTREAMER] Bắt đầu thu hình trong "
        f"{TEST_DURATION_SECONDS} giây...",
        file=sys.stderr
    )

    pipeline.set_state(Gst.State.PLAYING)

    GLib.timeout_add_seconds(
        TEST_DURATION_SECONDS,
        stop_test
    )

    try:
        loop.run()
    finally:
        pipeline.set_state(Gst.State.NULL)
        gst_bus.remove_signal_watch()

    elapsed = time.monotonic() - started_at

    if result["error"]:
        raise RuntimeError(result["error"])

    frame_count = result["frame_count"]

    if frame_count == 0:
        raise RuntimeError("Không nhận được khung hình nào.")

    fps = frame_count / elapsed

    print("\n========== KẾT QUẢ ==========")
    print("Thời gian:", round(elapsed, 2), "giây")
    print("Số khung hình:", frame_count)
    print("FPS trung bình:", round(fps, 2))
    print("Ảnh kiểm tra:", OUTPUT_FILE)


def main():
    session_handle = None
    pipewire_fd = None
    bus = None

    try:
        print("======================================")
        print(" PBL4 - CONTINUOUS SCREEN CAPTURE")
        print("======================================")

        dbus.mainloop.glib.DBusGMainLoop(set_as_default=True)

        bus = dbus.SessionBus()

        portal_object = bus.get_object(
            PORTAL_BUS_NAME,
            PORTAL_OBJECT_PATH
        )

        screencast = dbus.Interface(
            portal_object,
            SCREENCAST_INTERFACE
        )

        token = f"pbl4_stream_{os.getpid()}"

        # 1. Tạo session
        create_options = dbus.Dictionary({
            "handle_token": dbus.String(token),
            "session_handle_token": dbus.String(token + "_session")
        }, signature="sv")

        print("\n[1/4] Tạo ScreenCast session...")

        create_results = call_portal_request(
            bus,
            screencast.CreateSession,
            create_options
        )

        session_handle = str(create_results["session_handle"])

        print("[OK] Session:", session_handle)

        # 2. Chọn nguồn màn hình
        select_options = dbus.Dictionary({
            "handle_token": dbus.String(token + "_select"),
            "types": dbus.UInt32(1),
            "multiple": dbus.Boolean(False),
            "cursor_mode": dbus.UInt32(2)
        }, signature="sv")

        print("\n[2/4] Chọn nguồn màn hình...")

        call_portal_request(
            bus,
            screencast.SelectSources,
            dbus.ObjectPath(session_handle),
            select_options
        )

        print("[OK] Đã chọn nguồn.")

        # 3. Xin quyền chia sẻ
        start_options = dbus.Dictionary({
            "handle_token": dbus.String(token + "_start")
        }, signature="sv")

        print("\n[3/4] Đang yêu cầu quyền chia sẻ...")
        print("Hãy chọn màn hình và nhấn Share.")

        start_results = call_portal_request(
            bus,
            screencast.Start,
            dbus.ObjectPath(session_handle),
            dbus.String(""),
            start_options
        )

        streams = start_results.get("streams", [])

        if not streams:
            raise RuntimeError("Không nhận được PipeWire stream.")

        node_id = int(streams[0][0])

        print("[OK] PipeWire node ID:", node_id)

        # 4. Mở PipeWire remote
        print("\n[4/4] Mở PipeWire remote...")

        remote_fd = screencast.OpenPipeWireRemote(
            dbus.ObjectPath(session_handle),
            dbus.Dictionary({}, signature="sv")
        )

        pipewire_fd = (
            remote_fd.take()
            if hasattr(remote_fd, "take")
            else int(remote_fd)
        )

        print("[OK] PipeWire remote đã mở.")

        # Thu hình liên tục
        capture_continuous(pipewire_fd, node_id)

        print("\n[SUCCESS] Thu hình liên tục thành công.")

    except Exception as error:
        print("\n[ERROR]", error)
        return 1

    finally:
        if pipewire_fd is not None:
            os.close(pipewire_fd)

        if session_handle and bus:
            try:
                session_object = bus.get_object(
                    PORTAL_BUS_NAME,
                    session_handle
                )

                session = dbus.Interface(
                    session_object,
                    SESSION_INTERFACE
                )

                session.Close()

                print("[PORTAL] Đã đóng session.")

            except Exception as error:
                print("[PORTAL] Lỗi đóng session:", error)

    return 0


if __name__ == "__main__":
    sys.exit(main())