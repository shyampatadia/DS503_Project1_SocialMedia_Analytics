follows = LOAD '/user/ds503/input/Follows.csv' USING PigStorage(',') AS (uid:int, target_id:int);
-- Load Task G output (Relationship Counts)
rel_counts = LOAD '/user/ds503/output_task_g/part-r-00000' USING PigStorage(',') AS (pid:int, count:int);
joined = JOIN follows BY target_id, rel_counts BY pid;
-- Filter criteria (e.g., following someone with > 10 relations)
result = FILTER joined BY rel_counts::count > 10;
STORE result INTO '/user/ds503/output_task_f' USING PigStorage(',');