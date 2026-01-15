# CLI Command Examples

Commands live under `cli/runtime/commands/`.

Each command module should define:

- `COMMAND`: the slash command name (e.g., `/prompt`)
- `handle(**kwargs)`: a callable invoked by the runtime command registry

Example: `cli/runtime/commands/example.py`

```python
from rich.console import Console

COMMAND = "/example"


def handle(*, console: Console, **_: object) -> None:
    console.print("Example command executed.")
```
