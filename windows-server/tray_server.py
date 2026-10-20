"""
Windows tray application wrapper around server.py.

Runs the WebSocket server in a background thread and shows a system tray
icon with the laptop's LAN IP (click "Show address") and a Quit option.
This is the entry point that gets packaged into the .exe (see build.bat).
"""
import os
import sys
import threading

import pystray
from PIL import Image

import server

# When PyInstaller bundles this as --onefile, data files are unpacked to a
# temp dir at runtime (sys._MEIPASS) instead of living next to this script.
_base_dir = getattr(sys, "_MEIPASS", os.path.dirname(os.path.abspath(__file__)))
ICON_PATH = os.path.join(_base_dir, "assets", "app_icon.ico")


def load_icon_image():
    return Image.open(ICON_PATH)


def run_server_thread():
    import asyncio
    asyncio.run(server.main())


def on_show_address(icon, item):
    icon.notify(f"ws://{server.local_ip()}:{server.PORT}", "RemoteTouchpad")


def on_quit(icon, item):
    icon.stop()


def main():
    t = threading.Thread(target=run_server_thread, daemon=True)
    t.start()

    menu = pystray.Menu(
        pystray.MenuItem("Show address", on_show_address),
        pystray.MenuItem("Quit", on_quit),
    )
    icon = pystray.Icon("RemoteTouchpad", load_icon_image(), "RemoteTouchpad", menu)
    icon.run()


if __name__ == "__main__":
    main()
