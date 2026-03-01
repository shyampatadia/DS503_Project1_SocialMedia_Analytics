#!/usr/bin/env bash
set -euo pipefail

# Run inside container.
# Builds a task-level MR-vs-Pig timing comparison CSV.

MR_TIMES=${MR_TIMES:-/home/ds503/task_times.csv}
PIG_TIMES=${PIG_TIMES:-/home/ds503/pig_task_times.csv}
OUT_CSV=${OUT_CSV:-/home/ds503/mr_vs_pig_comparison.csv}

if [[ ! -f "$MR_TIMES" ]]; then
  echo "Missing MR timing file: $MR_TIMES" >&2
  exit 1
fi

if [[ ! -f "$PIG_TIMES" ]]; then
  echo "Missing Pig timing file: $PIG_TIMES" >&2
  exit 1
fi

latest_time() {
  local file="$1"
  local label="$2"
  awk -F, -v lbl="$label" 'NR>1 && $2==lbl && $3=="total" {val=$4} END{if(val=="") print "NA"; else print val}' "$file"
}

diff_time() {
  local pig="$1"
  local mr="$2"
  if [[ "$pig" =~ ^[0-9]+$ && "$mr" =~ ^[0-9]+$ ]]; then
    echo $((pig - mr))
  else
    echo "NA"
  fi
}

# MR labels chosen to reflect your best current MR variant for each task.
mr_A=$(latest_time "$MR_TIMES" "TaskAOptimized")
mr_B=$(latest_time "$MR_TIMES" "TaskBOptimized")
mr_C=$(latest_time "$MR_TIMES" "TaskCSimple")
mr_D=$(latest_time "$MR_TIMES" "TaskDOptimized")
mr_E=$(latest_time "$MR_TIMES" "TaskESimple")
mr_F=$(latest_time "$MR_TIMES" "TaskFOptimized")
mr_G=$(latest_time "$MR_TIMES" "TaskGOptimized")
mr_H=$(latest_time "$MR_TIMES" "TaskHOptimized")

pig_A=$(latest_time "$PIG_TIMES" "TaskAPig")
pig_B=$(latest_time "$PIG_TIMES" "TaskBPig")
pig_C=$(latest_time "$PIG_TIMES" "TaskCPig")
pig_D=$(latest_time "$PIG_TIMES" "TaskDPig")
pig_E=$(latest_time "$PIG_TIMES" "TaskEPig")
pig_F=$(latest_time "$PIG_TIMES" "TaskFPig")
pig_G=$(latest_time "$PIG_TIMES" "TaskGPig")
pig_H=$(latest_time "$PIG_TIMES" "TaskHPig")

{
  echo "task,mr_label,mr_time_ms,pig_label,pig_time_ms,pig_minus_mr_ms"
  echo "A,TaskAOptimized,$mr_A,TaskAPig,$pig_A,$(diff_time "$pig_A" "$mr_A")"
  echo "B,TaskBOptimized,$mr_B,TaskBPig,$pig_B,$(diff_time "$pig_B" "$mr_B")"
  echo "C,TaskCSimple,$mr_C,TaskCPig,$pig_C,$(diff_time "$pig_C" "$mr_C")"
  echo "D,TaskDOptimized,$mr_D,TaskDPig,$pig_D,$(diff_time "$pig_D" "$mr_D")"
  echo "E,TaskESimple,$mr_E,TaskEPig,$pig_E,$(diff_time "$pig_E" "$mr_E")"
  echo "F,TaskFOptimized,$mr_F,TaskFPig,$pig_F,$(diff_time "$pig_F" "$mr_F")"
  echo "G,TaskGOptimized,$mr_G,TaskGPig,$pig_G,$(diff_time "$pig_G" "$mr_G")"
  echo "H,TaskHOptimized,$mr_H,TaskHPig,$pig_H,$(diff_time "$pig_H" "$mr_H")"
} > "$OUT_CSV"

echo "Wrote comparison CSV: $OUT_CSV"
