from abc import ABC, abstractmethod
from pathlib import Path
from typing import Dict, Type, Any


class AnalysisTool(ABC):
    """Base class for all image analysis tools."""

    @property
    @abstractmethod
    def name(self) -> str:
        """Unique identifier, e.g., 'sharpness'."""
        pass

    @property
    @abstractmethod
    def display_name(self) -> str:
        """Human-readable name, e.g., 'Sharpness Analysis'."""
        pass

    @abstractmethod
    def analyze(self, filepath: Path, **kwargs: Any) -> Any:
        """Run analysis on a single file. Returns a numeric score or a tuple/dict of results."""
        pass


class ToolRegistry:
    """Central registry for analysis tools."""
    _tools: Dict[str, Type[AnalysisTool]] = {}

    @classmethod
    def register(cls, tool_class: Type[AnalysisTool]) -> Type[AnalysisTool]:
        name = getattr(tool_class, "name")
        if isinstance(name, property):
            name_val = name.fget(None)  # type: ignore
        else:
            name_val = name
        cls._tools[name_val] = tool_class
        return tool_class

    @classmethod
    def get(cls, name: str) -> Type[AnalysisTool]:
        return cls._tools[name]

    @classmethod
    def all_tools(cls) -> Dict[str, Type[AnalysisTool]]:
        return dict(cls._tools)
