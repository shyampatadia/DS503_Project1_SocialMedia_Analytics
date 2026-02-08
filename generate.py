"""
CircleNet Dataset Generator - Main entry point
Run: python generate.py --config config/small_config.yaml
"""

import argparse
import sys
import time
from pathlib import Path

import numpy as np
import yaml

from config_loader import load_config
from generators import generate_users, generate_follows, generate_follows_fast, generate_activities


def main() -> int:
    parser = argparse.ArgumentParser(
        description="CircleNet Dataset Generator - Generate synthetic social network data"
    )
    parser.add_argument(
        "--config", "-c",
        type=str,
        required=True,
        help="Path to configuration YAML file",
    )
    parser.add_argument(
        "--output", "-o",
        type=str,
        help="Override output directory from config",
    )
    parser.add_argument(
        "--seed", "-s",
        type=int,
        help="Override random seed from config",
    )
    parser.add_argument(
        "--users-only",
        action="store_true",
        help="Generate only CircleNetPage dataset",
    )
    parser.add_argument(
        "--follows-only",
        action="store_true",
        help="Generate only Follows dataset",
    )
    parser.add_argument(
        "--activities-only",
        action="store_true",
        help="Generate only ActivityLog dataset",
    )
    parser.add_argument(
        "--fast",
        action="store_true",
        help="Use fast mode (skips duplicate checking in follows, ~20x faster)",
    )

    args = parser.parse_args()

    # Load configuration
    config_path = Path(args.config)
    if not config_path.exists():
        print(f"Error: Config file not found: {config_path}")
        return 1

    print(f"Loading configuration from: {config_path}")
    config = load_config(config_path)

    # Override config with CLI args
    if args.output:
        config.output.directory = args.output
    if args.seed is not None:
        config.seed = args.seed

    # Setup output directory
    output_dir = Path(config.output.directory)
    output_dir.mkdir(parents=True, exist_ok=True)

    # Initialize random generator with seed
    rng = np.random.default_rng(config.seed)

    print(f"Random seed: {config.seed}")
    print(f"Output directory: {output_dir}")
    print(f"Configuration:")
    print(f"  - Users: {config.sizes.num_users:,}")
    print(f"  - Follows: {config.sizes.num_follows:,}")
    print(f"  - Activities: {config.sizes.num_activities:,}")
    print()

    # Determine which datasets to generate
    generate_all = not (args.users_only or args.follows_only or args.activities_only)

    total_start = time.time()

    # Generate CircleNetPage
    if generate_all or args.users_only:
        start = time.time()
        generate_users(config, rng, output_dir / "CircleNetPage.csv")
        print(f"  Completed in {time.time() - start:.2f}s\n")

    # Generate Follows
    if generate_all or args.follows_only:
        start = time.time()
        if args.fast:
            generate_follows_fast(config, rng, output_dir / "Follows.csv")
        else:
            generate_follows(config, rng, output_dir / "Follows.csv")
        print(f"  Completed in {time.time() - start:.2f}s\n")

    # Generate ActivityLog
    if generate_all or args.activities_only:
        start = time.time()
        generate_activities(config, rng, output_dir / "ActivityLog.csv")
        print(f"  Completed in {time.time() - start:.2f}s\n")

    print(f"Total generation time: {time.time() - total_start:.2f}s")
    print("Done!")

    return 0


if __name__ == "__main__":
    sys.exit(main())
