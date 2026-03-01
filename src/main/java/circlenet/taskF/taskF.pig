-- Task F (Pig): owners with followers above global average
%default PAGES '/circlenet/pages/CircleNetPage.csv'
%default FOLLOWS '/circlenet/follows/Follows.csv'
%default OUT '/circlenet/pig_output/taskF'

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

by_owner = GROUP targets BY owner_id;
counts = FOREACH by_owner GENERATE group AS owner_id, COUNT(targets) AS followers;

owners_with_counts = JOIN owners BY owner_id LEFT OUTER, counts BY owner_id;
owner_counts = FOREACH owners_with_counts GENERATE
    owners::owner_id AS owner_id,
    owners::nickname AS nickname,
    ((counts::followers IS NULL) ? 0L : counts::followers) AS followers;

all_rows = GROUP owner_counts ALL;
avg_rel = FOREACH all_rows GENERATE AVG(owner_counts.followers) AS avg_followers;

crossed = CROSS owner_counts, avg_rel;
filtered = FILTER crossed BY owner_counts::followers > avg_rel::avg_followers;

result = FOREACH filtered GENERATE
    owner_counts::owner_id AS owner_id,
    owner_counts::nickname AS nickname,
    owner_counts::followers AS followers,
    avg_rel::avg_followers AS average_followers;

STORE result INTO '$OUT' USING PigStorage(',');
