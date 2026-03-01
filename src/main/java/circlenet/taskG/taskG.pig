-- Task G (Pig): outdated pages (no access in last 90 days = 2160 hours)
%default PAGES '/circlenet/pages/CircleNetPage.csv'
%default ACTIVITY '/circlenet/activitylog/ActivityLog.csv'
%default OUT '/circlenet/pig_output/taskG'

pages = LOAD '$PAGES' USING PigStorage(',') AS (
    id:int,
    nickname:chararray,
    jobtitle:chararray,
    regioncode:int,
    favoritehobby:chararray
);

activity = LOAD '$ACTIVITY' USING PigStorage(',') AS (
    actionid:long,
    bywho:int,
    whatpage:int,
    actiontype:chararray,
    actiontime:int
);

page_meta = FOREACH pages GENERATE id AS user_id, nickname;
user_times = FOREACH activity GENERATE bywho AS user_id, actiontime AS action_time;

by_user = GROUP user_times BY user_id;
user_max = FOREACH by_user GENERATE group AS user_id, MAX(user_times.action_time) AS user_last_time;

all_times = GROUP user_times ALL;
global_max = FOREACH all_times GENERATE MAX(user_times.action_time) AS global_last_time;

pages_with_last = JOIN page_meta BY user_id LEFT OUTER, user_max BY user_id;
with_global = CROSS pages_with_last, global_max;

outdated = FILTER with_global BY
    (user_max::user_last_time IS NULL) OR
    (user_max::user_last_time < (global_max::global_last_time - 2160));

result = FOREACH outdated GENERATE
    page_meta::user_id AS id,
    page_meta::nickname AS nickname,
    user_max::user_last_time AS user_last_time,
    global_max::global_last_time AS global_last_time,
    (global_max::global_last_time - 2160) AS threshold_time;

STORE result INTO '$OUT' USING PigStorage(',');
