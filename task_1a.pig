-- Load Pages: id, name, hobby
pages = LOAD '/user/ds503/input/CircleNetPage.csv' USING PigStorage(',') AS (id:int, name:chararray, hobby:chararray);
grouped_hobbies = GROUP pages BY hobby;
result = FOREACH grouped_hobbies GENERATE group AS hobby, COUNT(pages) AS total_users;
STORE result INTO '/user/ds503/output_task_a' USING PigStorage(',');