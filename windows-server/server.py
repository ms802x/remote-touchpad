"""
Remote mouse/keyboard server.

Run this on the Windows laptop:
    python server.py

The phone app finds it automatically on the LAN (UDP discovery on port 8766),
or you can connect manually to ws://<this-machine's-LAN-IP>:8765
"""
import asyncio
import ctypes
from ctypes import wintypes
import json
import socket

from pynput.keyboard import Controller as KeyboardController, Key
from pynput.mouse import Controller as MouseController, Button

import websockets

PORT = 8765
DISCOVERY_PORT = 8766
DISCOVERY_REQUEST = b"REMOTE_CONTROL_DISCOVER"

mouse = MouseController()
keyboard = KeyboardController()

# A single native relative-move call (vs. pynput's read-position-then-set-
# position, which is two round trips) - noticeably smoother/lower latency.
_MOUSEEVENTF_MOVE = 0x0001


def move_mouse_relative(dx: int, dy: int) -> None:
    ctypes.windll.user32.mouse_event(_MOUSEEVENTF_MOVE, dx, dy, 0, 0)


# --- Unicode text injection -------------------------------------------------
# pynput's keyboard.type() maps characters to virtual-key codes through the
# ACTIVE WINDOWS KEYBOARD LAYOUT (VkKeyScan). If the system's active input
# language isn't English (e.g. Arabic), plain ASCII letters get reinterpreted
# through that layout and come out as the wrong characters. Sending raw
# Unicode code points via SendInput's KEYEVENTF_UNICODE flag bypasses the
# active layout entirely, so typed text is always correct regardless of
# whatever language the system is currently set to.

INPUT_KEYBOARD = 1
KEYEVENTF_UNICODE = 0x0004
KEYEVENTF_KEYUP = 0x0002


class _KEYBDINPUT(ctypes.Structure):
    _fields_ = [
        ("wVk", wintypes.WORD),
        ("wScan", wintypes.WORD),
        ("dwFlags", wintypes.DWORD),
        ("time", wintypes.DWORD),
        ("dwExtraInfo", ctypes.POINTER(wintypes.ULONG)),
    ]


class _INPUT(ctypes.Structure):
    class _Inner(ctypes.Union):
        _fields_ = [("ki", _KEYBDINPUT)]

    _anonymous_ = ("_inner",)
    _fields_ = [("type", wintypes.DWORD), ("_inner", _Inner)]


def type_text_unicode(text: str) -> None:
    events = []
    for ch in text:
        code = ord(ch)
        events.append(_INPUT(type=INPUT_KEYBOARD, ki=_KEYBDINPUT(0, code, KEYEVENTF_UNICODE, 0, None)))
        events.append(_INPUT(type=INPUT_KEYBOARD, ki=_KEYBDINPUT(0, code, KEYEVENTF_UNICODE | KEYEVENTF_KEYUP, 0, None)))
    if not events:
        return
    array = (_INPUT * len(events))(*events)
    ctypes.windll.user32.SendInput(len(events), array, ctypes.sizeof(_INPUT))


# Phone sends fractional pixel deltas (sensitivity multiplier isn't a whole
# number); truncating each one to an int would silently drop slow/small
# movements, so leftover fractions are carried over to the next move.
_move_remainder_x = 0.0
_move_remainder_y = 0.0

SPECIAL_KEYS = {
    "ENTER": Key.enter,
    "BACKSPACE": Key.backspace,
    "SPACE": Key.space,
    "TAB": Key.tab,
    "VOLUME_UP": Key.media_volume_up,
    "VOLUME_DOWN": Key.media_volume_down,
    "VOLUME_MUTE": Key.media_volume_mute,
    "PLAY_PAUSE": Key.media_play_pause,
    "NEXT_TRACK": Key.media_next,
    "PREV_TRACK": Key.media_previous,
}

BUTTONS = {
    "left": Button.left,
    "right": Button.right,
}


def local_ip() -> str:
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        s.close()


def handle_message(data: dict) -> None:
    msg_type = data.get("type")

    if msg_type == "move":
        global _move_remainder_x, _move_remainder_y
        _move_remainder_x += data.get("dx", 0)
        _move_remainder_y += data.get("dy", 0)
        dx = int(_move_remainder_x)
        dy = int(_move_remainder_y)
        if dx or dy:
            _move_remainder_x -= dx
            _move_remainder_y -= dy
            move_mouse_relative(dx, dy)

    elif msg_type == "click":
        button = BUTTONS.get(data.get("button", "left"), Button.left)
        mouse.click(button)

    elif msg_type == "key":
        if "text" in data:
            type_text_unicode(data["text"])
        elif "code" in data:
            key = SPECIAL_KEYS.get(data["code"])
            if key is not None:
                for _ in range(max(1, data.get("repeat", 1))):
                    keyboard.press(key)
                    keyboard.release(key)

    elif msg_type == "scroll":
        clicks = data.get("clicks", 0)
        if clicks:
            mouse.scroll(0, clicks)

    elif msg_type == "zoom":
        clicks = data.get("clicks", 0)
        if clicks:
            with keyboard.pressed(Key.ctrl):
                mouse.scroll(0, clicks)


def _disable_nagle(websocket) -> None:
    # Nagle's algorithm batches/delays small TCP packets, which is exactly
    # the traffic pattern here (a stream of tiny move/key messages). Without
    # disabling it, bursts of input can lag by tens of milliseconds each.
    try:
        sock = websocket.transport.get_extra_info("socket")
        if sock is not None:
            sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
    except Exception:
        pass


async def handler(websocket):
    print("Phone connected.")
    _disable_nagle(websocket)
    try:
        async for message in websocket:
            try:
                data = json.loads(message)
            except json.JSONDecodeError:
                continue
            handle_message(data)
    except websockets.exceptions.ConnectionClosed:
        pass
    finally:
        print("Phone disconnected.")


class DiscoveryProtocol(asyncio.DatagramProtocol):
    def __init__(self):
        self.transport = None

    def connection_made(self, transport):
        self.transport = transport

    def datagram_received(self, data, addr):
        if data == DISCOVERY_REQUEST:
            reply = json.dumps({
                "name": socket.gethostname(),
                "ip": local_ip(),
                "port": PORT,
            }).encode()
            self.transport.sendto(reply, addr)


async def run_discovery_responder():
    loop = asyncio.get_running_loop()
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind(("0.0.0.0", DISCOVERY_PORT))
    await loop.create_datagram_endpoint(lambda: DiscoveryProtocol(), sock=sock)


async def main():
    print(f"Remote control server listening on ws://{local_ip()}:{PORT}")
    await run_discovery_responder()
    async with websockets.serve(handler, "0.0.0.0", PORT):
        await asyncio.Future()  # run forever


if __name__ == "__main__":
    asyncio.run(main())
