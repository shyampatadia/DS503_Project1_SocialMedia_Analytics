#!/bin/bash

PIG_OPTS="-x mapreduce"
OUTPUT_BASE="/user/ds503"
REPORT_FILE="pig_implementation_report.txt"

echo "Project 2: Apache Pig Implementation Report" > $REPORT_FILE
echo "Generated on: $(date)" >> $REPORT_FILE
echo "----------------------------------------------------------" >> $REPORT_FILE

echo "Starting Pig Latin Task Suite..."

TASKS=("1a" "1b" "1c" "1d" "1e" "1g" "1f" "1h")

for task in "${TASKS[@]}"; do
    PIG_FILE="task_${task}.pig"

    if [ -f "$PIG_FILE" ]; then
        echo ">>> Preparing Task ${task}..."

        DIR_SUFFIX=${task#1}
        hadoop fs -rm -r "${OUTPUT_BASE}/output_task_${DIR_SUFFIX}" 2>/dev/null

        echo ">>> Executing $PIG_FILE..."
        pig $PIG_OPTS "$PIG_FILE"

        if [ $? -eq 0 ]; then
            # Append Implementation details to the report
            case $task in
                "1a") echo "Task 1a: Grouped users by hobby and counted totals." >> $REPORT_FILE ;;
                "1b") echo "Task 1b: Joined logs and pages to find top 10 most accessed pages." >> $REPORT_FILE ;;
                "1c") echo "Task 1c: Summed activity time per page using GROUP and SUM." >> $REPORT_FILE ;;
                "1d") echo "Task 1d: Extracted unique user IDs using DISTINCT." >> $REPORT_FILE ;;
                "1e") echo "Task 1e: Identified inactive users using LEFT OUTER JOIN and FILTER." >> $REPORT_FILE ;;
                "1g") echo "Task 1g: Counted social relations per target ID." >> $REPORT_FILE ;;
                "1f") echo "Task 1f: Joined Task 1g results with follows to find popular targets (>10)." >> $REPORT_FILE ;;
                "1h") echo "Task 1h: Analyzed interest distribution by grouping by hobby." >> $REPORT_FILE ;;
            esac
            echo "----------------------------------------------------------" >> $REPORT_FILE
        fi
    else
        echo "Error: $PIG_FILE not found."
    fi
done

echo "Done! Full report generated in $REPORT_FILE."