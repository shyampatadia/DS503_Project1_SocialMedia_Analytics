-- Task C (Pig): map-only style filter by hobby (no GROUP/JOIN)
%default PAGES '/circlenet/pages/CircleNetPage.csv'
%default HOBBY 'PodcastBinging'
%default OUT '/circlenet/pig_output/taskC'

pages = LOAD '$PAGES' USING PigStorage(',') AS (
    id:int,
    nickname:chararray,
    jobtitle:chararray,
    regioncode:int,
    favoritehobby:chararray
);

filtered_rows = FILTER pages BY
    favoritehobby IS NOT NULL AND
    LOWER(TRIM(favoritehobby)) == LOWER('$HOBBY');
result = FOREACH filtered_rows GENERATE nickname, jobtitle;

STORE result INTO '$OUT' USING PigStorage(',');
