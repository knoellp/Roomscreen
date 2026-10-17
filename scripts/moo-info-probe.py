#!/usr/bin/env python3
"""Read-only MOO probe: WebSocket upgrade on /api, then com.roonlabs.registry:1/info.
Does NOT register an extension. Usage: moo-info-probe.py HOST PORT"""
import base64, os, socket, struct, sys

host, port = sys.argv[1], int(sys.argv[2])
s = socket.create_connection((host, port), 5)
key = base64.b64encode(os.urandom(16)).decode()
s.sendall((f"GET /api HTTP/1.1\r\nHost: {host}:{port}\r\nUpgrade: websocket\r\n"
           f"Connection: Upgrade\r\nSec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n\r\n").encode())
buf = b""
while b"\r\n\r\n" not in buf:
    buf += s.recv(4096)
head, rest = buf.split(b"\r\n\r\n", 1)
print(head.decode())
payload = b"MOO/1 REQUEST com.roonlabs.registry:1/info\nRequest-Id: 0\n\n"
mask = os.urandom(4)
frame = bytes([0x82, 0x80 | len(payload)]) + mask + bytes(b ^ mask[i % 4] for i, b in enumerate(payload))
s.sendall(frame)
s.settimeout(5)
data = rest
while len(data) < 2:
    data += s.recv(4096)
ln = data[1] & 0x7F
off = 2
if ln == 126:
    while len(data) < 4: data += s.recv(4096)
    ln, off = struct.unpack(">H", data[2:4])[0], 4
while len(data) < off + ln:
    data += s.recv(4096)
print("opcode", data[0] & 0x0F)
print(data[off:off + ln].decode("utf8", "replace"))
