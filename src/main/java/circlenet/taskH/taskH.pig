-- Task H (Pig): same-region one-way follows (not followed back)
%default PAGES '/circlenet/pages/CircleNetPage.csv'
%default FOLLOWS '/circlenet/follows/Follows.csv'
%default OUT '/circlenet/pig_output/taskH'

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

page_region = FOREACH pages GENERATE id, nickname, regioncode;
edges = FOREACH follows GENERATE id1 AS src, id2 AS dst;

with_src = JOIN edges BY src, page_region BY id;
src_region = FOREACH with_src GENERATE edges::src AS src, edges::dst AS dst, page_region::regioncode AS src_region;

with_dst = JOIN src_region BY dst, page_region BY id;
same_region = FILTER with_dst BY
    (src_region::src != src_region::dst) AND
    (src_region::src_region == page_region::regioncode);

same_region_edges = FOREACH same_region GENERATE src_region::src AS src, src_region::dst AS dst;
reversed = FOREACH same_region_edges GENERATE dst AS src, src AS dst;

paired = JOIN same_region_edges BY (src, dst) LEFT OUTER, reversed BY (src, dst);
one_way = FILTER paired BY reversed::src IS NULL;

one_way_users = FOREACH one_way GENERATE same_region_edges::src AS user_id;
uniq_users = DISTINCT one_way_users;

joined = JOIN uniq_users BY user_id, page_region BY id;
result = FOREACH joined GENERATE uniq_users::user_id AS id, page_region::nickname AS nickname;

STORE result INTO '$OUT' USING PigStorage(',');
