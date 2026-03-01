-- Task E (Pig): total actions and distinct pages accessed per owner
%default PAGES '/circlenet/pages/CircleNetPage.csv'
%default ACTIVITY '/circlenet/activitylog/ActivityLog.csv'
%default OUT '/circlenet/pig_output/taskE'

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

owners = FOREACH pages GENERATE id AS owner_id;
actions = FOREACH activity GENERATE bywho AS owner_id, whatpage AS page_id;

by_owner = GROUP actions BY owner_id;
stats = FOREACH by_owner {
    unique_pages = DISTINCT actions.page_id;
    GENERATE
        group AS owner_id,
        COUNT(actions) AS total_actions,
        COUNT(unique_pages) AS distinct_pages;
};

joined = JOIN owners BY owner_id LEFT OUTER, stats BY owner_id;
result = FOREACH joined GENERATE
    owners::owner_id AS owner_id,
    ((stats::total_actions IS NULL) ? 0L : stats::total_actions) AS total_actions,
    ((stats::distinct_pages IS NULL) ? 0L : stats::distinct_pages) AS distinct_pages;

STORE result INTO '$OUT' USING PigStorage(',');
