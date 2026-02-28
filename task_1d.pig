logs = LOAD '/user/ds503/input/ActivityLog.csv' USING PigStorage(',') AS (uid:int, pid:int, time:int);
distinct_users = DISTINCT (FOREACH logs GENERATE uid);
STORE distinct_users INTO '/user/ds503/output_task_d' USING PigStorage(',');