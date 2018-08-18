# grassroot-graph-analysis
Collection of Neo4j user-defined functions and procedures for characterizing and analyzing the grassroot graph

## Installation
1. Verify installation of Neo4j version 3.x or higher
2. Install the neo4j graph algorithms: https://neo4j.com/developer/graph-algorithms/
3. Run “mvn clean package” in graph analysis module to produce a new "taget" folder that should contain two jar files
4. Copy "extensions-1.0-SNAPSHOT.jar" produced from preceding step into the plugins folder of your Neo4j home directory
5. Start Neo4j. The most basic way to do this is to run "NEO4j_HOME/bin/neo4j start" from the directory above NEO4j_HOME
6. Make the following calls in Neo4j desktop or browser: "CALL pagerank.setup()" and "CALL closeness.setup()"

## Extensions

NOTE - If returning numbers, all cypher queries should return floats rather than integers, as javascript formatting in node lambda formats integer return types in unusual manner (i.e. {low: value, high: value}).

NOTE - Procedures are called using the "CALL procedure.name" syntax. Functions are called using the "RETURN function.name" syntax. Both functions and procedures can be integrated into larger cypher queries.

### Common Parameters

@metricType - The name of the metric to be evaluated. Either "PAGERANK" or "CLOSENESS".

@entityType - Graph entity type by which to filter. Either "ACTOR" or "EVENT" until support for INTERACTION is introduced.

@subType - Graph entity sub-type by which to filter. "INDIVIDUAL" or "GROUP", if entity is actor. "MEETING", "VOTE", or "TODO" if entity is event.

@firstRank - The rank of the first entity to include in the query according to the order specified by the algorithm. This ordering could be membership counts, participation counts, pagerank score, closeness score, etc.

@lastRank - The rank of the last entity to include in the query according to the order specified by the algorithm. This ordering could be membership counts, participation counts, pagerank score, closeness score, etc.

### Profiling

Profiling extensions profile the general structure of the graph, such as entity counts, etc. Extensions that are not related to metrics and metric analysis, but rather general graph characterization, should be included here.

#### 1. Counts
Function - Returns counts of all entities and relationships in graph, broken down by subtype.

Usage: profile.counts()

#### 2. GroupMemberships
Function - Returns counts of group memberships in the range provided.

Usage: profile.groupMemberships(firstRank, lastRank)

#### 3. UserParticipations
Function - Returns counts of user participations in the range provided.

Usage: profile.userParticipations(firstRank, lastRank)

#### 4. MembershipStats
Function - Returns summary stats of group membership counts in rank range provided.

Usage: profile.membershipStats(firstRank, lastRank)

#### 5. ParticipationStats
Function - Returns summary stats of user participation counts in rank range provided.

Usage: profile.participationStats(firstRank, lastRank)

### Metric

Metric extensions provide insights into metrics such as pagerank and any algorithms to be added in the future. Functions and procedures with the "metric" syntax should be able to be used for any algorithm (pagerank, closeness, etc.) - the algorithm is determined by the metric type passed into these extensions as an argument. Functions and procedures that are algorithm-specific should be included only in the files for that algorithm.

@normalized - Boolean value indicating whether to use the normalized or raw values of a metric in the query.

Default Parameters - If firstrank and lastrank are left as 0, the extensions will default to use all entities specified. For example, "metric.stats("PAGERANK", "ACTOR", "INDIVIDUAL", 0, 0, false)" would return pagerank summary statistics for all users, not limited to a specific range. In addition, if entity-type and sub-type are passed as empty strings, all entities are used for the query. For example, "metric.stats("PAGERANK", "", "", 0, 0, false)" would return summary statistics for pagerank for all entities in the graph, not just users. Generally, if last rank is left as 0, it will default to the full count of entities that are specified for the query. Additionally, if sub-type is passed as empty string and entity-type is not, all entities with entity-type will be used in the query, regardless of sub-type.

#### 1. Normalize
Procedure - Writes normalized metric scores for the entities specified.

Usage: metric.normalize(metricType, entityType, subType)

#### 2. Stats
Function - Returns summary stats for entities specified by the type and range provided.

Usage: metric.stats(metricType, entityType, subType, firstRank, lastRank, normalized)

#### 3. ScoresByRankRange
Function - Returns metric scores in rank range for entities specified by the type and range provided. For example, passing in firstRank=0 and lastRank=100 would return a list of the top 100 metric (pagerank or closeness) scores.

Usage: metric.scoresByRankRange(metricType, entityType, subType, firstRank, lastRank, normalized)

#### 4. ScoresByScoreRange
Function - Returns metric scores in score range for entities specified by the type and range provided. For example, passing in bestScore=1.5 and worstScore=1.0 would give you all scores for a metric that are between 1.0 and 1.5.

Usage: metric.scoresByScoreRange(metricType, entityType, subType, bestScore, worstScore, normalized)

### Pagerank

As implemented in this library, pagerank is written based on participation relationships in the grassroot graph. The algorithm is also written in an undirected fashion, meaning if there is a participation relationship from one entity1 to entity2, the algorithm is written as if there is also a participation relationship form entity2 to entity1. This decision was made due to the structure of the graph - the vast majority of participation relationships are users -> groups or users -> events, so if reciprocal participation relationships were not implemented, the pagerank algorithm would encounter a large portion of dead ends. With regard to pagerank parameters, 100 iterations are used because below this threshold the algorithm does not always converge to final values when applied to the grassroot graph (i.e. calls with 50 iterations versus 60 iterations would produce different pagerank values). A damping factor of 0.85 is chosen, as is standard.

#### 1. Setup 
Procedure - Writes raw and normalized pagerank scores to the graph.

Usage: pagerank.setup()

#### 2. Write
Procedure - Writes raw pagerank scores for all entities based on participation relationships.

Usage: pagerank.write()

#### 3. Tiers
Function - Returns counts of users in three general pagerank tiers:
- Tier 1 -> normalized pagerank above 10.0
- Tier 2 -> normalized pagerank between 3.0 - 10.0
- Tier 3 -> normalized pagerank below 3.0

Usage: pagerank.tiers()

### Closeness

As with pagerank above, closeness is also written based on participation relationships in the grassroot graph that are treated in an undirected fashion. If not undirected, the algorithm would not provide meaningful insight (for example, an event with hundreds of participants but with no outgoing participations would have a closeness of 0 because no shortest paths would pass through that event since it has no outgoing relationships).

#### 1. Setup
Procedure - Writes raw and normalized closeness scores to the graph.

Usage: closeness.setup()

#### 2. Write
Procedure - Writes raw closeness scores for all entities based on participation relationships.

Usage: closeness.write()

#### 3. Tiers
Function - Returns counts of users in four general pagerank tiers:
- Tier 1 -> normalized pagerank above 1.5
- Tier 2 -> normalized pagerank between 0.5-1.5
- Tier 3 -> normalized pagerank between -0.4-0.5
- Tier 4 -> normalized pagerank below -0.4

Usage: closeness.tiers()

### Connections

Connections extensions provide analysis of the number of connections that nodes possess, where nodes are ordered by a provided algorithm. Connections take the form of entities or relationships. 

@countEntities - Boolean value indicating whether to count entities. If false, relationships are counted instead.

@depth - The depth at which entities or relationships should be counted. Depth of 1 corresponds to the entities/relationships that are immediately connected to a node. Depth 2 corresponds to the entities/relationships that are 1 level away from a node. Only depths of 1, 2, and 3 are currently supported, as these calls are intensive.

#### 1. Mean
Function - Returns the number of mean entities reached at the depth specified for entities determined by the type and range provided.

Usage: connections.mean(metricType, entityType, subType, firstRank, lastRank, countEntities, depth)

Sample call: connections.mean("PAGERANK", "ACTOR", "INDIVIDUAL", 0, 100, true, 1)

#### 2. MeanList
Function - Returns the number of mean relationships at the depth specified for entities determined by the type and range provided.

Usage: connections.meanList(metricType, entityType, subType, firstRank, lastRank, countEntities, depth)

Sample call: connections.meanList("PAGERANK", "ACTOR", "INDIVIDUAL", 0, 100, true, 1)

#### 3. CompareMetrics
Function - Returns mean entities or mean relationships at depth specified in intervals of 10 for the top x users by pagerank and closeness metrics.

Usage: connections.compareMetrics(metric1Type, metric2Type, entityType, subType, firstRank, lastRank, countEntities, depth)

Sample call: connections.compareMetrics("PAGERANK", "CLOSENESS", "ACTOR", "INDIVIDUAL", 0, 100, true, 1)
