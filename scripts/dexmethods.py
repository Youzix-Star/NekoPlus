#!/usr/bin/env python3
"""List the methods of one class in a .dex file. Minimal DEX reader: header, string/type/proto/
method pools, class_defs, class_data_item. Enough to answer "is this method in the APK"."""
import struct, sys, io

def uleb(data, off):
    result = 0; shift = 0
    while True:
        b = data[off]; off += 1
        result |= (b & 0x7f) << shift
        if not (b & 0x80): break
        shift += 7
    return result, off

def read_strings(data):
    size, off = struct.unpack_from("<II", data, 56)
    out = []
    for i in range(size):
        s_off = struct.unpack_from("<I", data, off + 4 * i)[0]
        n, p = uleb(data, s_off)
        out.append(data[p:p + n].decode("utf-8", "replace"))
    return out

def main(path, want):
    data = open(path, "rb").read()
    if data[:4] != b"dex\n":
        print("not a dex"); return
    strings = read_strings(data)
    type_size, type_off = struct.unpack_from("<II", data, 64)
    types = [strings[struct.unpack_from("<I", data, type_off + 4 * i)[0]] for i in range(type_size)]
    proto_size, proto_off = struct.unpack_from("<II", data, 72)
    protos = []
    for i in range(proto_size):
        _, ret, params_off = struct.unpack_from("<III", data, proto_off + 12 * i)
        params = []
        if params_off:
            n = struct.unpack_from("<I", data, params_off)[0]
            params = [types[struct.unpack_from("<H", data, params_off + 4 + 2 * k)[0]] for k in range(n)]
        protos.append("(" + "".join(params) + ")" + types[ret])
    method_size, method_off = struct.unpack_from("<II", data, 88)
    methods = []
    for i in range(method_size):
        cls, proto, name = struct.unpack_from("<HHI", data, method_off + 8 * i)
        methods.append((types[cls], protos[proto], strings[name]))
    cls_size, cls_off = struct.unpack_from("<II", data, 96)
    found = False
    for i in range(cls_size):
        class_idx, _, _, _, _, _, class_data_off, _ = struct.unpack_from("<IIIIIIII", data, cls_off + 32 * i)
        if types[class_idx] != want or not class_data_off:
            continue
        found = True
        p = class_data_off
        sf, p = uleb(data, p); inf, p = uleb(data, p)
        dm, p = uleb(data, p); vm, p = uleb(data, p)
        for _ in range(sf + inf):
            _, p = uleb(data, p); _, p = uleb(data, p)
        for kind, count in (("direct", dm), ("virtual", vm)):
            idx = 0
            for _ in range(count):
                diff, p = uleb(data, p); acc, p = uleb(data, p)
                code, p = uleb(data, p)
                idx += diff
                c, proto, name = methods[idx]
                print(f"  {kind:7} {name}{proto if proto.startswith('(') else ''}  proto={proto}")
    if not found:
        print(f"  {want} not found")

if __name__ == "__main__":
    print(f"=== {sys.argv[2]} ===")
    main(sys.argv[1], sys.argv[2])
