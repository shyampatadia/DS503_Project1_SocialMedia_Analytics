pages = LOAD '/user/ds503/input/CircleNetPage.csv' USING PigStorage(',') AS (id:int, name:chararray, hobby:chararray);
-- Replace with actual metric calculation based on your P1 schema
grouped = GROUP pages BY hobby;
result = FOREACH grouped GENERATE group AS hobby, COUNT(pages);
STORE result INTO '/user/ds503/output_task_h' USING PigStorage(',');