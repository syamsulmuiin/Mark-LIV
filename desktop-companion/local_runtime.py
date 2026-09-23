"""Local execution bridge for MARK LIV desktop companions.
Runs the existing MARK LIV device-side tools on the companion machine so the
server refactor does not remove established computer/file/browser capabilities.
"""
from __future__ import annotations
import importlib, sys
from pathlib import Path
RUNTIME = Path(__file__).resolve().parent / "runtime"
if str(RUNTIME) not in sys.path:
    sys.path.insert(0, str(RUNTIME))

TOOLS = {
    "open_app": ("actions.open_app", "open_app"),
    "computer_control": ("actions.computer_control", "computer_control"),
    "computer_settings": ("actions.computer_settings", "computer_settings"),
    "desktop_control": ("actions.desktop", "desktop_control"),
    "file_controller": ("actions.file_controller", "file_controller"),
    "browser_control": ("actions.browser_control", "browser_control"),
    "screen_processor": ("actions.screen_processor", "screen_processor"),
    "send_message": ("actions.send_message", "send_message"),
    "system_monitor": ("actions.system_monitor", "system_monitor"),
}

def invoke(tool: str, parameters: dict | None = None):
    if tool not in TOOLS:
        raise ValueError(f"unsupported local tool: {tool}")
    module_name, handler_name = TOOLS[tool]
    module = importlib.import_module(module_name)
    handler = getattr(module, handler_name)
    return handler(parameters=parameters or {}, response=None, player=None, session_memory=None)
