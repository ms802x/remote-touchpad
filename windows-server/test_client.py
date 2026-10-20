"""
Throwaway manual test client for server.py.

Run this on the same machine (or another on the LAN) after starting server.py:
    python test_client.py <server-ip>

It will wiggle the mouse, click, and type some text so you can confirm
the server is working before building the Android app.
"""
import asyncio
import json
import sys

import websockets


async def main():
    if len(sys.argv) < 2:
        print("Usage: python test_client.py <server-ip>")
        return

    uri = f"ws://{sys.argv[1]}:8765"
    async with websockets.connect(uri) as ws:
        print("Connected. Moving mouse...")
        for _ in range(20):
            await ws.send(json.dumps({"type": "move", "dx": 5, "dy": 0}))
            await asyncio.sleep(0.05)

        print("Clicking...")
        await ws.send(json.dumps({"type": "click", "button": "left"}))
        await asyncio.sleep(0.5)

        print("Typing text...")
        await ws.send(json.dumps({"type": "key", "text": "hello from phone"}))
        await asyncio.sleep(0.5)

        print("Pressing Enter...")
        await ws.send(json.dumps({"type": "key", "code": "ENTER"}))

        print("Done.")


if __name__ == "__main__":
    asyncio.run(main())
