# CircleNet Dataset Generator

High-performance Python generator for synthetic social network data, designed for Hadoop + MapReduce analytics.

## Installation

```bash
pip install -r requirements.txt
```

## Usage

```bash
# Generate small test dataset
python generate.py --config config/small_config.yaml

# Generate full dataset (200K users, 20M follows, 10M activities)
python generate.py --config config/default_config.yaml

# Override output directory
python generate.py --config config/small_config.yaml --output ./my_output

# Override random seed
python generate.py --config config/small_config.yaml --seed 123

# Generate specific datasets only
python generate.py --config config/small_config.yaml --users-only
python generate.py --config config/small_config.yaml --follows-only
python generate.py --config config/small_config.yaml --activities-only
```

## Output Files

Three headerless CSV files are generated in `./output/`:

1. **CircleNetPage.csv** - User profiles
   - `ID,NickName,JobTitle,RegionCode,FavoriteHobby`

2. **Follows.csv** - Follow relationships
   - `ColRel,ID1,ID2,DateOfRelation,Description`

3. **ActivityLog.csv** - User activity log
   - `ActionId,ByWho,WhatPage,ActionType,ActionTime`

## Configuration

See `config/default_config.yaml` for all options including:
- Dataset sizes
- Hobby vocabulary and weights
- Region distribution
- Follow graph properties (popularity skew, reciprocity)
- Activity behavior (action types, user tiers, time decay)
