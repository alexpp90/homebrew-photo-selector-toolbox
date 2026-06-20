try:
    from importlib.metadata import version
    __version__ = version("photo-selector-toolbox")
except Exception:
    __version__ = "0.1.0"
