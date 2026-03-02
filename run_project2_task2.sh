#!/usr/bin/env bash
set -euo pipefail

# Run inside container
# Example:
#   export JAR=/home/ds503/ds503_bdm-1.0-SNAPSHOT.jar
#   export CFG=/home/ds503/project2_task2_config.properties
#   /home/ds503/run_project2_task2.sh

JAR=${JAR:-/home/ds503/ds503_bdm-1.0-SNAPSHOT.jar}
CFG=${CFG:-/home/ds503/project2_task2_config.properties}
TIMES=${TIMES:-/home/ds503/task2_times.csv}
LOG_DIR=${LOG_DIR:-/home/ds503/task2_logs}

write_header_if_missing() {
  if [[ ! -f "$TIMES" ]]; then
    echo "timestamp,label,time_ms,time_s,success" > "$TIMES"
  fi
}

run_case() {
  local label="$1"
  local mode="$2"
  local out="$3"
  local log_file="$LOG_DIR/${label}.log"

  hdfs dfs -rm -r -f "$out" >/dev/null 2>&1 || true
  mkdir -p "$LOG_DIR"

  local start end elapsed_ms elapsed_s ts success
  start=$(date +%s%3N)

  if hadoop jar "$JAR" project2.task2simple.KMeansMain "$CFG" "mode=$mode" "output.base=$out" 2>&1 | tee "$log_file"; then
    success=1
  else
    success=0
  fi

  end=$(date +%s%3N)
  elapsed_ms=$((end - start))
  elapsed_s=$(awk -v ms="$elapsed_ms" 'BEGIN{printf "%.3f", ms/1000.0}')
  ts=$(date -u +"%Y-%m-%dT%H:%M:%S.000")

  echo "$ts,$label,$elapsed_ms,$elapsed_s,$success" >> "$TIMES"

  if [[ "$success" -ne 1 ]]; then
    echo "Failed case: $label" >&2
    exit 1
  fi
}

write_header_if_missing

# 2.2(a)
run_case "Task2_2a_single" "SINGLE" "/project2/task2/output/single"
# 2.2(b)
run_case "Task2_2b_basic" "BASIC" "/project2/task2/output/basic"
# 2.2(c)
run_case "Task2_2c_early" "EARLY" "/project2/task2/output/early"
# 2.2(d) + 2.2(e)(i)
run_case "Task2_2d_opt_centers" "OPT_CENTERS" "/project2/task2/output/opt_centers"
# 2.2(e)(ii)
run_case "Task2_2e_opt_points" "OPT_POINTS" "/project2/task2/output/opt_points"

echo "Task 2 runs completed."
echo "Timing file: $TIMES"
echo "Logs dir   : $LOG_DIR"
