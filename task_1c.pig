logs = LOAD '/user/ds503/input/ActivityLog.csv' USING PigStorage(',') AS (uid:int, pid:int, time:int);
grouped = GROUP logs BY pid;
result = FOREACH grouped GENERATE group AS pid, SUM(logs.time) AS total_time;
STORE result INTO '/user/ds503/output_task_c' USING PigStorage(',');