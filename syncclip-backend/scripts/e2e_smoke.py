#!/usr/bin/env python3
"""E2E smoke: REST register/login + WebSocket relay/pending/ACK/spoof/rate-limit."""
import asyncio
import base64
import hashlib
import json
import os
import sys
import time
import urllib.error
import urllib.request
import uuid

import websockets

BASE = "http://127.0.0.1:18080"
WS = "ws://127.0.0.1:18080"
PASSWORD = "SecurePassword123!"
results = []


def check(name, condition, detail=""):
    results.append((name, bool(condition), detail))
    print(("PASS " if condition else "FAIL ") + name + ("" if condition else f" -> {detail}"))


def http(method, path, body=None, token=None, headers=None):
    data = None if body is None else json.dumps(body).encode()
    request = urllib.request.Request(BASE + path, data=data, method=method)
    request.add_header("Content-Type", "application/json")
    if token:
        request.add_header("Authorization", "Bearer " + token)
    for key, value in (headers or {}).items():
        request.add_header(key, value)
    try:
        with urllib.request.urlopen(request) as response:
            raw = response.read().decode()
            return response.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as error:
        raw = error.read().decode()
        return error.code, (json.loads(raw) if raw else None)


def payload(size=64):
    return base64.b64encode(bytes([7] * size)).decode()


def event_for(origin, targets, event_id=None):
    return {
        "type": "SYNC_EVENT",
        "eventId": event_id or str(uuid.uuid4()),
        "originDeviceId": origin,
        "targetDeviceIds": targets,
        "encryptedPayload": payload(),
        "payloadHash": hashlib.sha256(b"hello").hexdigest(),
        "timestamp": int(time.time() * 1000),
    }


async def receive_json(socket, timeout=5):
    raw = await asyncio.wait_for(socket.recv(), timeout=timeout)
    return json.loads(raw)


async def main():
    email = f"e2e-{uuid.uuid4()}@example.com"
    device_a = str(uuid.uuid4())
    device_b = str(uuid.uuid4())

    status, registered = http("POST", "/v1/auth/register", {
        "email": email, "password": PASSWORD, "deviceId": device_a,
        "deviceName": "E2E Desktop", "osType": "macOS",
    })
    check("register 201", status == 201, f"{status} {registered}")
    token_a = registered["accessToken"]

    status, logged = http("POST", "/v1/auth/login", {
        "email": email, "password": PASSWORD, "deviceId": device_b,
        "deviceName": "E2E Phone", "osType": "Android",
    })
    check("login second device 200", status == 200, f"{status} {logged}")
    token_b = logged["accessToken"]

    # --- online relay ---
    async with websockets.connect(f"{WS}/v1/sync?deviceId={device_a}",
                                  additional_headers={"Authorization": "Bearer " + token_a}) as ws_a:
        async with websockets.connect(f"{WS}/v1/sync?deviceId={device_b}",
                                      additional_headers={"Authorization": "Bearer " + token_b}) as ws_b:
            event_id = str(uuid.uuid4())
            await ws_a.send(json.dumps(event_for(device_a, [device_b], event_id)))
            relayed = await receive_json(ws_b)
            check("online relay delivers SYNC_EVENT",
                  relayed.get("type") == "SYNC_EVENT" and relayed.get("eventId") == event_id, relayed)
            check("relayed event has no targetDeviceIds", "targetDeviceIds" not in relayed, relayed)

            # spoof: чужой origin должен быть отклонён
            spoof = event_for(str(uuid.uuid4()), [device_b])
            await ws_a.send(json.dumps(spoof))
            error = await receive_json(ws_a)
            check("spoofed origin rejected with INVALID_PACKET",
                  error.get("type") == "ERROR" and error.get("code") == "INVALID_PACKET", error)

            # oversize payload
            big = event_for(device_a, [device_b])
            big["encryptedPayload"] = payload(524_317)
            await ws_a.send(json.dumps(big))
            error = await receive_json(ws_a)
            check("oversize payload rejected with PAYLOAD_TOO_LARGE",
                  error.get("type") == "ERROR" and error.get("code") == "PAYLOAD_TOO_LARGE", error)

            # ACK удаляет строку
            await ws_b.send(json.dumps({"type": "ACK", "eventId": event_id, "deviceId": device_b}))
            await asyncio.sleep(0.3)
            status, pending = http("GET", f"/v1/sync/pending?deviceId={device_b}", token=token_b)
            check("ack removes packet from queue", status == 200 and pending["totalCount"] == 0, pending)

            # PING -> PONG
            await ws_a.send(json.dumps({"type": "PING", "timestamp": 123}))
            pong = await receive_json(ws_a)
            check("ping answered with pong", pong.get("type") == "PONG" and pong.get("timestamp") == 123, pong)

    # --- offline queue: устройство B отключено ---
    offline_event = str(uuid.uuid4())
    async with websockets.connect(f"{WS}/v1/sync?deviceId={device_a}",
                                  additional_headers={"Authorization": "Bearer " + token_a}) as ws_a2:
        await ws_a2.send(json.dumps(event_for(device_a, [device_b], offline_event)))
        await asyncio.sleep(0.5)

        # --- rate limit (100 SYNC_EVENT/мин): получатель офлайн, поэтому кадров нет ---
        for _ in range(100):
            await ws_a2.send(json.dumps(event_for(device_a, [device_b])))
        await ws_a2.send(json.dumps(event_for(device_a, [device_b])))
        error = await receive_json(ws_a2, timeout=5)
        check("sync event rate limit returns RATE_LIMIT_EXCEEDED",
              error.get("type") == "ERROR" and error.get("code") == "RATE_LIMIT_EXCEEDED", error)
        await ws_a2.send(json.dumps({"type": "PING", "timestamp": 55}))
        # Лимит теперь на устройство (SEC-08), поэтому в очереди может остаться
        # несколько ERROR от отклонённых событий — ищем PONG среди кадров.
        pong = None
        for _ in range(10):
            message = await receive_json(ws_a2)
            if message.get("type") == "PONG":
                pong = message
                break
        check("connection survives rate limit", pong is not None, pong)

    status, pending = http("GET", f"/v1/sync/pending?deviceId={device_b}", token=token_b)
    check("offline packet waits in queue", status == 200 and pending["totalCount"] >= 1, pending)

    async with websockets.connect(f"{WS}/v1/sync?deviceId={device_b}",
                                  additional_headers={"Authorization": "Bearer " + token_b}) as ws_b2:
        first = await receive_json(ws_b2)
        check("pending delivered as first frame", first.get("type") == "SYNC_EVENT", first)

    # --- ошибки авторизации ---
    try:
        async with websockets.connect(f"{WS}/v1/sync?deviceId={device_b}",
                                      additional_headers={"Authorization": "Bearer invalid"}):
            check("invalid token rejected before upgrade", False, "connection accepted")
    except Exception as error:  # noqa: BLE001
        check("invalid token rejected before upgrade", "401" in str(error) or "rejected" in str(error).lower(), str(error))

    status, error_body = http("GET", f"/v1/sync/pending?deviceId={uuid.uuid4()}", token=token_b)
    check("foreign deviceId rejected with device_mismatch",
          status == 403 and error_body["error"] == "device_mismatch", f"{status} {error_body}")

    status, body = http("GET", "/v1/devices", token=token_a)
    check("devices list contains two permanent devices",
          status == 200 and len(body["devices"]) == 2 and all(d["isOnline"] is False for d in body["devices"]), body)

    # --- i18n ---
    status, body = http("POST", "/v1/auth/register", {
        "email": email, "password": PASSWORD, "deviceId": str(uuid.uuid4()),
        "deviceName": "Dup", "osType": "Linux",
    }, headers={"Accept-Language": "ru-RU,ru;q=0.9"})
    check("localized error message (ru)",
          status == 409 and "уже" in body["message"], f"{status} {body}")

    failed = [name for name, ok, _ in results if not ok]
    print(f"\n{len(results) - len(failed)}/{len(results)} checks passed")
    if failed:
        print("FAILED:", failed)
        sys.exit(1)


if __name__ == "__main__":
    asyncio.run(main())
