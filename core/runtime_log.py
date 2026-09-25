"""Bounded stdout/stderr logging for the long-running headless server."""
from __future__ import annotations

import io
import os
import sys
import threading
from pathlib import Path

DEFAULT_MAX_BYTES = 5 * 1024 * 1024
DEFAULT_BACKUPS = 5


class RotatingTextStream(io.TextIOBase):
    """Small print/traceback-compatible rotating text stream.

    Rotation is size based: error.log -> error.log.1 -> ... .N.  It intentionally
    implements only the TextIO methods used by print() and traceback so the
    existing console/logging behaviour does not need to be rewritten.
    """
    def __init__(self, path: Path, max_bytes: int = DEFAULT_MAX_BYTES, backups: int = DEFAULT_BACKUPS):
        self.path = Path(path)
        self.max_bytes = max(64 * 1024, int(max_bytes))
        self.backups = max(1, int(backups))
        self._lock = threading.RLock()
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self._file = self.path.open("a", encoding="utf-8", errors="replace", buffering=1)

    @property
    def encoding(self):
        return "utf-8"

    def writable(self):
        return True

    def isatty(self):
        return False

    def fileno(self):
        return self._file.fileno()

    def _rollover_if_needed(self, incoming: str):
        try:
            current = self.path.stat().st_size
        except OSError:
            current = 0
        if current + len(incoming.encode("utf-8", errors="replace")) <= self.max_bytes:
            return
        self._file.flush()
        self._file.close()
        oldest = self.path.with_name(f"{self.path.name}.{self.backups}")
        oldest.unlink(missing_ok=True)
        for idx in range(self.backups - 1, 0, -1):
            src = self.path.with_name(f"{self.path.name}.{idx}")
            dst = self.path.with_name(f"{self.path.name}.{idx + 1}")
            if src.exists():
                os.replace(src, dst)
        if self.path.exists():
            os.replace(self.path, self.path.with_name(f"{self.path.name}.1"))
        self._file = self.path.open("a", encoding="utf-8", errors="replace", buffering=1)

    def write(self, text):
        if not text:
            return 0
        if not isinstance(text, str):
            text = str(text)
        with self._lock:
            self._rollover_if_needed(text)
            self._file.write(text)
            self._file.flush()
        return len(text)

    def flush(self):
        with self._lock:
            self._file.flush()


def configure_runtime_log(path: Path, max_bytes: int | None = None, backups: int | None = None):
    """Redirect worker stdout/stderr to one bounded rotating runtime log."""
    max_bytes = max_bytes or int(os.getenv("MARK_LIV_LOG_MAX_BYTES", DEFAULT_MAX_BYTES))
    backups = backups or int(os.getenv("MARK_LIV_LOG_BACKUPS", DEFAULT_BACKUPS))
    stream = RotatingTextStream(path, max_bytes=max_bytes, backups=backups)
    sys.stdout = stream
    sys.stderr = stream
    return stream
