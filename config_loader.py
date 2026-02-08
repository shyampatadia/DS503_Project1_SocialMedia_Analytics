"""Configuration loading and validation for the dataset generator."""

from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import yaml


@dataclass
class OutputConfig:
    directory: str = "./output"
    chunk_size: int = 10000


@dataclass
class SizesConfig:
    num_users: int = 200000
    num_follows: int = 20000000
    num_activities: int = 10000000


@dataclass
class TimeConfig:
    max_timestamp: int = 8760
    min_timestamp: int = 0
    recent_window: int = 2160


@dataclass
class RegionConfig:
    min_code: int = 1
    max_code: int = 50
    distribution_alpha: float = 1.5


@dataclass
class HobbyEntry:
    name: str
    weight: int


@dataclass
class UsersConfig:
    nickname_min_length: int = 10
    nickname_max_length: int = 20
    jobtitle_min_length: int = 10
    jobtitle_max_length: int = 20
    regions: RegionConfig = field(default_factory=RegionConfig)
    hobbies: list[HobbyEntry] = field(default_factory=list)


@dataclass
class FollowsConfig:
    popularity_alpha: float = 2.0
    reciprocity_probability: float = 0.3
    min_zero_followers_pct: float = 0.05
    description_templates: list[str] = field(default_factory=list)


@dataclass
class ActionTypeEntry:
    name: str
    weight: int


@dataclass
class ActivityTierEntry:
    name: str
    pct: float
    avg_actions: int


@dataclass
class ActivitiesConfig:
    action_types: list[ActionTypeEntry] = field(default_factory=list)
    inactive_user_pct: float = 0.10
    recently_inactive_pct: float = 0.15
    activity_tiers: list[ActivityTierEntry] = field(default_factory=list)
    time_decay_factor: float = 0.7


@dataclass
class Config:
    seed: int = 42
    output: OutputConfig = field(default_factory=OutputConfig)
    sizes: SizesConfig = field(default_factory=SizesConfig)
    time: TimeConfig = field(default_factory=TimeConfig)
    users: UsersConfig = field(default_factory=UsersConfig)
    follows: FollowsConfig = field(default_factory=FollowsConfig)
    activities: ActivitiesConfig = field(default_factory=ActivitiesConfig)


def _parse_hobby_entries(hobbies_data: list[dict[str, Any]]) -> list[HobbyEntry]:
    return [HobbyEntry(name=h["name"], weight=h["weight"]) for h in hobbies_data]


def _parse_action_types(actions_data: list[dict[str, Any]]) -> list[ActionTypeEntry]:
    return [ActionTypeEntry(name=a["name"], weight=a["weight"]) for a in actions_data]


def _parse_activity_tiers(tiers_data: list[dict[str, Any]]) -> list[ActivityTierEntry]:
    return [
        ActivityTierEntry(name=t["name"], pct=t["pct"], avg_actions=t["avg_actions"])
        for t in tiers_data
    ]


def load_config(config_path: str | Path) -> Config:
    """Load configuration from a YAML file."""
    config_path = Path(config_path)

    with open(config_path, "r", encoding="utf-8") as f:
        data = yaml.safe_load(f)

    # Parse nested configurations
    output_cfg = OutputConfig(**data.get("output", {}))
    sizes_cfg = SizesConfig(**data.get("sizes", {}))
    time_cfg = TimeConfig(**data.get("time", {}))

    # Parse users config
    users_data = data.get("users", {})
    regions_data = users_data.pop("regions", {})
    hobbies_data = users_data.pop("hobbies", [])
    users_cfg = UsersConfig(
        regions=RegionConfig(**regions_data),
        hobbies=_parse_hobby_entries(hobbies_data),
        **users_data,
    )

    # Parse follows config
    follows_cfg = FollowsConfig(**data.get("follows", {}))

    # Parse activities config
    activities_data = data.get("activities", {})
    action_types_data = activities_data.pop("action_types", [])
    tiers_data = activities_data.pop("activity_tiers", [])
    activities_cfg = ActivitiesConfig(
        action_types=_parse_action_types(action_types_data),
        activity_tiers=_parse_activity_tiers(tiers_data),
        **activities_data,
    )

    return Config(
        seed=data.get("seed", 42),
        output=output_cfg,
        sizes=sizes_cfg,
        time=time_cfg,
        users=users_cfg,
        follows=follows_cfg,
        activities=activities_cfg,
    )
