"""Entry point for the `deployae` console script — wires argparse subcommands to each
cli/*.py module's own add_arguments()/run()."""

from __future__ import annotations

import argparse
import sys

from deployae.charts import TierRequiredError
from deployae.cli import cleanup, deploy
from deployae.helm import HelmError
from deployae.kube import KubectlError

_COMMANDS = {
    "cleanup": cleanup,
    "deploy": deploy,
}


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="deployae", description="Deployment CLI for the agent-engine Helm charts"
    )
    subparsers = parser.add_subparsers(dest="command", required=True)
    for name, module in _COMMANDS.items():
        subparser = subparsers.add_parser(name, help=(module.__doc__ or "").splitlines()[0])
        module.add_arguments(subparser)
        subparser.set_defaults(handler=module.run)
    return parser


def main() -> None:
    args = build_parser().parse_args()
    try:
        args.handler(args)
    except (TierRequiredError, HelmError, KubectlError, ValueError, RuntimeError) as error:
        print(f"Error: {error}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
