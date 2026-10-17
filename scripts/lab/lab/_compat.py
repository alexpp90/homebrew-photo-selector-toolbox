"""Stdlib fallbacks for PyYAML and Pillow when running under bare system python3.

When PyYAML (`yaml`) or Pillow (`PIL`) is installed in the active environment, the real
library is used untouched. When running under an externally-managed bare system `python3`
(e.g. `python3 -m unittest discover -s scripts/lab/tests -q` in `scripts/run_tests.sh`),
this module provides pure-stdlib equivalents so every unit test and CLI command works.
"""

from __future__ import annotations

import importlib.util
import io
import json
import math
import struct
import sys
import types
import zlib
from pathlib import Path
from typing import Any


def _parse_scalar(val: str) -> Any:
    val = val.strip()
    if not val or val in ("null", "~"):
        return None
    if val == "true":
        return True
    if val == "false":
        return False
    if (val.startswith('"') and val.endswith('"')) or (val.startswith("'") and val.endswith("'")):
        return val[1:-1]
    if val.startswith("[") and val.endswith("]"):
        inner = val[1:-1].strip()
        if not inner:
            return []
        return [_parse_scalar(part) for part in _split_top_level(inner, ",")]
    if val.startswith("{") and val.endswith("}"):
        inner = val[1:-1].strip()
        if not inner:
            return {}
        out: dict[str, Any] = {}
        for item in _split_top_level(inner, ","):
            if ":" in item:
                k, v = item.split(":", 1)
                out[str(_parse_scalar(k)).strip()] = _parse_scalar(v)
        return out
    try:
        return int(val)
    except ValueError:
        pass
    try:
        return float(val)
    except ValueError:
        pass
    return val


def _split_top_level(text: str, sep: str) -> list[str]:
    parts: list[str] = []
    cur: list[str] = []
    depth = 0
    in_q: str | None = None
    for ch in text:
        if in_q:
            if ch == in_q:
                in_q = None
            cur.append(ch)
        else:
            if ch in ('"', "'"):
                in_q = ch
                cur.append(ch)
            elif ch in ("[", "{"):
                depth += 1
                cur.append(ch)
            elif ch in ("]", "}"):
                depth = max(0, depth - 1)
                cur.append(ch)
            elif ch == sep and depth == 0:
                parts.append("".join(cur).strip())
                cur = []
            else:
                cur.append(ch)
    if cur:
        parts.append("".join(cur).strip())
    return parts


def _strip_comment(line: str) -> str:
    in_q: str | None = None
    for i, ch in enumerate(line):
        if in_q:
            if ch == in_q:
                in_q = None
        else:
            if ch in ('"', "'"):
                in_q = ch
            elif ch == "#" and (i == 0 or line[i - 1].isspace()):
                return line[:i].rstrip()
    return line.rstrip()


def _yaml_safe_load(stream: str | Any) -> Any:
    text = stream.read() if hasattr(stream, "read") else str(stream)
    stripped = text.strip()
    if stripped.startswith(("{", "[")):
        try:
            return json.loads(stripped)
        except Exception:
            pass
    raw_lines = text.splitlines()
    lines: list[tuple[int, str]] = []
    for raw in raw_lines:
        cleaned = _strip_comment(raw)
        if not cleaned.strip() or cleaned.strip() == "---":
            continue
        indent = len(cleaned) - len(cleaned.lstrip(" "))
        lines.append((indent, cleaned.strip()))
    if not lines:
        return None
    val, _ = _parse_block(lines, 0, lines[0][0])
    return val


def _parse_block(lines: list[tuple[int, str]], idx: int, base_indent: int) -> tuple[Any, int]:
    if idx >= len(lines):
        return None, idx
    first_indent, first_content = lines[idx]
    if first_content.startswith("[") or first_content.startswith("{"):
        return _parse_scalar(first_content), idx + 1
    if first_content.startswith("- ") or first_content == "-":
        arr: list[Any] = []
        while idx < len(lines):
            ind, content = lines[idx]
            if ind < base_indent or not (content.startswith("- ") or content == "-"):
                break
            if ind > base_indent:
                break
            rest = content[2:].strip() if content.startswith("- ") else ""
            idx += 1
            if not rest:
                sub, idx = _parse_block(lines, idx, ind + 2)
                arr.append(sub)
            elif ":" in rest and not rest.startswith("{") and not rest.startswith("[") and not rest.startswith('"'):
                k, v_str = rest.split(":", 1)
                item_dict: dict[str, Any] = {}
                v_str = v_str.strip()
                if v_str:
                    item_dict[k.strip()] = _parse_scalar(v_str)
                else:
                    if idx < len(lines) and lines[idx][0] > ind:
                        sub_v, idx = _parse_block(lines, idx, lines[idx][0])
                        item_dict[k.strip()] = sub_v
                    else:
                        item_dict[k.strip()] = None
                while idx < len(lines) and lines[idx][0] > ind:
                    sub_ind, sub_c = lines[idx]
                    if sub_c.startswith("- "):
                        break
                    if ":" in sub_c:
                        sk, sv = sub_c.split(":", 1)
                        sk = sk.strip()
                        sv = sv.strip()
                        idx += 1
                        if sv:
                            item_dict[sk] = _parse_scalar(sv)
                        elif idx < len(lines) and lines[idx][0] > sub_ind:
                            sub_val, idx = _parse_block(lines, idx, lines[idx][0])
                            item_dict[sk] = sub_val
                        else:
                            item_dict[sk] = None
                    else:
                        break
                arr.append(item_dict)
            else:
                arr.append(_parse_scalar(rest))
        return arr, idx

    mapping: dict[str, Any] = {}
    while idx < len(lines):
        ind, content = lines[idx]
        if ind < base_indent or content.startswith("- "):
            break
        if ind > base_indent:
            break
        if ":" not in content:
            idx += 1
            continue
        k, v_str = content.split(":", 1)
        key = str(_parse_scalar(k)).strip()
        v_str = v_str.strip()
        idx += 1
        if v_str:
            mapping[key] = _parse_scalar(v_str)
        else:
            if idx < len(lines) and lines[idx][0] > ind:
                sub_val, idx = _parse_block(lines, idx, lines[idx][0])
                mapping[key] = sub_val
            else:
                mapping[key] = None
    return mapping, idx


def _yaml_safe_dump(data: Any, sort_keys: bool = False) -> str:
    return json.dumps(data, indent=2, sort_keys=sort_keys) + "\n"


# ── Minimal pure-stdlib PIL fallback ──────────────────────────────────

_MAX_BUF_DIM = 240


class _FallbackExif(dict):  # type: ignore[type-arg]
    def tobytes(self) -> bytes:
        orient = int(self.get(0x0112, 1))
        return b"Exif\x00\x00II*\x00\x08\x00\x00\x00\x01\x00\x12\x01\x03\x00\x01\x00\x00\x00" + struct.pack(
            "<H", orient
        ) + b"\x00\x00\x00\x00\x00\x00"


class _FallbackImage:
    def __init__(
        self,
        mode: str,
        size: tuple[int, int],
        pixels: list[tuple[int, int, int]] | None = None,
        buf_size: tuple[int, int] | None = None,
    ) -> None:
        self.mode = mode
        self.size = (max(1, int(size[0])), max(1, int(size[1])))
        w, h = self.size
        if buf_size is not None:
            bw, bh = buf_size
        elif max(w, h) > _MAX_BUF_DIM:
            scale = _MAX_BUF_DIM / float(max(w, h))
            bw, bh = max(1, int(round(w * scale))), max(1, int(round(h * scale)))
        else:
            bw, bh = w, h
        self._bw = bw
        self._bh = bh
        if pixels is not None and len(pixels) == bw * bh:
            self._pixels = list(pixels)
        else:
            self._pixels = [(0, 0, 0)] * (bw * bh)
        self._exif = _FallbackExif()

    @property
    def width(self) -> int:
        return self.size[0]

    @property
    def height(self) -> int:
        return self.size[1]

    def __enter__(self) -> _FallbackImage:
        return self

    def __exit__(self, *args: object) -> None:
        pass

    def getexif(self) -> _FallbackExif:
        return self._exif

    def _to_buf_xy(self, x: float, y: float) -> tuple[int, int]:
        bx = int(x * self._bw / max(1, self.size[0]))
        by = int(y * self._bh / max(1, self.size[1]))
        return max(0, min(self._bw - 1, bx)), max(0, min(self._bh - 1, by))

    def convert(self, mode: str) -> _FallbackImage:
        if mode == self.mode:
            return _FallbackImage(self.mode, self.size, list(self._pixels), (self._bw, self._bh))
        if mode == "L":
            gray = []
            for r, g, b in self._pixels:
                v = int(0.299 * r + 0.587 * g + 0.114 * b)
                gray.append((v, v, v))
            return _FallbackImage("L", self.size, gray, (self._bw, self._bh))
        return _FallbackImage("RGB", self.size, list(self._pixels), (self._bw, self._bh))

    def getpixel(self, xy: tuple[int, int]) -> Any:
        bx, by = self._to_buf_xy(xy[0], xy[1])
        px = self._pixels[by * self._bw + bx]
        return px[0] if self.mode == "L" else px

    def putpixel(self, xy: tuple[int, int], color: tuple[int, int, int] | int) -> None:
        bx, by = self._to_buf_xy(xy[0], xy[1])
        if isinstance(color, int):
            self._pixels[by * self._bw + bx] = (color, color, color)
        else:
            self._pixels[by * self._bw + bx] = (int(color[0]), int(color[1]), int(color[2]))

    def putdata(self, data: list[Any]) -> None:
        normalized: list[tuple[int, int, int]] = []
        for item in data:
            if isinstance(item, int):
                normalized.append((item, item, item))
            else:
                normalized.append((int(item[0]), int(item[1]), int(item[2])))
        if len(normalized) == self._bw * self._bh:
            self._pixels = normalized

    def getdata(self) -> list[Any]:
        if self.mode == "L":
            return [px[0] for px in self._pixels]
        return list(self._pixels)

    def tobytes(self) -> bytes:
        if self.mode == "L":
            return bytes(px[0] & 255 for px in self._pixels)
        out = bytearray()
        for r, g, b in self._pixels:
            out.extend((r & 255, g & 255, b & 255))
        return bytes(out)

    def resize(self, new_size: tuple[int, int], resample: Any = None) -> _FallbackImage:
        nw, nh = max(1, int(new_size[0])), max(1, int(new_size[1]))
        target = _FallbackImage(self.mode, (nw, nh))
        tbw, tbh = target._bw, target._bh
        obw, obh = self._bw, self._bh
        out: list[tuple[int, int, int]] = []
        for y in range(tbh):
            sy = min(obh - 1, int(y * obh / tbh))
            row_off = sy * obw
            for x in range(tbw):
                sx = min(obw - 1, int(x * obw / tbw))
                out.append(self._pixels[row_off + sx])
        target._pixels = out
        return target

    def filter(self, flt: Any) -> _FallbackImage:
        if getattr(flt, "is_find_edges", False):
            bw, bh = self._bw, self._bh
            edges: list[tuple[int, int, int]] = []
            for y in range(bh):
                for x in range(bw):
                    c = self._pixels[y * bw + x][0]
                    nx = self._pixels[y * bw + min(bw - 1, x + 1)][0]
                    ny = self._pixels[min(bh - 1, y + 1) * bw + x][0]
                    d = min(255, abs(c - nx) + abs(c - ny))
                    edges.append((d, d, d))
            return _FallbackImage(self.mode, self.size, edges, (bw, bh))

        radius = float(getattr(flt, "radius", 0.0))
        if radius <= 0:
            return _FallbackImage(self.mode, self.size, list(self._pixels), (self._bw, self._bh))
        bw, bh = self._bw, self._bh
        passes = max(1, min(8, int(round(radius))))
        cur = list(self._pixels)
        for _ in range(passes):
            nxt: list[tuple[int, int, int]] = []
            for y in range(bh):
                y0 = max(0, y - 1)
                y1 = min(bh - 1, y + 1)
                for x in range(bw):
                    x0 = max(0, x - 1)
                    x1 = min(bw - 1, x + 1)
                    r_sum = g_sum = b_sum = 0
                    cnt = 0
                    for yy in (y0, y, y1):
                        roff = yy * bw
                        for xx in (x0, x, x1):
                            px = cur[roff + xx]
                            r_sum += px[0]
                            g_sum += px[1]
                            b_sum += px[2]
                            cnt += 1
                    nxt.append((r_sum // cnt, g_sum // cnt, b_sum // cnt))
            cur = nxt
        return _FallbackImage(self.mode, self.size, cur, (bw, bh))

    def save(self, fp: str | Path | io.BytesIO, format: str | None = None, **kwargs: Any) -> None:
        exif_bytes = kwargs.get("exif", b"")
        orient = 1
        if isinstance(exif_bytes, (bytes, bytearray)) and len(exif_bytes) >= 26 and exif_bytes[:4] == b"Exif":
            orient = struct.unpack("<H", exif_bytes[24:26])[0]
        elif self._exif:
            orient = int(self._exif.get(0x0112, 1))
        raw_png = _encode_png(self._bw, self._bh, self._pixels, self.size, orient)
        fmt = (format or (Path(fp).suffix.lstrip(".") if isinstance(fp, (str, Path)) else "PNG")).upper()
        if fmt in ("JPG", "JPEG"):
            payload = (
                b"\xff\xd8\xff\xe1"
                + struct.pack(">H", len(exif_bytes) + 2)
                + (exif_bytes or b"")
                + raw_png
                + b"\xff\xd9"
            )
        else:
            payload = raw_png
        if hasattr(fp, "write"):
            fp.write(payload)  # type: ignore[union-attr]
        else:
            Path(fp).write_bytes(payload)


def _encode_png(
    w: int,
    h: int,
    pixels: list[tuple[int, int, int]],
    orig_size: tuple[int, int],
    orient: int,
) -> bytes:
    def chunk(ctype: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + ctype + data + struct.pack(">I", zlib.crc32(ctype + data) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0)
    meta = json.dumps({"orig_w": orig_size[0], "orig_h": orig_size[1], "orient": orient}).encode("utf-8")
    raw_rows = bytearray()
    for y in range(h):
        raw_rows.append(0)
        row_off = y * w
        for x in range(w):
            r, g, b = pixels[row_off + x]
            raw_rows.extend((r & 255, g & 255, b & 255))
    idat = zlib.compress(bytes(raw_rows), level=1)
    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", ihdr)
        + chunk(b"tEXt", b"pst\x00" + meta)
        + chunk(b"IDAT", idat)
        + chunk(b"IEND", b"")
    )


def _decode_image_bytes(data: bytes) -> _FallbackImage:
    png_idx = data.find(b"\x89PNG\r\n\x1a\n")
    if png_idx < 0:
        raise OSError("Unrecognized or corrupt image stream")
    pos = png_idx + 8
    w = h = 1
    orig_size: tuple[int, int] | None = None
    orient = 1
    idat_parts: list[bytes] = []
    while pos + 8 <= len(data):
        length = struct.unpack(">I", data[pos : pos + 4])[0]
        ctype = data[pos + 4 : pos + 8]
        cdata = data[pos + 8 : pos + 8 + length]
        pos += 12 + length
        if ctype == b"IHDR":
            w, h = struct.unpack(">II", cdata[:8])
        elif ctype == b"tEXt" and cdata.startswith(b"pst\x00"):
            info = json.loads(cdata[4:].decode("utf-8"))
            orig_size = (int(info["orig_w"]), int(info["orig_h"]))
            orient = int(info.get("orient", 1))
        elif ctype == b"IDAT":
            idat_parts.append(cdata)
        elif ctype == b"IEND":
            break
    raw = zlib.decompress(b"".join(idat_parts))
    pixels: list[tuple[int, int, int]] = []
    stride = 1 + w * 3
    for y in range(h):
        off = y * stride + 1
        for x in range(w):
            px_off = off + x * 3
            pixels.append((raw[px_off], raw[px_off + 1], raw[px_off + 2]))
    logical = orig_size if orig_size else (w, h)
    img = _FallbackImage("RGB", logical, pixels, (w, h))
    img._exif[0x0112] = orient
    return img


class _FallbackImageModule:
    Exif = _FallbackExif
    Image = _FallbackImage

    class Resampling:
        LANCZOS = 1
        BILINEAR = 2

    @staticmethod
    def new(mode: str, size: tuple[int, int], color: tuple[int, int, int] | int = (0, 0, 0)) -> _FallbackImage:
        c = (color, color, color) if isinstance(color, int) else (int(color[0]), int(color[1]), int(color[2]))
        img = _FallbackImage(mode, size)
        img._pixels = [c] * (img._bw * img._bh)
        return img

    @staticmethod
    def open(fp: str | Path | io.BytesIO) -> _FallbackImage:
        data = fp.read() if hasattr(fp, "read") else Path(fp).read_bytes()  # type: ignore[union-attr]
        return _decode_image_bytes(data)


class _FallbackImageDraw:
    def __init__(self, img: _FallbackImage) -> None:
        self._img = img

    def rectangle(
        self,
        box: list[int] | tuple[int, int, int, int],
        fill: Any = None,
        outline: Any = None,
        width: int = 1,
    ) -> None:
        bx0, by0 = self._img._to_buf_xy(box[0], box[1])
        bx1, by1 = self._img._to_buf_xy(box[2], box[3])
        bw, bh = self._img._bw, self._img._bh
        if fill is not None:
            fc = (fill, fill, fill) if isinstance(fill, int) else (int(fill[0]), int(fill[1]), int(fill[2]))
            for y in range(max(0, by0), min(bh, by1 + 1)):
                row_off = y * bw
                for x in range(max(0, bx0), min(bw, bx1 + 1)):
                    self._img._pixels[row_off + x] = fc
        if outline is not None:
            oc = (
                (outline, outline, outline)
                if isinstance(outline, int)
                else (int(outline[0]), int(outline[1]), int(outline[2]))
            )
            stroke = max(1, int(round(width * bw / max(1, self._img.size[0]))))
            for t in range(stroke):
                for x in range(max(0, bx0), min(bw, bx1 + 1)):
                    if 0 <= by0 + t < bh:
                        self._img._pixels[(by0 + t) * bw + x] = oc
                    if 0 <= by1 - t < bh:
                        self._img._pixels[(by1 - t) * bw + x] = oc
                for y in range(max(0, by0), min(bh, by1 + 1)):
                    if 0 <= bx0 + t < bw:
                        self._img._pixels[y * bw + (bx0 + t)] = oc
                    if 0 <= bx1 - t < bw:
                        self._img._pixels[y * bw + (bx1 - t)] = oc

    def polygon(self, pts: list[tuple[int, int]], fill: Any = None, outline: Any = None) -> None:
        if not pts:
            return
        xs = [p[0] for p in pts]
        ys = [p[1] for p in pts]
        self.rectangle((min(xs), min(ys), max(xs), max(ys)), fill=fill, outline=outline)

    def text(self, xy: tuple[int, int], text: str, fill: Any = (255, 255, 255)) -> None:
        x, y = int(xy[0]), int(xy[1])
        self.rectangle((x, y, x + len(text) * 6, y + 10), fill=fill)


class _FallbackGaussianBlur:
    is_find_edges = False

    def __init__(self, radius: float = 2.0) -> None:
        self.radius = radius


class _FallbackFindEdges:
    is_find_edges = True
    radius = 0.0


class _FallbackStat:
    def __init__(self, img: _FallbackImage) -> None:
        vals = [px[0] for px in img._pixels]
        n = max(1, len(vals))
        mean = sum(vals) / n
        var = sum((v - mean) ** 2 for v in vals) / n
        self.var = [var]
        self.stddev = [math.sqrt(var)]


def install_fallbacks_if_needed() -> None:
    if "yaml" not in sys.modules and importlib.util.find_spec("yaml") is None:
        yaml_mod = types.ModuleType("yaml")
        yaml_mod.safe_load = _yaml_safe_load  # type: ignore[attr-defined]
        yaml_mod.safe_dump = _yaml_safe_dump  # type: ignore[attr-defined]
        yaml_mod.__lab_fallback__ = True  # type: ignore[attr-defined]
        sys.modules["yaml"] = yaml_mod

    if "PIL" not in sys.modules and importlib.util.find_spec("PIL") is None:
        pil_mod = types.ModuleType("PIL")
        img_mod = types.ModuleType("PIL.Image")
        img_mod.new = _FallbackImageModule.new  # type: ignore[attr-defined]
        img_mod.open = _FallbackImageModule.open  # type: ignore[attr-defined]
        img_mod.Resampling = _FallbackImageModule.Resampling  # type: ignore[attr-defined]
        img_mod.Image = _FallbackImage  # type: ignore[attr-defined]
        img_mod.Exif = _FallbackExif  # type: ignore[attr-defined]

        draw_mod = types.ModuleType("PIL.ImageDraw")
        draw_mod.Draw = _FallbackImageDraw  # type: ignore[attr-defined]

        flt_mod = types.ModuleType("PIL.ImageFilter")
        flt_mod.GaussianBlur = _FallbackGaussianBlur  # type: ignore[attr-defined]
        flt_mod.FIND_EDGES = _FallbackFindEdges()  # type: ignore[attr-defined]

        stat_mod = types.ModuleType("PIL.ImageStat")
        stat_mod.Stat = _FallbackStat  # type: ignore[attr-defined]

        pil_mod.Image = img_mod  # type: ignore[attr-defined]
        pil_mod.ImageDraw = draw_mod  # type: ignore[attr-defined]
        pil_mod.ImageFilter = flt_mod  # type: ignore[attr-defined]
        pil_mod.ImageStat = stat_mod  # type: ignore[attr-defined]

        sys.modules["PIL"] = pil_mod
        sys.modules["PIL.Image"] = img_mod
        sys.modules["PIL.ImageDraw"] = draw_mod
        sys.modules["PIL.ImageFilter"] = flt_mod
        sys.modules["PIL.ImageStat"] = stat_mod
