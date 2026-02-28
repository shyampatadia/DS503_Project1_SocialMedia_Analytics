logs = LOAD '/user/ds503/input/ActivityLog.csv' USING PigStorage(',') AS (uid:int, pid:int, time:int);
pages = LOAD '/user/ds503/input/CircleNetPage.csv' USING PigStorage(',') AS (pid:int, name:chararray, hobby:chararray);
grouped_logs = GROUP logs BY pid;
counts = FOREACH grouped_logs GENERATE group AS pid, COUNT(logs) AS access_count;
joined = JOIN counts BY pid, pages BY pid;
ordered = ORDER joined BY counts::access_count DESC;
top10 = LIMIT ordered 10;
result = FOREACH top10 GENERATE pages::pid, pages::name, counts::access_count;
STORE result INTO '/user/ds503/output_task_b' USING PigStorage(',');