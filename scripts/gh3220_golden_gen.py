#!/usr/bin/env python3
"""Generate golden vectors for GH3220 diff decoding.

Ports the nibble-diff encoding from device code
.claude/gh3220_protocol/c_to_mcu/demo_kernel_code/module/gh_protocol/gh_zip.c
(GH3X2X_GetRawDataDiff / GH3X2X_GetDataDiff).

Output lines: "last_hex|cur_hex|encoded_hex"
Each group starts from last=0 so vectors can be decoded sequentially.
"""
import random

MASK32 = 0xFFFFFFFF

def abs_diff_u32(a, b):
    a &= MASK32; b &= MASK32
    return (a - b) if a >= b else (b - a)

def sign_u32(a, b):
    a &= MASK32; b &= MASK32
    return 0 if a > b else 1

# 注意：编码仅在 32bit 差分内自洽（dtype <= 15 才能放入一个 nibble）。
def get_diff(channel_count, cur, last):
    nibbles = []
    for ch in range(channel_count):
        d = abs_diff_u32(cur[ch], last[ch])
        if d == 0:
            dtype = 0
        else:
            chnum = 0
            for k in range(8, -1, -1):
                if ((d >> (k * 4)) & 0x0F) != 0:
                    chnum = k
                    break
            dtype = chnum * 2 + sign_u32(cur[ch], last[ch])
        nibbles.append(dtype & 0x0F)
        for k in range(dtype // 2, -1, -1):
            nibbles.append((d >> (k * 4)) & 0x0F)
    out = bytearray()
    for i in range(0, len(nibbles), 2):
        hi = nibbles[i]
        lo = nibbles[i + 1] if i + 1 < len(nibbles) else 0
        out.append((hi << 4) | lo)
    return bytes(out)

def hex32(v):
    return "%08x" % (v & MASK32)

def gen(channel_count, frames, seed):
    rng = random.Random(seed)
    last = [0] * channel_count
    lines = []
    for _ in range(frames):
        cur = [rng.getrandbits(32) for _ in range(channel_count)]
        enc = get_diff(channel_count, cur, last)
        lines.append("%s|%s|%s" % (
            "".join(hex32(v) for v in last),
            "".join(hex32(v) for v in cur),
            enc.hex()))
        last = cur
    return lines

if __name__ == "__main__":
    for ch in (1, 2, 4):
        for line in gen(ch, 6, seed=ch):
            print(line)

