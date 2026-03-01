-- Task B (Pig): top 10 pages by access count with page metadata
%default PAGES '/circlenet/pages/CircleNetPage.csv'
%default ACTIVITY '/circlenet/activitylog/ActivityLog.csv'
%default OUT '/circlenet/pig_output/taskB'

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

page_hits = FOREACH activity GENERATE whatpage AS page_id;
grp = GROUP page_hits BY page_id;
counts = FOREACH grp GENERATE group AS page_id, COUNT(page_hits) AS access_count;

page_meta = FOREACH pages GENERATE id AS page_id, nickname, jobtitle;
joined = JOIN counts BY page_id, page_meta BY page_id;

ordered = ORDER joined BY counts::access_count DESC, page_meta::page_id ASC;
top10 = LIMIT ordered 10;

result = FOREACH top10 GENERATE
    page_meta::page_id AS id,
    page_meta::nickname AS nickname,
    page_meta::jobtitle AS jobtitle,
    counts::access_count AS access_count;

STORE result INTO '$OUT' USING PigStorage(',');
