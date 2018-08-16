# grassroot-graph-analysis
Collection of Neo4j user-defined functions and procedures for characterizing and analyzing the grassroot graph

## Installation
1. Verify installation of Neo4j version 3.x or higher
2. Install neo4j graph algorithms provided at https://neo4j.com/developer/graph-algorithms/
3. Run “mvn clean package” in this module to produce two jar files in a new "target" folder
4. Copy "extensions-1.0-SNAPSHOT.jar" produced from preceding step into $NEO4J_HOME/plugins
5. Start (or restart, if already running) Neo4j
6. Make the following call: "CALL metric.setup()"

## Extensions

Note: procedures are called using the "CALL procedure.name" syntax. Functions are called using the "RETURN function.name" syntax. Both functions and procedures can be integrated as one part of a larger cypher query.

### Profiling

#### 1. Counts
Function - Returns counts of all entities and relationships in graph, broken down by subtype.

Usage: profile.counts()

#### 2. GroupMemberships
Function - Returns counts of group memberships in the range provided.

Usage: profile.groupMemberships(firstRank, lastRank)

#### 3. UserParticipations
Function - Returns counts of user participations in the range provided.

Usage: profile.userParticipations(firstRank, lastRank)

### Metric

#### 1. Normalize
Procedure - Writes normalized pagerank scores for the entities specified.

Usage: metric.normalize(metricType, entityType, subType)

#### 2. Stats
Function - Returns summary stats for entities specified by the type and range provided.

Usage: metric.stats(metricType, entityType, subType, firstRank, lastRank, normalized)

#### 3. Scores
Function - Returns pagerank scores for entities specified by the type and range provided.

Usage: metric.scores(metricType, entityType, subType, firstRank, lastRank, normalized)

### Pagerank

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

#### 1. Mean
Function - Returns the number of mean entities reached at the depth specified for entities determined by the type and range provided.

Usage: connections.mean(metricType, entityType, subType, firstRank, lastRank, countEntities, depth)

#### 2. MeanList
Function - Returns the number of mean relationships at the depth specified for entities determined by the type and range provided.

Usage: connections.meanList(metricType, entityType, subType, firstRank, lastRank, countEntities, depth)

#### 3. CompareMetrics
Function - Returns mean entities or mean relationships at depth specified in intervals of 10 for the top x users by pagerank and closeness metrics.

Usage: connections.compareMetrics(metric1Type, metric2Type, entityType, subType, firstRank, lastRank, countEntities, depth)
