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

OUTPUT_FILE = "/tmp/pbl4-screen-test.jpg"


def wait_for_portal_response(bus, request_path):
    """
    Chờ Portal trả về tín hiệu Request.Response.
    Response:
        0 = thành công
        1 = người dùng hủy
        2 = lỗi
    """

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

    loop.run()
    signal.remove()

    response_code = result.get("response", 2)

    if response_code != 0:
        if response_code == 1:
            raise RuntimeError("Người dùng đã hủy yêu cầu chia sẻ màn hình.")

        raise RuntimeError("Portal không thể khởi tạo phiên chia sẻ màn hình.")

    return result["results"]


def call_portal_request(bus, method, *args):
    """
    Gọi một phương thức Portal và chờ Request.Response.
    """

    request_path = method(*args)

    return wait_for_portal_response(bus, request_path)


def capture_one_frame(pipewire_fd, node_id):
    """
    Đọc một khung hình từ PipeWire bằng GStreamer
    và lưu thành file JPEG.
    """

    Gst.init(None)

    pipeline_description = (
        f'pipewiresrc fd={pipewire_fd} path="{node_id}" '
        '! videoconvert '
        '! jpegenc quality=85 '
        '! appsink name=frames emit-signals=true '
        'max-buffers=1 drop=true sync=false'
    )

    print("[GSTREAMER] Khởi tạo pipeline...")

    pipeline = Gst.parse_launch(pipeline_description)

    appsink = pipeline.get_by_name("frames")

    loop = GLib.MainLoop()
    result = {
        "saved": False,
        "error": None,
        "timed_out": False
    }

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
            with open(OUTPUT_FILE, "wb") as image_file:
                image_file.write(map_info.data)

            result["saved"] = True

            print("[GSTREAMER] Đã nhận được khung hình.")
            print(f"[GSTREAMER] Ảnh được lưu tại: {OUTPUT_FILE}")

        finally:
            buffer.unmap(map_info)

        GLib.idle_add(loop.quit)

        return Gst.FlowReturn.OK

    def on_bus_message(bus, message):
        if message.type == Gst.MessageType.ERROR:
            error, debug_info = message.parse_error()

            result["error"] = str(error)

            print("[GSTREAMER ERROR]", error)

            if debug_info:
                print("[GSTREAMER DEBUG]", debug_info)

            loop.quit()

    def on_timeout():
        result["timed_out"] = True
        loop.quit()
        return False

    appsink.connect("new-sample", on_new_sample)

    gst_bus = pipeline.get_bus()
    gst_bus.add_signal_watch()
    gst_bus.connect("message", on_bus_message)

    print("[GSTREAMER] Bắt đầu nhận hình ảnh...")

    pipeline.set_state(Gst.State.PLAYING)

    timeout_id = GLib.timeout_add_seconds(30, on_timeout)

    try:
        loop.run()
    finally:
        GLib.source_remove(timeout_id)
        pipeline.set_state(Gst.State.NULL)
        gst_bus.remove_signal_watch()

    if result["error"]:
        raise RuntimeError(result["error"])

    if result["timed_out"]:
        raise RuntimeError("Hết thời gian chờ khung hình từ PipeWire.")

    if not result["saved"]:
        raise RuntimeError("Không nhận được khung hình.")

    if not os.path.exists(OUTPUT_FILE):
        raise RuntimeError("Không tìm thấy file ảnh sau khi ghi.")

    if os.path.getsize(OUTPUT_FILE) == 0:
        raise RuntimeError("File ảnh được tạo nhưng không có dữ liệu.")


def main():
    session_handle = None
    pipewire_fd = None
    bus = None

    try:
        print("======================================")
        print(" PBL4 - SCREEN CAST PORTAL TEST")
        print("======================================")

        # Khởi tạo kết nối D-Bus
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

        # Tạo session
        token = f"pbl4_{os.getpid()}_{int(time.time())}"

        create_options = dbus.Dictionary({
            "handle_token": dbus.String(token),
            "session_handle_token": dbus.String(token + "_session")
        }, signature="sv")

        print("\n[1/4] Đang tạo ScreenCast session...")

        create_results = call_portal_request(
            bus,
            screencast.CreateSession,
            create_options
        )

        session_handle = str(create_results["session_handle"])

        print("[OK] Đã tạo session:", session_handle)

        # Chọn nguồn màn hình
        select_options = dbus.Dictionary({
            "handle_token": dbus.String(token + "_select"),
            "types": dbus.UInt32(1),
            "multiple": dbus.Boolean(False),
            "cursor_mode": dbus.UInt32(2)
        }, signature="sv")

        print("\n[2/4] Đang cấu hình nguồn màn hình...")

        call_portal_request(
            bus,
            screencast.SelectSources,
            dbus.ObjectPath(session_handle),
            select_options
        )

        print("[OK] Đã cấu hình nguồn màn hình.")

        # Bắt đầu chia sẻ và chờ người dùng cấp quyền
        start_options = dbus.Dictionary({
            "handle_token": dbus.String(token + "_start")
        }, signature="sv")

        print("\n[3/4] Đang yêu cầu quyền chia sẻ màn hình...")
        print("Hãy chọn màn hình và xác nhận trên hộp thoại Ubuntu.")

        start_results = call_portal_request(
            bus,
            screencast.Start,
            dbus.ObjectPath(session_handle),
            dbus.String(""),
            start_options
        )

        streams = start_results.get("streams", [])

        if not streams:
            raise RuntimeError("Portal không trả về PipeWire stream.")

        node_id = int(streams[0][0])

        print("[OK] Đã nhận PipeWire node ID:", node_id)

        # Mở kết nối PipeWire riêng của Portal
        print("\n[4/4] Đang mở PipeWire remote...")

        remote_fd = screencast.OpenPipeWireRemote(
            dbus.ObjectPath(session_handle),
            dbus.Dictionary({}, signature="sv")
        )

        if hasattr(remote_fd, "take"):
            pipewire_fd = remote_fd.take()
        else:
            pipewire_fd = int(remote_fd)

        print("[OK] Đã mở PipeWire remote.")

        # Nhận một khung hình
        capture_one_frame(pipewire_fd, node_id)

        print("\n======================================")
        print(" KIỂM THỬ THÀNH CÔNG")
        print("======================================")
        print("File ảnh:", OUTPUT_FILE)

    except Exception as error:
        print("\n[ERROR]", error)
        sys.exit_code = 1

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

                print("[PORTAL] Đã đóng ScreenCast session.")

            except Exception as error:
                print("[PORTAL] Không thể đóng session:", error)


if __name__ == "__main__":
    main()