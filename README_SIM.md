# FaceIn Network Exercise with Apage Pig
## Task Premise: Creating Datasets for a FaceIn Big Data Application
Write a program (You are welcome to use a different programming language for that
part, e.g., Python) that creates datasets related to a new social media application FaceIn,
including the following datasets as three separate data files:
1. FaceInPage: each line represents a person and should include at least the following
attributes describing the person as listed below.

2. Associates: each line describes a relationship (friendship) between two persons and
a timestamp indicating when this relationship was declared.
FriendRel: unique sequential number (integer) taken from value in the
range from 1 to 20,000,000 (the file has 20,000,000 lines and
thus friend relationships)
* PersonA_ID: Person-ID of a person who has a FaceInPage, i.e., from 1 to 200,000 people
* PersonB_ID: Person-ID of a person who has a FaceInPage, i.e., from 1 to 200,000 people. This ID should be different than PersonA_ID. Also, since this is a symmetric relationship, if a relationship
between two persons num1 and num2 already exists (PersonA_ID=num1 and PersonB_ID=num2), there shouldn't be a relationship between num2 and num1 already exists (PersonA_ID=num2 and PersonB_ID=num1) recorded.
* ID: unique sequential number (integer) from 1 to 200,000 indicating the owner of the page (there will be 200,000 lines)
* Name: characters of length between 10 and 20 (do not use commas inside this string)
* Nationality: characters of length between 10 and 20 (do not use commas inside this string)
* CountryCod: number (integer) between 1 and 50
* Hobby: sequence of characters of length between 10 and 20
* DateofFriendship: random number (integer; or some other sequential data type to
use as date) between 1 and 1,000,000 to indicate when the
friendship started
* Desc: text of characters of length between 20 and 50 explaining the type of friendship: college-friend, family, etc.

3. AccessLogs: each line indicates a person num1 has accessed the FaceInPage that
belongs to a second person num2, including the timing of the access.
* AccessId: unique sequential number (integer) from 1 to 10,000,000
* ByWho: References the Id of the person who has accessed the FaceInPage
* WhatPage: References the Id of the page that was accessed
* TypeOfAccess: text of characters of length between 20 and 50 explaining if just viewed, left a note, added a friendship, etc.
* AccessTime: random number between 1 and 1,000,000 (or epoch time)
  
### Setup
Apache Pig was set up on local machines following the GeeksForGeeks tutorial “Apache Pig Installation on Windows and Case Study.” The grunt shell was started in local mode and the data from project 1 was loaded and dumped to ensure proper setup. Scripts for each task from project 1 were written and executed locally using the grunt shell. Paths in the Pig scripts were then modified to load and store files to and from HDFS. Pig was started in MapReduce/HDFS mode to execute the tasks.

### Task a
Task: Report all FaceInPage users (name, and hobby) whose nationality is the same as your own nationality (pick one). Note that nationalities in the data file are a random sequence of characters unless meaningful strings like “American” are used. This is up to you.

FaceInPage.csv was loaded using Pig Storage. FILTER was used to remove the first line (header) if it did not match the rest of the data. Another FILTER was applied to return records that matched a given nationality. The filtered relation was stored in a local directory and later modified to store in HDFS. An example output for filtering by nationality = “Bulgaria” is shown.

### Task b
Task: Find the 10 most popular FaceIn pages, namely, those that got the most accesses based on the AccessLog among all pages. Return Id, Name, and Nationality.

FaceInPage.csv and AccessLogs.csv were loaded and filtered to remove headers. Data from access logs was grouped by ‘WhatPage’ to generate a relation with ‘PageID’ and access log count. This relation was ranked by access count in descending order and limited to 10 results. JOIN was used by ‘PageID’ to get ‘Name’ and ‘Nationality’ from FaceInPage.csv.

### Task c
Task: Report for each country, how many of its citizens have a FaceInPage.

FaceInPage.csv was loaded and filtered to remove the header. Data was grouped by nationality, counted for each group, and stored in a results file.

### Task d
Task: For each FaceInPage, compute the “happiness factor” of its owner. That is, for each FaceInPage, report the owner’s name, and the number of relationships they have. For page owners that aren't listed in Associates, return a score of zero. Please note that a symmetric relationship is maintained, take that into account in calculations.

FaceInPage.csv and Associates.csv were loaded. Associates.csv had a column named “Desc”, which conflicted with PigLatin keywords and was handled by filtering out headers first. Data was copied with switched ‘PersonA_ID’ and ‘PersonB_ID’ to account for bidirectional relationships. UNION combined the original and switched relations, using DISTINCT to avoid duplicates. Data was grouped by ‘PersonA_ID’, relationships counted, and joined with FaceInPage to get the owner’s name. FaceInPage entries without associations were filled with 0s.

### Task e
Task: Determine which people have favorites. For each FaceInPage owner, determine how many total accesses to FaceInPage they have made (as reported in the AccessLog) and how many distinct FaceInPages they have accessed in total.

FaceInPage.csv and AccessLogs.csv were loaded and headers removed. Access logs were grouped by ‘ByWho’ to generate a relation with total visit counts. A separate relation grouped access logs by ‘ByWho’ and ‘WhatPage’ to count unique page visits. JOIN combined these relations with FaceInPage to get each user’s name, filling blanks with 0s. The output showed total visits and unique pages visited.

### Task f
Task: Identify people that have a relationship with someone (Associates); yet never accessed their respective friend’s FaceInPage. Report IDs and names.

All three data files were loaded and headers removed. IDs in the access logs were duplicated and switched to account for bidirectional relationships from ‘Task d’. A LEFT OUTER JOIN joined associates and access logs, filtering to keep entries where ‘ByWho’ and ‘WhatPage’ were empty. JOIN was used with FaceInPage to get names of both users. The output showed IDs and Names of users who had not visited their associates’ FaceIn page.

### Task g
Task: Identify "outdated" FaceInPages. Return IDs and Names of persons that have not accessed FaceIn for 90 days (i.e., no entries in the AccessLog in the last 90 days).

FaceInPage.csv and AccessLogs.csv were loaded and headers removed. Access logs were grouped by ‘ByWho’ and ordered by ‘AccessTime’, selecting the newest access. Data was filtered to keep entries where AccessTime / (60*24) was greater than 90 or where ‘AccessTime’ was null. JOIN was used with FaceInPage to get names and results were stored.

### Task h
Task: Report all owners of a FaceInPage who are more popular than an average user, namely, those who have more relationships than the average number of relationships across all owners FaceInPages.

The code from ‘Task d’ was reused. GENERATE AVG calculated the average of all happiness factors and the result from ‘Task d’ was filtered to return users with happiness factors above the average. UNION combined the average result and filtered users, outputting in two different files.

### Runtime Performance Apache Pig and Java MapReduce
Runtimes for each task in MapReduce mode were timed and compared to performance statistics from project one.

```text
| Task   | Optimized Java (s) | Apache Pig (s) | Performance Decrease |
|--------|---------------------|----------------|----------------------|
| Task a | 3.00                | 13.60          | 4.53x                |
| Task b | 12.40               | 113.50         | 9.15x                |
| Task c | 2.94                | 13.45          | 4.57x                |
| Task d | 6.24                | 79.70          | 12.78x               |
| Task e | 20.92               | 275.90         | 13.19x               |
| Task f | 45.00               | 243.975        | 5.42x                |
| Task g | 12.89               | 105.70         | 8.20x                |
| Task h | 7.68                | 93.60          | 12.19x               |
```

--- 

# K-Means Clustering
Premise: Develop a Java MapReduce implementation of the K-Means Clustering Algorithm

## Task 2.1 (Creation of Dataset)
A Python script was written to create the necessary dataset using the random library and Pandas. A data frame of 5,000 pairs of integers between 0 and 1,000 and seed values were generated. Smaller and larger datasets were created for testing and later use.

## Task 2.2 (Clustering the Data)
A main Java class was created with drivers, mappers, combiners, and reducers. Driver methods were called from JUnit test classes with parameters set in their respective JUnit test classes (e.g. file paths, K and R values, convergence threshold). Points and seeds files were configured as input. Euclidean distance was used to find the closest cluster centers to each point. Output files from previous iterations were used as input for subsequent rounds.

## Single-Iteration K-Means
Initial centers were read from the seeds file. The mapper parsed each line of the input, calculated the nearest cluster center, and output pairs of (cluster point, data point) to the reducer. The reducer generated a list of points associated with each cluster, calculated new cluster centers, and wrote a file with the new centers.

## Basic Multi-Iteration K-Means
The parameter R was introduced in the JUnit tests. The algorithm was changed to read and update clusters from the output files of previous iterations. A for-loop in the driver method repeated job creation as many times as specified.

## Advanced Multi-Iteration K-Means with Convergence
A threshold value was introduced in the JUnit parameters. The history of clusters was tracked to compare with previous iterations. The program calculated the percentage difference in change for each centroid after every iteration. If the difference was smaller than the threshold for all centroids, the algorithm converged and exited the iteration loop.

## Optimization K-Means
A combiner was introduced to sum points given by the mapper and send the sum and count of points in each cluster to the reducer. An optimized reducer added partial sums from different mappers and used the count to calculate new cluster centers. The results of an R value configuration of 30 with a convergence threshold of 1% converged after 12 iterations.

## Output Variations
Cluster centers and an indication of convergence were outputted by adding an extra Map-Reduce job that ran once convergence was reached or the number of iterations matched R. The mapper output (cluster, point) value pairs to a PointsGrouperReducer class. The cleanup method read a context configuration variable specifying convergence and wrote the list of clusters and convergence indication to a final output file. Final clustered data points were outputted using multiple output MapReduce class. The cleanup method of the PointsGrouperReducer wrote the dictionary contents to the output file.

## Experimenting
Different sets of parameters were tried, monitoring the performance of the model. Results for a 5,000 points dataset are shown below. Experiments with larger datasets and comparisons with SciKit-Learn are in Section 3.

| Experiment ID | Task | K  | Start Seeds                                                | R  | Threshold Convergence | Converged?            | Time Taken (s) | Final Clusters                                                                 |
|---------------|------|----|------------------------------------------------------------|----|-----------------------|-----------------------|---------------|--------------------------------------------------------------------------------|
| A1            | A    | 5  | (8508, 8743) (2984, 8574) (3794, 2826) (3853, 3885) (8698, 6252) | -  | -                     | -                     | 5.62          | (3976.97, 7015.44) (4281.52, 1878.03) (8361.41, 5343.83) (8647.18, 949.25) (9530.33, 3017.72) |
| A2            | A    | 10 | (3432, 1937) (4470, 2124) (2303, 304) (5220, 9018) (6193, 3599) (1751, 1249) (8320, 4039) (528, 3182) (7253, 7262) (7677, 3117) | -  | -                     | -                     | 5.31          | (1340.38, 8396.40) (2313.12, 1380.40) (2456.88, 243.13) (1702.59, 5982.91) (6398.64, 1012.03) (5715.18, 3495.29) (6515.07, 5570.82) (756.83, 1968.45) (5451.73, 9469.46) (6914.07, 7841.71) |
| B1            | B    | 5  | (8508, 8743) (2984, 8574) (3794, 2826) (3853, 3885) (8698, 6252) | 5  | -                     | -                     | 12.01         | (2510.99, 7477.67) (2510.19, 2490.96) (7509.88, 1216.60) (7505.88, 7860.08) (7568.02, 4118.73) |
| B2            | B    | 5  | (8508, 8743) (2984, 8574) (3794, 2826) (3853, 3885) (8698, 6252) | 10 | -                     | -                     | 20.12         | (2340.39, 2456.37) (2346.16, 7514.14) (7132.88, 4721.19) (7445.64, 8239.49) (7519.55, 1501.34) |
| B3            | B    | 10 | (3432, 1937) (4470, 2124) (2303, 304) (5220, 9018) (6193, 3599) (1751, 1249) (8320, 4039) (528, 3182) (7253, 7262) (7677, 3117) | 15 | -                     | -                     | 25.48         | (1313.61, 1538.05) (1360.92, 5515.90) (1728.16, 8444.86) (3070.06, 3576.30) (4840.27, 1438.07) (4951.72, 8492.88) (5434.88, 5146.40) (8249.85, 1599.66) (8289.61, 8218.75) (8475.89, 4829.61) |
| C1            | C    | 5  | (8508, 8743) (2984, 8574) (3794, 2826) (3853, 3885) (8698, 6252) | 5  | 0.01                  | DID NOT CONVERGE      | 10.51         | (2510.99, 7477.67) (2510.19, 2490.96) (7509.88, 1216.60) (7505.88, 7860.08) (7568.02, 4118.73) |
| C2            | C    | 10 | (3432, 1937) (4470, 2124) (2303, 304) (5220, 9018) (6193, 3599) (1751, 1249) (8320, 4039) (528, 3182) (7253, 7262) (7677, 3117) | 10 | 0.01                  | DID NOT CONVERGE      | 16.78         | (1238.06, 1608.32) (1472.55, 5549.53) (1717.19, 8464.57) (3074.00, 3430.22) (4754.36, 1280.82) (4989.22, 8302.62) (5434.39, 4712.60) (8284.74, 1623.76) (8304.54, 8258.09) (8388.92, 5002.12) |
| C3            | C    | 10 | (3432, 1937) (4470, 2124) (2303, 304) (5220, 9018) (6193, 3599) (1751, 1249) (8320, 4039) (528, 3182) (7253, 7262) (7677, 3117) | 15 | 0.05                  | CONVERGED AFTER 12 ITERATIONS | 21.12 | (1289.54, 1557.74) (1409.11, 5532.99) (1717.09, 8456.46) (3051.48, 3514.60) (4826.37, 1386.10) (4969.28, 8388.49) (5428.49, 4947.07) (8289.26, 1627.44) (8295.24, 8245.79) (8429.22, 4938.68) |
| C4            | C    | 15 | (4538, 1082) (4777, 1060) (8396, 1345) (6213, 4499) (2171, 7879) (1402, 7200) (1313, 2867) (8056, 2986) (111, 8891) (6132, 5728) (2627, 8924) (2106, 8927) (6170, 8436) (2205, 8116) (7934, 6206) | 30 | 0.01                  | CONVERGED AFTER 30 ITERATIONS | 42.38 | (1038.62, 6350.04) (1073.50, 1360.45) (1521.84, 8734.12) (1804.94, 3801.81) (3646.40, 1394.33) (3747.97, 5756.28) (4003.03, 8605.50) (5344.06, 3684.06) (6204.14, 1302.90) (6402.84, 6279.27) (6424.78, 8931.87) (8215.53, 3561.71) (8811.14, 1115.97) (8821.82, 8442.75) (8891.48, 5849.79) |
| D1            | D    | 5  | (8508, 8743) (2984, 8574) (3794, 2826) (3853, 3885) (8698, 6252) | 5  | 0.05                  | DID NOT CONVERGE      | 10.54         | (2510.99, 7477.67) (2510.19, 2490.96) (7509.88, 1216.60) (7505.88, 7860.08) (7568.02, 4118.73) |
| D2            | D    | 10 | (3432, 1937) (4470, 2124) (2303, 304) (5220, 9018) (6193, 3599) (1751, 1249) (8320, 4039) (528, 3182) (7253, 7262) (7677, 3117) | 10 | 0.05                  | DID NOT CONVERGE      | 18.07         | (1238.06, 1608.32) (1472.55, 5549.53) (1717.19, 8464.57) (3074.00, 3430.22) (4754.36, 1280.82) (4989.22, 8302.62) (5434.39, 4712.60) (8284.74, 1623.76) (8304.54, 8258.09) (8388.92, 5002.12) |
| D3            | D    | 10 | (3432, 1937) (4470, 2124) (2303, 304) (5220, 9018) (6193, 3599) (1751, 1249) (8320, 4039) (528, 3182) (7253, 7262) (7677, 3117) | 20 | 0.05                  | CONVERGED AFTER 12 ITERATIONS | 19.65 | (1289.54, 1557.74) (1409.11, 5532.99) (1717.09, 8456.46) (3051.48, 3514.60) (4826.37, 1386.10) (4969.28, 8388.49) (5428.49, 4947.07) (8289.26, 1627.44) (8295.24, 8245.79) (8429.22, 4938.68) |
| E1            | E    | 10 | (3432, 1937) (4470, 2124) (2303, 304) (5220, 9018) (6193, 3599) (1751, 1249) (8320, 4039) (528, 3182) (7253, 7262) (7677, 3117) | 20 | 0.05                  | CONVERGED AFTER 12 ITERATIONS | 21.31 | (1289.54, 1557.74) (1409.11, 5532.99) (1717.09, 8456.46) (3051.48, 3514.60) (4826.37, 1386.10) (4969.28, 8388.49) (5428.49, 4947.07) (8289.26, 1627.44) (8295.24, 8245.79) (8429.22, 4938.68) |

The number of initial clusters did not seem to influence the computing time as much. The optimized version with the combiner performed better with larger data sets. Additional experiments were conducted with 10 clusters, 30 maximum rounds, a convergence threshold of 0.01, and varying amounts of data points. All tests converged within 19 iterations.

| Experiment ID | Task           | Data Points | Runtime (s) |
|---------------|----------------|-------------|-------------|
| C5            | C (Unoptimized)| 100,000     | 33.57       |
| D4            | D (Optimized)  | 100,000     | 29.06       |
| C6            | C (Unoptimized)| 1,000,000   | 88.83       |
| D5            | D (Optimized)  | 1,000,000   | 68.59       |
| C7            | C (Unoptimized)| 5,000,000   | 347.69      |
| D6            | D (Optimized)  | 5,000,000   | 206.49      |

With larger data sets, the optimized version with the combiner performs much better than the unoptimized one.

# Extensions
## Extension I: Adding Silhouette Measure
The silhouette measure for the clustered points was researched and implemented to evaluate the accuracy of the clustering algorithm. It calculates the similarity of any given point to other points in the same cluster as opposed to points in other clusters and takes on a value between -1.0 and 1.0.

Separate helper classes were introduced to calculate all distances between points within a dataset. These methods were then applied to the output of the MapReduce algorithm in a separate JUnit test. A sample clustering run of 10 predefined centers and 5,000 points resulted in an average silhouette score of 0.35631.

## Extension IIA: Comparing Accuracy with SciKit-Learn’s K-Means
To test the accuracy of the K-Means implementation, the same data was used with SciKit-Learn’s K-Means algorithm. The native `silhouette_score` method calculated the silhouette measure for the result. The output was 0.37, slightly better than the custom K-Means implementation.

## Extension IIB: Comparing Runtime Performance with SciKit-Learn’s K-Means

| Experiment ID | Time Taken (s) Java MapReduce | Time Taken (s) SciKit-Learn | Silhouette Score SciKit-Learn |
|---------------|-------------------------------|-----------------------------|-------------------------------|
| A1            | 5.62                          | 0.69                        | 0.294                         |
| A2            | 5.31                          | 0.71                        | 0.273                         |
| B1            | 12.01                         | 0.70                        | 0.374                         |
| B2            | 20.12                         | 0.73                        | 0.380                         |
| B3            | 25.48                         | 0.73                        | 0.360                         |
| C1            | 10.51                         | 0.70                        | 0.374                         |
| C2            | 16.78                         | 0.73                        | 0.357                         |
| C3            | 21.12                         | 0.75                        | 0.360                         |
| C4            | 42.38                         | 0.78                        | 0.355                         |
| D1            | 10.54                         | 0.70                        | 0.374                         |
| D2            | 18.07                         | 0.72                        | 0.357                         |
| D3            | 19.65                         | 0.76                        | 0.360                         |
| E1            | 21.31                         | 0.74                        | 0.360                         |

While the K-Means implementation fares similarly to SciKit-Learn’s in terms of silhouette accuracy, the latter is much faster when processing a small number of data points, barely changing its performance runtime based on the number of rounds. The bulk of the time with SciKit-Learn’s K-Means seems to be spent calculating silhouette, as without it, the runtimes are below a tenth of a second. As expected, experiments with a smaller number of rounds (e.g. A1 and A2) produced the lowest silhouette score.

Performance was also compared on increasingly larger amounts of data points ranging from 10,000 to 10,000,000, this time without calculating silhouette. All other parameters were held constant at R = 30, K = 10, and a convergence threshold of 5%.

| Experiment ID | Number of Data Points | Time SciKit-Learn (s) | Time Java MapReduce (s) | Java MapReduce Convergence |
|---------------|-----------------------|-----------------------|-------------------------|----------------------------|
| P1            | 10,000                | 0.09                  | 21.71                   | 12 Iterations              |
| P2            | 100,000               | 0.54                  | 23.57                   | 12 Iterations              |
| P3            | 1,000,000             | 4.37                  | 55.81                   | 12 Iterations              |
| P4            | 5,000,000             | 11.42                 | 120.18                  | 12 Iterations              |
| P5            | 10,000,000            | 37.88                 | 236.19                  | 12 Iterations              |
| P6            | 50,000,000            | 166.32                | 1110.46                 | 12 Iterations              |

It seems like the bulk of the time being spent with the implementation of MapReduce is in reading and writing context files. While the performance reduction with SciKit-Learn between 100,000 and 1,000,000 data points is close to 8x, it slightly more than doubled with the MapReduce implementation. This means the solution is scalable and can even match SciKit-Learn’s at higher data volumes. A graph outlining the difference in performance between the two models can be found on the next page. A logarithmic scale on both axes shows that as the number of data points increases, the performance of both models becomes more similar. This is because the effect of I/O time penalties with MapReduce is less critical to its overall performance.

![Time_Comparison_SKLearn_Java_MR](https://github.com/user-attachments/assets/d3c89e10-93b8-440f-b391-3aa36c21e66b)


