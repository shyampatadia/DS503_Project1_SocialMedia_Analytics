follows = LOAD '/user/ds503/input/Follows.csv' USING PigStorage(',') AS (uid:int, target_id:int);
grouped = GROUP follows BY target_id;
result = FOREACH grouped GENERATE group AS pid, COUNT(follows) AS relation_count;
STORE result INTO '/user/ds503/output_task_g' USING PigStorage(',');