"""Dataset generators for CircleNet - Vectorized for high performance."""

from pathlib import Path

import numpy as np
from numpy.random import Generator
from tqdm import tqdm

from config_loader import Config
from utils import generate_nicknames_batch, generate_job_titles_batch, generate_descriptions_batch


# =============================================================================
# CircleNetPage (Users) Generator - Vectorized
# =============================================================================

def generate_users(config: Config, rng: Generator, output_file: Path) -> None:
    """Generate CircleNetPage dataset with vectorized operations."""
    num_users = config.sizes.num_users
    chunk_size = config.output.chunk_size

    hobby_names = [h.name for h in config.users.hobbies]
    hobby_weights = np.array([h.weight for h in config.users.hobbies], dtype=np.float64)
    hobby_weights /= hobby_weights.sum()

    num_regions = config.users.regions.max_code - config.users.regions.min_code + 1
    region_weights = np.arange(1, num_regions + 1, dtype=np.float64) ** (-config.users.regions.distribution_alpha)
    region_weights /= region_weights.sum()

    with open(output_file, "w", encoding="utf-8") as f:
        user_id = 1
        first_chunk = True
        pbar = tqdm(total=num_users, desc="Users", unit="rows")

        while user_id <= num_users:
            batch_size = min(chunk_size, num_users - user_id + 1)

            # Vectorized generation
            ids = np.arange(user_id, user_id + batch_size)
            region_codes = rng.choice(num_regions, size=batch_size, p=region_weights) + config.users.regions.min_code
            hobby_indices = rng.choice(len(hobby_names), size=batch_size, p=hobby_weights)
            hobbies = np.array(hobby_names)[hobby_indices]

            nicknames = generate_nicknames_batch(rng, batch_size, config.users.nickname_min_length, config.users.nickname_max_length)
            job_titles = generate_job_titles_batch(rng, batch_size, config.users.jobtitle_min_length, config.users.jobtitle_max_length)

            # Build CSV rows vectorized
            lines = [f"{ids[i]},{nicknames[i]},{job_titles[i]},{region_codes[i]},{hobbies[i]}"
                     for i in range(batch_size)]

            if not first_chunk:
                f.write("\n")
            f.write("\n".join(lines))
            first_chunk = False
            user_id += batch_size
            pbar.update(batch_size)

        pbar.close()

    print(f"Generated {num_users:,} users -> {output_file}")


# =============================================================================
# Follows Generator - Fully Vectorized
# =============================================================================

def generate_follows(config: Config, rng: Generator, output_file: Path) -> None:
    """Generate Follows dataset - fully vectorized, no Python loops."""
    num_users = config.sizes.num_users
    num_follows = config.sizes.num_follows
    chunk_size = min(config.output.chunk_size, 500000)  # Larger chunks for efficiency

    # Popularity weights for being followed (power-law)
    popularity_weights = np.arange(1, num_users + 1, dtype=np.float64) ** (-config.follows.popularity_alpha)
    popularity_weights /= popularity_weights.sum()

    templates = config.follows.description_templates
    num_templates = len(templates)

    with open(output_file, "w", encoding="utf-8") as f:
        generated = 0
        seen_pairs = set()
        first_chunk = True
        pbar = tqdm(total=num_follows, desc="Follows", unit="rows")

        while generated < num_follows:
            # Generate more candidates than needed (oversample to handle duplicates)
            batch_target = min(chunk_size, num_follows - generated)
            oversample = int(batch_target * 1.3) + 1000  # 30% extra for duplicates

            # Vectorized: generate all follower IDs (uniform)
            id1_arr = rng.integers(1, num_users + 1, size=oversample)

            # Vectorized: generate all followed IDs (power-law weighted)
            id2_arr = rng.choice(num_users, size=oversample, p=popularity_weights) + 1

            # Vectorized: filter self-follows
            valid_mask = id1_arr != id2_arr
            id1_arr = id1_arr[valid_mask]
            id2_arr = id2_arr[valid_mask]

            # Filter duplicates (need to check against seen)
            pairs = list(zip(id1_arr, id2_arr))
            unique_pairs = []
            for p in pairs:
                if p not in seen_pairs:
                    seen_pairs.add(p)
                    unique_pairs.append(p)
                    if len(unique_pairs) >= batch_target:
                        break

            if not unique_pairs:
                continue

            batch_size = len(unique_pairs)
            id1_final = np.array([p[0] for p in unique_pairs])
            id2_final = np.array([p[1] for p in unique_pairs])

            # Vectorized: generate timestamps
            timestamps = rng.integers(config.time.min_timestamp, config.time.max_timestamp + 1, size=batch_size)

            # Vectorized: generate description indices and get descriptions
            desc_indices = rng.integers(0, num_templates, size=batch_size)
            descriptions = generate_descriptions_batch(rng, templates, desc_indices, batch_size)

            # Vectorized: generate col_rel IDs
            col_rels = np.arange(generated + 1, generated + batch_size + 1)

            # Build lines
            lines = [f"{col_rels[i]},{id1_final[i]},{id2_final[i]},{timestamps[i]},{descriptions[i]}"
                     for i in range(batch_size)]

            if not first_chunk:
                f.write("\n")
            f.write("\n".join(lines))
            first_chunk = False
            generated += batch_size
            pbar.update(batch_size)

        pbar.close()

    print(f"Generated {generated:,} follows -> {output_file}")


# =============================================================================
# Follows Generator - Ultra Fast (No Duplicate Check)
# =============================================================================

def generate_follows_fast(config: Config, rng: Generator, output_file: Path) -> None:
    """Generate Follows dataset - maximum speed, allows rare duplicates."""
    num_users = config.sizes.num_users
    num_follows = config.sizes.num_follows
    chunk_size = 1000000  # 1M rows per chunk

    # Popularity weights
    popularity_weights = np.arange(1, num_users + 1, dtype=np.float64) ** (-config.follows.popularity_alpha)
    popularity_weights /= popularity_weights.sum()

    templates = np.array(config.follows.description_templates)
    num_templates = len(templates)

    with open(output_file, "w", encoding="utf-8") as f:
        generated = 0
        first_chunk = True
        pbar = tqdm(total=num_follows, desc="Follows (fast)", unit="rows")

        while generated < num_follows:
            batch_size = min(chunk_size, num_follows - generated)

            # All vectorized - no Python loops
            id1_arr = rng.integers(1, num_users + 1, size=batch_size)
            id2_arr = rng.choice(num_users, size=batch_size, p=popularity_weights) + 1

            # Fix self-follows by shifting id2
            self_follow_mask = id1_arr == id2_arr
            id2_arr[self_follow_mask] = (id2_arr[self_follow_mask] % num_users) + 1

            timestamps = rng.integers(config.time.min_timestamp, config.time.max_timestamp + 1, size=batch_size)
            desc_indices = rng.integers(0, num_templates, size=batch_size)
            descriptions = templates[desc_indices]
            col_rels = np.arange(generated + 1, generated + batch_size + 1)

            # Vectorized string building with numpy
            lines = np.char.add(col_rels.astype(str), ',')
            lines = np.char.add(lines, id1_arr.astype(str))
            lines = np.char.add(lines, ',')
            lines = np.char.add(lines, id2_arr.astype(str))
            lines = np.char.add(lines, ',')
            lines = np.char.add(lines, timestamps.astype(str))
            lines = np.char.add(lines, ',')
            lines = np.char.add(lines, descriptions)

            if not first_chunk:
                f.write("\n")
            f.write("\n".join(lines))
            first_chunk = False
            generated += batch_size
            pbar.update(batch_size)

        pbar.close()

    print(f"Generated {generated:,} follows -> {output_file}")


# =============================================================================
# ActivityLog Generator - Fully Vectorized
# =============================================================================

def generate_activities(config: Config, rng: Generator, output_file: Path) -> None:
    """Generate ActivityLog dataset - fully vectorized."""
    num_users = config.sizes.num_users
    num_activities = config.sizes.num_activities
    chunk_size = 1000000  # 1M rows per chunk

    action_names = np.array([a.name for a in config.activities.action_types])
    action_weights = np.array([a.weight for a in config.activities.action_types], dtype=np.float64)
    action_weights /= action_weights.sum()

    min_t = config.time.min_timestamp
    max_t = config.time.max_timestamp
    time_range = max_t - min_t
    decay = config.activities.time_decay_factor

    # Pre-compute user activity assignments
    inactive_mask = rng.random(num_users) < config.activities.inactive_user_pct
    active_user_ids = np.where(~inactive_mask)[0] + 1  # 1-indexed user IDs

    if len(active_user_ids) == 0:
        print("Warning: All users are inactive!")
        return

    with open(output_file, "w", encoding="utf-8") as f:
        generated = 0
        first_chunk = True
        pbar = tqdm(total=num_activities, desc="Activities", unit="rows")

        while generated < num_activities:
            batch_size = min(chunk_size, num_activities - generated)

            # Vectorized: pick random active users for ByWho
            by_who = rng.choice(active_user_ids, size=batch_size)

            # Vectorized: pick random users for WhatPage (any user)
            what_page = rng.integers(1, num_users + 1, size=batch_size)

            # Fix self-actions
            self_action_mask = by_who == what_page
            what_page[self_action_mask] = (what_page[self_action_mask] % num_users) + 1

            # Vectorized: action types
            action_indices = rng.choice(len(action_names), size=batch_size, p=action_weights)
            action_types = action_names[action_indices]

            # Vectorized: timestamps with decay (more recent = more likely)
            if decay > 0:
                u = rng.random(size=batch_size)
                normalized = 1 - (1 - u) ** (1 / (1 + decay))
                action_times = (min_t + normalized * time_range).astype(np.int64)
            else:
                action_times = rng.integers(min_t, max_t + 1, size=batch_size)

            action_ids = np.arange(generated + 1, generated + batch_size + 1)

            # Vectorized string building
            lines = np.char.add(action_ids.astype(str), ',')
            lines = np.char.add(lines, by_who.astype(str))
            lines = np.char.add(lines, ',')
            lines = np.char.add(lines, what_page.astype(str))
            lines = np.char.add(lines, ',')
            lines = np.char.add(lines, action_types)
            lines = np.char.add(lines, ',')
            lines = np.char.add(lines, action_times.astype(str))

            if not first_chunk:
                f.write("\n")
            f.write("\n".join(lines))
            first_chunk = False
            generated += batch_size
            pbar.update(batch_size)

        pbar.close()

    print(f"Generated {generated:,} activities -> {output_file}")
