"""Lightweight JSON Schema validator (draft 2020-12 subset, no external dependencies)."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any


SCHEMAS_DIR = Path(__file__).resolve().parent.parent / "schemas"
_SCHEMA_CACHE: dict[str, dict[str, Any]] = {}


def load_schema(schema_name: str) -> dict[str, Any]:
    """Load `<schema_name>.schema.json` from `scripts/lab/schemas/`."""
    name = schema_name[:-12] if schema_name.endswith(".schema.json") else schema_name
    if name not in _SCHEMA_CACHE:
        path = SCHEMAS_DIR / f"{name}.schema.json"
        if not path.is_file():
            raise FileNotFoundError(f"Unknown schema {schema_name!r} (expected {path})")
        _SCHEMA_CACHE[name] = json.loads(path.read_text(encoding="utf-8"))
    return _SCHEMA_CACHE[name]


def _matches_type(val: Any, expected: str) -> bool:
    if expected == "null":
        return val is None
    if expected == "boolean":
        return isinstance(val, bool)
    if expected == "integer":
        return isinstance(val, int) and not isinstance(val, bool)
    if expected == "number":
        return isinstance(val, (int, float)) and not isinstance(val, bool)
    if expected == "string":
        return isinstance(val, str)
    if expected == "array":
        return isinstance(val, list)
    if expected == "object":
        return isinstance(val, dict)
    return False


def _validate_node(val: Any, schema: dict[str, Any], path: str, errors: list[str]) -> None:
    expected_type = schema.get("type")
    if expected_type is not None:
        allowed_types = [expected_type] if isinstance(expected_type, str) else list(expected_type)
        if not any(_matches_type(val, t) for t in allowed_types):
            errors.append(f"{path}: expected type {expected_type}, got {type(val).__name__}")
            return

    if "enum" in schema and val not in schema["enum"]:
        errors.append(f"{path}: value {val!r} not in enum {schema['enum']}")

    if isinstance(val, dict):
        for req_key in schema.get("required", []):
            if req_key not in val:
                errors.append(f"{path}: missing required key {req_key!r}")
        props = schema.get("properties", {})
        for k, v in val.items():
            if k in props:
                _validate_node(v, props[k], f"{path}.{k}", errors)

    elif isinstance(val, list):
        min_items = schema.get("minItems")
        max_items = schema.get("maxItems")
        if min_items is not None and len(val) < min_items:
            errors.append(f"{path}: length {len(val)} < minItems {min_items}")
        if max_items is not None and len(val) > max_items:
            errors.append(f"{path}: length {len(val)} > maxItems {max_items}")
        item_schema = schema.get("items")
        if isinstance(item_schema, dict):
            for idx, item in enumerate(val):
                _validate_node(item, item_schema, f"{path}[{idx}]", errors)


def validate(obj: Any, schema_name: str | dict[str, Any]) -> list[str]:
    """Validate `obj` against `schema_name` (or an inline schema dict); return error strings."""
    schema = load_schema(schema_name) if isinstance(schema_name, str) else schema_name
    errors: list[str] = []
    _validate_node(obj, schema, "$", errors)
    return errors
