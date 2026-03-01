#!/usr/bin/env bash
set -euo pipefail

# Run inside container.
# Captures Pig runtimes in CSV format compatible with existing task_times.csv columns.

PAGES=${PAGES:-/circlenet/pages/CircleNetPage.csv}
FOLLOWS=${FOLLOWS:-/circlenet/follows/Follows.csv}
ACTIVITY=${ACTIVITY:-/circlenet/activitylog/ActivityLog.csv}
PIG_OUT=${PIG_OUT:-/circlenet/pig_output}
PIG_TIMES=${PIG_TIMES:-/home/ds503/pig_task_times.csv}
HOBBY=${HOBBY:-PodcastBinging}

write_header_if_missing() {
  if [[ ! -f "$PIG_TIMES" ]]; then
    echo "timestamp,label,phase,time_ms,time_s,success" > "$PIG_TIMES"
  fi
}

run_pig_task() {
  local label="$1"
  local script_path="$2"
  local output_path="$3"
  shift 3
  local extra_params=("$@")

  hdfs dfs -rm -r -f "$output_path" >/dev/null 2>&1 || true

  local start end elapsed_ms elapsed_s ts success
  start=$(date +%s%3N)

  if pig -x mapreduce -f "$script_path" \
      -param PAGES="$PAGES" \
      -param FOLLOWS="$FOLLOWS" \
      -param ACTIVITY="$ACTIVITY" \
      -param OUT="$output_path" \
      "${extra_params[@]}"; then
    success=1
  else
    success=0
  fi

  end=$(date +%s%3N)
  elapsed_ms=$((end - start))
  elapsed_s=$(awk -v ms="$elapsed_ms" 'BEGIN{printf "%.3f", ms/1000.0}')
  ts=$(date -u +"%Y-%m-%dT%H:%M:%S.000")

  echo "$ts,$label,total,$elapsed_ms,$elapsed_s,$success" >> "$PIG_TIMES"

  if [[ "$success" -ne 1 ]]; then
    echo "Task failed: $label" >&2
    exit 1
  fi
}

write_header_if_missing

run_pig_task "TaskAPig" "/home/ds503/pig_scripts/taskA/taskA.pig" "$PIG_OUT/taskA"
run_pig_task "TaskBPig" "/home/ds503/pig_scripts/taskB/taskB.pig" "$PIG_OUT/taskB"
run_pig_task "TaskCPig" "/home/ds503/pig_scripts/taskC/taskC.pig" "$PIG_OUT/taskC" -param HOBBY="$HOBBY"
run_pig_task "TaskDPig" "/home/ds503/pig_scripts/taskD/taskD.pig" "$PIG_OUT/taskD"
run_pig_task "TaskEPig" "/home/ds503/pig_scripts/taskE/taskE.pig" "$PIG_OUT/taskE"
run_pig_task "TaskFPig" "/home/ds503/pig_scripts/taskF/taskF.pig" "$PIG_OUT/taskF"
run_pig_task "TaskGPig" "/home/ds503/pig_scripts/taskG/taskG.pig" "$PIG_OUT/taskG"
run_pig_task "TaskHPig" "/home/ds503/pig_scripts/taskH/taskH.pig" "$PIG_OUT/taskH"

echo "Pig task timings written to: $PIG_TIMES"
