"""`deployae seed infra|catalog|all` — seeds MongoDB infra config and/or REST's
model/agent catalog."""

from __future__ import annotations

import argparse

from deployae.seeding import catalog as catalog_seeding
from deployae.seeding import infra as infra_seeding


def add_arguments(parser: argparse.ArgumentParser) -> None:
    subparsers = parser.add_subparsers(dest="target", required=True)

    infra_parser = subparsers.add_parser("infra", help="Seed configs/infra/*.json into MongoDB")
    _add_common_flags(infra_parser)
    _add_infra_flags(infra_parser)
    infra_parser.set_defaults(handler=_run_infra)

    catalog_parser = subparsers.add_parser(
        "catalog", help="Seed configs/models and configs/agents into REST"
    )
    _add_common_flags(catalog_parser)
    catalog_parser.set_defaults(handler=_run_catalog)

    all_parser = subparsers.add_parser("all", help="Seed infra config, then the catalog")
    _add_common_flags(all_parser)
    _add_infra_flags(all_parser)
    all_parser.set_defaults(handler=_run_all)


def _add_common_flags(parser: argparse.ArgumentParser) -> None:
    parser.add_argument(
        "-t", "--tier", required=True, help="Tier whose resource names to reference"
    )
    parser.add_argument("-n", "--namespace", help="Override every chart's namespace")


def _add_infra_flags(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--agent-seed-node-count", type=int, default=3)
    parser.add_argument("--agent-cluster-name", default="agent")
    parser.add_argument("--pekko-snapshot-threshold", type=int, default=100)
    parser.add_argument("--title-model-id")
    parser.add_argument("--compaction-model-id")
    parser.add_argument("--evaluator-model-id")
    parser.add_argument("--embedding-model-id")


def _infra_options(args: argparse.Namespace) -> infra_seeding.SeedInfraOptions:
    return infra_seeding.SeedInfraOptions(
        agent_seed_node_count=args.agent_seed_node_count,
        agent_cluster_name=args.agent_cluster_name,
        pekko_snapshot_threshold=args.pekko_snapshot_threshold,
        title_model_id=args.title_model_id,
        compaction_model_id=args.compaction_model_id,
        evaluator_model_id=args.evaluator_model_id,
        embedding_model_id=args.embedding_model_id,
    )


def _run_infra(args: argparse.Namespace) -> None:
    infra_seeding.run(args.tier, args.namespace, _infra_options(args))


def _run_catalog(args: argparse.Namespace) -> None:
    catalog_seeding.run(args.tier, args.namespace)


def _run_all(args: argparse.Namespace) -> None:
    _run_infra(args)
    _run_catalog(args)


def run(args: argparse.Namespace) -> None:
    args.handler(args)
