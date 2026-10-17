"""Convert Android UiAutomator XML dumps into normalised `Tree` objects."""

from __future__ import annotations

import re
import xml.etree.ElementTree as ET
from pathlib import Path

from ..model import Node, Tree


_BOUNDS_RE = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


def parse_bounds_dp(bounds_str: str, scale: float) -> list[float]:
    """Parse `[x1,y1][x2,y2]` pixel bounds and convert to dp (`px / scale`)."""
    m = _BOUNDS_RE.match(bounds_str.strip())
    if not m:
        return [0.0, 0.0, 0.0, 0.0]
    x1, y1, x2, y2 = (int(m.group(i)) for i in range(1, 5))
    s = scale if scale > 0 else 1.0
    return [
        round(x1 / s, 2),
        round(y1 / s, 2),
        round(max(0, x2 - x1) / s, 2),
        round(max(0, y2 - y1) / s, 2),
    ]


def _strip_resource_prefix(res_id: str) -> str:
    if ":id/" in res_id:
        return res_id.split(":id/", 1)[1]
    return res_id


def _infer_role(cls_name: str, clickable: bool, text: str) -> str:
    short = cls_name.rsplit(".", 1)[-1] if cls_name else ""
    if short in ("Button", "ImageButton", "CompoundButton", "CheckBox", "Switch", "RadioButton"):
        return "button"
    if short == "ImageView":
        return "image"
    if short == "EditText":
        return "input"
    if short == "TextView":
        return "button" if clickable else "text"
    if clickable:
        return "button"
    if text:
        return "text"
    return "container"


def parse_uiautomator_xml(
    xml_source: Path | str,
    density_dpi: int = 320,
    overlay_ids: set[str] | None = None,
) -> Tree:
    """Parse UiAutomator XML (file path or XML string) into a normalised `Tree` in dp."""
    if isinstance(xml_source, Path) or (
        isinstance(xml_source, str) and not xml_source.lstrip().startswith("<")
    ):
        xml_text = Path(xml_source).read_text(encoding="utf-8")
    else:
        xml_text = xml_source

    scale = float(density_dpi) / 160.0 if density_dpi > 0 else 1.0
    overlays = overlay_ids or set()
    root = ET.fromstring(xml_text)

    nodes: list[Node] = []
    win_w = 0.0
    win_h = 0.0
    origin: list[float] | None = None
    counter = 0

    def walk(elem: ET.Element, nearest_named_parent: str | None) -> None:
        nonlocal counter, win_w, win_h, origin
        if elem.tag == "node":
            raw_res_id = elem.attrib.get("resource-id", "").strip()
            has_named_id = bool(raw_res_id)
            node_id = _strip_resource_prefix(raw_res_id) if has_named_id else f"#{counter}"
            counter += 1

            bounds = parse_bounds_dp(elem.attrib.get("bounds", "[0,0][0,0]"), scale)
            if win_w == 0.0 and win_h == 0.0 and bounds[2] > 0 and bounds[3] > 0:
                win_w, win_h = bounds[2], bounds[3]
                # uiautomator reports screen coordinates; a freeform window (DeX, ChromeOS)
                # starts away from (0,0), so make every node relative to the window.
                origin = [bounds[0], bounds[1]]
            if origin is not None:
                bounds = [round(bounds[0] - origin[0], 2), round(bounds[1] - origin[1], 2), bounds[2], bounds[3]]

            clickable = elem.attrib.get("clickable", "false") == "true"
            long_clickable = elem.attrib.get("long-clickable", "false") == "true"
            checkable = elem.attrib.get("checkable", "false") == "true"
            interactive = clickable or long_clickable or checkable

            text = elem.attrib.get("text", "")
            content_desc = elem.attrib.get("content-desc", "")
            cls_name = elem.attrib.get("class", "")
            role = _infer_role(cls_name, clickable, text)
            visible = bounds[2] > 0 and bounds[3] > 0

            nodes.append(
                Node(
                    id=node_id,
                    parent=nearest_named_parent,
                    role=role,
                    name=content_desc,
                    bounds=bounds,
                    interactive=interactive,
                    visible=visible,
                    overlay=node_id in overlays,
                    text=text,
                    clipped=None,
                )
            )
            next_parent = node_id if has_named_id else nearest_named_parent
        else:
            next_parent = nearest_named_parent

        for child in elem:
            walk(child, next_parent)

    walk(root, None)
    return Tree(
        platform="android",
        window={"w": win_w, "h": win_h, "unit": "dp", "scale": scale},
        nodes=nodes,
    )
