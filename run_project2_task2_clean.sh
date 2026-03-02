#!/usr/bin/env bash
set -euo pipefail

# Clean Task 2 artifacts inside container.
# Default: keeps input data, removes outputs/logs/timings.
# Use RESET_INPUT=1 to also remove HDFS input and local generated CSV files.

BASE_OUT=${BASE_OUT:-/project2/task2/output}
BASE_IN=${BASE_IN:-/project2/task2/input}
TIMES=${TIMES:-/home/ds503/task2_times.csv}
LOG_DIR=${LOG_DIR:-/home/ds503/task2_logs}
LOCAL_RESULTS_DIR=${LOCAL_RESULTS_DIR:-/home/ds503/task2_results}
LOCAL_POINTS=${LOCAL_POINTS:-/home/ds503/points_4d.csv}
LOCAL_SEEDS=${LOCAL_SEEDS:-/home/ds503/seeds_k5.csv}
RESET_INPUT=${RESET_INPUT:-0}

echo "[Task2 Clean] Removing old Task 2 outputs..."
hdfs dfs -rm -r -f "$BASE_OUT" >/dev/null 2>&1 || true

echo "[Task2 Clean] Removing timing/log/result files in container..."
rm -f "$TIMES" || true
rm -rf "$LOG_DIR" || true
rm -rf "$LOCAL_RESULTS_DIR" || true

if [[ "$RESET_INPUT" == "1" ]]; then
  echo "[Task2 Clean] RESET_INPUT=1 -> removing HDFS input + local generated csv"
  hdfs dfs -rm -r -f "$BASE_IN" >/dev/null 2>&1 || true
  rm -f "$LOCAL_POINTS" "$LOCAL_SEEDS" || true
fi

echo "[Task2 Clean] Done."
