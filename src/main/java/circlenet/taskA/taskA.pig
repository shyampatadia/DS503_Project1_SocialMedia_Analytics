-- Task A (Pig): hobby frequency
%default PAGES '/circlenet/pages/CircleNetPage.csv'
%default OUT '/circlenet/pig_output/taskA'

pages = LOAD '$PAGES' USING PigStorage(',') AS (
    id:int,
    nickname:chararray,
    jobtitle:chararray,
    regioncode:int,
    favoritehobby:chararray
);

hobbies = FOREACH pages GENERATE TRIM(favoritehobby) AS hobby;
valid_hobbies = FILTER hobbies BY hobby IS NOT NULL AND hobby != '';

grp = GROUP valid_hobbies BY hobby;
result = FOREACH grp GENERATE group AS hobby, COUNT(valid_hobbies) AS hobby_count;

STORE result INTO '$OUT' USING PigStorage(',');
