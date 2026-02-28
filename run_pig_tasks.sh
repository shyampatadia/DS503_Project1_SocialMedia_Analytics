#!/bin/bash
tasks=("a" "b" "c" "d" "e" "f" "g" "h")
echo "Starting CircleNet Pig Analytics Suite..."
for i in "${tasks[@]}"
do
    echo "------------------------------------------------"
    echo "Processing Task 1$i..."
    hadoop fs -rm -r /user/ds503/output_task_$i 2>/dev/null
    pig -x mapreduce "task_1$i.pig"
    echo "Verifying output for Task 1$i:"
    hadoop fs -ls "/user/ds503/output_task_$i"
done
echo "All Pig tasks completed!"
