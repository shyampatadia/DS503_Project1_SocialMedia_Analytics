#!/usr/bin/env bash
set -u

# Run inside container
# Executes Task 2.2(f) experiment matrix + one convergence stress run.

JAR=${JAR:-/home/ds503/ds503_bdm-1.0-SNAPSHOT.jar}
CFG=${CFG:-/home/ds503/project2_task2_config.properties}
LOG_DIR=${LOG_DIR:-/home/ds503/task2_logs}
TIMES=${TIMES:-/home/ds503/task2_exp_times.csv}
BASE=${BASE:-/project2/task2/exp}

mkdir -p "$LOG_DIR"

echo "timestamp,label,time_ms,time_s,success" > "$TIMES"

run_exp() {
  local label="$1"
  shift
  local out="$1"
  shift

  hdfs dfs -rm -r -f "$out" >/dev/null 2>&1 || true

  local start end ms sec ts rc
  start=$(date +%s%3N)
  if hadoop jar "$JAR" project2.task2simple.KMeansMain "$CFG" "$@" > "$LOG_DIR/${label}.log" 2>&1; then
    rc=0
  else
    rc=1
  fi
  end=$(date +%s%3N)

  ms=$((end-start))
  sec=$(awk -v ms="$ms" 'BEGIN{printf "%.3f", ms/1000.0}')
  ts=$(date -u +"%Y-%m-%dT%H:%M:%S.000")
  echo "$ts,$label,$ms,$sec,$([ $rc -eq 0 ] && echo 1 || echo 0)" >> "$TIMES"

  if [ $rc -eq 0 ]; then
    hdfs dfs -cat "$out/summary.txt" > "$LOG_DIR/${label}_summary.txt" 2>/dev/null || true
  fi

  echo "$label finished (success=$([ $rc -eq 0 ] && echo 1 || echo 0), time_ms=$ms)"
}

# 5.2 matrix
for K in 3 5 8; do
  for R in 10 30; do
    for MODE in EARLY OPT_CENTERS; do
      mode_lc=$(echo "$MODE" | tr '[:upper:]' '[:lower:]')
      label="exp_k${K}_r${R}_${mode_lc}"
      out="$BASE/k${K}_r${R}_${mode_lc}"
      run_exp "$label" "$out" mode="$MODE" k="$K" rounds="$R" output.base="$out"
    done
  done
done

# 5.3 extra convergence tuning run
label="exp_early_k5_r50_eps50"
out="$BASE/early_k5_r50_eps50"
run_exp "$label" "$out" mode=EARLY k=5 rounds=50 epsilon=50 output.base="$out"

echo "Done."
echo "Times: $TIMES"
echo "Logs : $LOG_DIR"
