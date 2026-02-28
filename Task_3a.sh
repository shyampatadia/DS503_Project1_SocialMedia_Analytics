#!/bin/bash

JAR_PATH="project2.jar"
KAG_DATA="/user/ds503/kaggle/kaggle_dataset.csv"
KAG_SEEDS="/user/ds503/kaggle/kaggle_seeds.csv"
LOG_FILE="kaggle_experiment_results.log"

echo "==========================================================" | tee -a $LOG_FILE
echo "Task 3a" | tee -a $LOG_FILE
echo "Execution Date: $(date)" | tee -a $LOG_FILE
echo "==========================================================" | tee -a $LOG_FILE

run_kag_task() {
    local task_id=$1
    local class_name=$2
    local out_dir="/user/ds503/output_kaggle_$task_id"

    echo ">>> Executing $class_name on Kaggle Data..." | tee -a $LOG_FILE
    hadoop fs -rm -r $out_dir > /dev/null 2>&1
    START=$(date +%s)

    hadoop jar $JAR_PATH $class_name $KAG_DATA $KAG_SEEDS $out_dir | tee -a $LOG_FILE

    END=$(date +%s)
    DIFF=$((END - START))

    echo "Finished $task_id. Duration: $DIFF seconds." | tee -a $LOG_FILE
    echo "----------------------------------------------------------" | tee -a $LOG_FILE
}

run_kag_task "2_2a" "Task2_2a"
run_kag_task "2_2b" "Task2_2b"
run_kag_task "2_2c" "Task2_2c"
run_kag_task "2_2d" "Task2_2d"
run_kag_task "2_2e" "Task2_2e"

echo "Task 3a Complete. Check $LOG_FILE."