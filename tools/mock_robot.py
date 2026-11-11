#!/usr/bin/env python3
"""Symulator robota do testowania aplikacji bez sprzętu.

Udaje główny ESP32: serwer WebSocket na /ws, loguje komendy JSON z aplikacji,
odpowiada na `status`, a dźwięk (ramki binarne PCM 16 kHz mono) zapisuje do pliku
`mic_capture.wav`, żeby można go było odsłuchać. Protokół: docs/protocol.md.

Uruchomienie:
    pip install websockets
    python tools/mock_robot.py            # nasłuch na 0.0.0.0:80 (port 80 może wymagać uprawnień)
    python tools/mock_robot.py --port 8080

Aplikacja łączy się z `ws://<adres>/ws` na porcie 80, więc najprościej uruchomić na porcie 80
albo przekierować port. Wymaga websockets >= 12.
"""
import argparse
import asyncio
import json
import time
import wave

import websockets

DRIVE_TIMEOUT_S = 0.5  # fail-safe jak w prawdziwym robocie


async def handler(ws, state):
    print("[+] klient połączony")
    state["last_drive"] = 0.0
    try:
        async for msg in ws:
            if isinstance(msg, (bytes, bytearray)):
                state["wav"].writeframes(msg)
                state["audio_bytes"] += len(msg)
                continue
            try:
                cmd = json.loads(msg)
            except json.JSONDecodeError:
                print("[!] niepoprawny JSON:", msg)
                continue
            c = cmd.get("c")
            if c == "drive":
                state["last_drive"] = time.monotonic()
                print(f"[jazda] x={cmd.get('x'):+.2f} y={cmd.get('y'):+.2f}")
            elif c == "status":
                await ws.send(json.dumps({"t": "status", "bat": 7.84, "cam": True, "audio": state["audio_on"]}))
            elif c == "audio":
                state["audio_on"] = bool(cmd.get("on"))
                print(f"[audio] {'ON' if state['audio_on'] else 'OFF'} (odebrano {state['audio_bytes']} B)")
            else:
                print("[cmd]", cmd)
    except websockets.ConnectionClosed:
        pass
    finally:
        print("[-] klient rozłączony -> STOP silników, audio OFF")
        state["audio_on"] = False


async def failsafe(state):
    """Wypisuje STOP, gdy przez 500 ms nie przyszła komenda drive (jak w firmware)."""
    moving = False
    while True:
        await asyncio.sleep(0.1)
        age = time.monotonic() - state["last_drive"]
        if state["last_drive"] and age < DRIVE_TIMEOUT_S:
            moving = True
        elif moving:
            moving = False
            print("[fail-safe] brak komendy drive przez 500 ms -> STOP")


async def main(port):
    wav = wave.open("mic_capture.wav", "wb")
    wav.setnchannels(1)
    wav.setsampwidth(2)
    wav.setframerate(16000)
    state = {"wav": wav, "audio_bytes": 0, "audio_on": False, "last_drive": 0.0}
    async with websockets.serve(lambda ws: handler(ws, state), "0.0.0.0", port):
        print(f"Symulator Wall-E nasłuchuje na ws://0.0.0.0:{port}/ws  (Ctrl+C kończy)")
        try:
            await failsafe(state)
        finally:
            wav.close()


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("--port", type=int, default=80)
    asyncio.run(main(p.parse_args().port))
