-- Task D (Pig): followers per owner including zero
%default PAGES '/circlenet/pages/CircleNetPage.csv'
%default FOLLOWS '/circlenet/follows/Follows.csv'
%default OUT '/circlenet/pig_output/taskD'

pages = LOAD '$PAGES' USING PigStorage(',') AS (
    id:int,
    nickname:chararray,
    jobtitle:chararray,
    regioncode:int,
    favoritehobby:chararray
);

follows = LOAD '$FOLLOWS' USING PigStorage(',') AS (
    colrel:long,
    id1:int,
    id2:int,
    dateofrelation:int,
    description:chararray
);

owners = FOREACH pages GENERATE id AS owner_id, nickname;
targets = FOREACH follows GENERATE id2 AS owner_id;

grp = GROUP targets BY owner_id;
counts = FOREACH grp GENERATE group AS owner_id, COUNT(targets) AS followers;

joined = JOIN owners BY owner_id LEFT OUTER, counts BY owner_id;
result = FOREACH joined GENERATE
    owners::owner_id AS owner_id,
    owners::nickname AS nickname,
    ((counts::followers IS NULL) ? 0L : counts::followers) AS followers;

STORE result INTO '$OUT' USING PigStorage(',');
