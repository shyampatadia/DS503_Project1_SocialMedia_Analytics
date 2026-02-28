#!/bin/bash


JAR_PATH="project2.jar"
DATASET="/user/ds503/input/dataset.csv"
SEEDS="/user/ds503/input/seeds.csv"
LOG_FILE="clustering_experiments_report.log"

echo "==========================================================" | tee -a $LOG_FILE
echo "Project 2_Task2" | tee -a $LOG_FILE
echo "Execution Date: $(date)" | tee -a $LOG_FILE
echo "==========================================================" | tee -a $LOG_FILE


run_task() {
    local task_name=$1
    local class_name=$2
    local output_dir="/user/ds503/output_$task_name"

    echo ">>> Running $task_name ($class_name)..." | tee -a $LOG_FILE


    hadoop fs -rm -r $output_dir > /dev/null 2>&1


    START_TIME=$(date +%s)
    hadoop jar $JAR_PATH $class_name | tee -a $LOG_FILE
    END_TIME=$(date +%s)

    DURATION=$((END_TIME - START_TIME))
    echo "Done. Duration: $DURATION seconds." | tee -a $LOG_FILE
    echo "Output saved to: $output_dir" | tee -a $LOG_FILE
    echo "----------------------------------------------------------" | tee -a $LOG_FILE
}


run_task "2_2a" "Task2_2a"
run_task "2_2b" "Task2_2b"
run_task "2_2c" "Task2_2c"
run_task "2_2d" "Task2_2d"
run_task "2_2e" "Task2_2e"

echo "All tasks complete. Check $LOG_FILE."