import pytest
from pathlib import Path
from typing import Any

from photo_selector_toolbox.tools import AnalysisTool, ToolRegistry


@pytest.fixture(autouse=True)
def reset_tool_registry():
    """Save and restore the state of ToolRegistry._tools for each test to prevent leakage."""
    original_tools = dict(ToolRegistry._tools)
    ToolRegistry._tools.clear()
    yield
    ToolRegistry._tools.clear()
    ToolRegistry._tools.update(original_tools)


class MockTool(AnalysisTool):
    name = "mock_tool"
    display_name = "Mock Tool"

    def analyze(self, filepath: Path, **kwargs: Any) -> Any:
        return "mock_result"


class MockToolWithProperty(AnalysisTool):
    @property
    def name(self) -> str:
        return "mock_tool_prop"

    @property
    def display_name(self) -> str:
        return "Mock Tool Property"

    def analyze(self, filepath: Path, **kwargs: Any) -> Any:
        return "mock_result_prop"


def test_analysis_tool_cannot_be_instantiated():
    """Test that AnalysisTool cannot be instantiated directly."""
    with pytest.raises(TypeError):
        AnalysisTool()


def test_mock_tool_instantiation():
    """Test that a subclass of AnalysisTool can be instantiated."""
    tool = MockTool()
    assert tool.name == "mock_tool"
    assert tool.display_name == "Mock Tool"
    assert tool.analyze(Path("dummy.jpg")) == "mock_result"


def test_tool_registry_register_and_get():
    """Test registering a tool and retrieving it by name."""
    # Register the tool
    registered_class = ToolRegistry.register(MockTool)
    assert registered_class is MockTool

    # Get the tool
    retrieved_class = ToolRegistry.get("mock_tool")
    assert retrieved_class is MockTool


def test_tool_registry_register_with_property():
    """Test registering a tool that uses properties for name and display_name."""
    ToolRegistry.register(MockToolWithProperty)

    retrieved_class = ToolRegistry.get("mock_tool_prop")
    assert retrieved_class is MockToolWithProperty


def test_tool_registry_get_missing():
    """Test retrieving a missing tool raises KeyError."""
    with pytest.raises(KeyError):
        ToolRegistry.get("nonexistent_tool")


def test_tool_registry_all_tools():
    """Test that all_tools returns a dictionary of all registered tools."""
    ToolRegistry.register(MockTool)
    ToolRegistry.register(MockToolWithProperty)

    tools = ToolRegistry.all_tools()
    assert len(tools) == 2
    assert tools["mock_tool"] is MockTool
    assert tools["mock_tool_prop"] is MockToolWithProperty


def test_abstract_methods_pass():
    """Test that the abstract methods just pass to ensure full coverage."""
    from photo_selector_toolbox.tools import AnalysisTool
    from pathlib import Path

    assert AnalysisTool.name.fget(None) is None
    assert AnalysisTool.display_name.fget(None) is None
    assert AnalysisTool.analyze(None, Path("dummy.jpg")) is None
