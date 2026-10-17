#!/usr/bin/env python3
"""One-off SOOD discovery probe (format per RoonLabs/node-roon-api sood.js).

Sends a SOOD v2 query for the Roon Core service to 239.255.90.90:9003 and to the
subnet broadcast of every IPv4 interface, then prints every parseable reply.
Usage: sood-probe.py [seconds]
"""
import fcntl, select, socket, struct, sys, time, uuid

PORT, MCAST = 9003, "239.255.90.90"
SERVICE_ID = "00720724-5143-4a9b-abac-0e50cba674bb"


def encode(kind, props):
    out = b"SOOD" + bytes([2]) + kind.encode()
    for k, v in props.items():
        kb, vb = k.encode(), v.encode()
        out += bytes([len(kb)]) + kb + struct.pack(">H", len(vb)) + vb
    return out


def parse(buf):
    if buf[:4] != b"SOOD" or len(buf) < 6 or buf[4] != 2:
        return None
    kind, pos, props = chr(buf[5]), 6, {}
    while pos < len(buf):
        n = buf[pos]; pos += 1
        if n == 0 or pos + n > len(buf):
            return None
        name = buf[pos:pos + n].decode("utf8", "replace"); pos += n
        if pos + 2 > len(buf):
            return None
        (vl,) = struct.unpack(">H", buf[pos:pos + 2]); pos += 2
        if vl == 0xFFFF:
            val = None
        else:
            if pos + vl > len(buf):
                return None
            val = buf[pos:pos + vl].decode("utf8", "replace"); pos += vl
        props[name] = val
    return kind, props


def interfaces():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    for _, name in socket.if_nameindex():
        try:
            ifr = struct.pack("256s", name.encode()[:15])
            ip = socket.inet_ntoa(fcntl.ioctl(s, 0x8915, ifr)[20:24])
            mask = socket.inet_ntoa(fcntl.ioctl(s, 0x891B, ifr)[20:24])
        except OSError:
            continue
        if ip.startswith("127."):
            continue
        ipi = struct.unpack(">I", socket.inet_aton(ip))[0]
        mi = struct.unpack(">I", socket.inet_aton(mask))[0]
        yield name, ip, socket.inet_ntoa(struct.pack(">I", ipi | (~mi & 0xFFFFFFFF)))


def main():
    secs = float(sys.argv[1]) if len(sys.argv) > 1 else 6
    socks = []
    for name, ip, bcast in interfaces():
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        s.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_TTL, 1)
        s.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_IF, socket.inet_aton(ip))
        s.bind((ip, 0))
        socks.append((name, ip, bcast, s))
        print(f"iface {name} ip={ip} broadcast={bcast} src_port={s.getsockname()[1]}")
    if not socks:
        print("no usable IPv4 interface"); return 1
    pkt = encode("Q", {"query_service_id": SERVICE_ID, "_tid": str(uuid.uuid4())})
    end, sent, seen = time.time() + secs, 0, 0
    while time.time() < end:
        if sent < 3:  # three query rounds, 1 s apart
            for name, ip, bcast, s in socks:
                for dst in (MCAST, bcast):
                    s.sendto(pkt, (dst, PORT))
                    print(f"sent query via {name} ({ip}) -> {dst}:{PORT} ({len(pkt)} bytes)")
            sent += 1
            nxt = time.time() + 1
        else:
            nxt = end
        while time.time() < min(nxt, end):
            r, _, _ = select.select([x[3] for x in socks], [], [], 0.2)
            for s in r:
                data, addr = s.recvfrom(65535)
                p = parse(data)
                if p is None:
                    print(f"reply from {addr}: unparseable {data[:32]!r}")
                elif p[1].get("_tid") and p[0] == "Q":
                    continue  # our own query looped back
                else:
                    seen += 1
                    print(f"reply from {addr[0]}:{addr[1]} type={p[0]} props={p[1]}")
    print(f"done: {seen} reply packet(s)")
    return 0


sys.exit(main())
