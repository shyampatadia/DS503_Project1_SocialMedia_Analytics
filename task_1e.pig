pages = LOAD '/user/ds503/input/CircleNetPage.csv' USING PigStorage(',') AS (id:int, name:chararray, hobby:chararray);
logs = LOAD '/user/ds503/input/ActivityLog.csv' USING PigStorage(',') AS (uid:int, pid:int, time:int);
joined = JOIN pages BY id LEFT OUTER, logs BY uid;
lonely = FILTER joined BY logs::uid IS NULL;
result = FOREACH lonely GENERATE pages::id, pages::name;
STORE result INTO '/user/ds503/output_task_e' USING PigStorage(',');