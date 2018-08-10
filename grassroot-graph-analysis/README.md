# grassroot-graph-analysis
Collection of Neo4j user-defined functions and procedures for characterizing and analyzing the grassroot graph

## Installation
1. Verify installation of Neo4j version 3.x or higher
2. Install neo4j graph algorithms provided at https://neo4j.com/developer/graph-algorithms/
3. Run “mvn clean package” in this module to produce two jar files in a new "target" folder.
4. Copy "extensions-1.0-SNAPSHOT.jar" produced from preceding step into $NEO4J_HOME/plugins
5. Start (or restart, if already running) Neo4j. 
6. Make the following call: "CALL pagerank.setup()"

## Extensions

Note: procedures are called using the "CALL procedure.name" syntax. Functions are called using the "RETURN function.name" syntax. Both functions and procedures can be integrated as one part of a larger cypher query.

### Pagerank

#### 1. Setup 
Procedure - Writes raw and normalized pagerank scores to the graph as well as raw closeness scores. These scores are necessary for the extensions in this library.

Usage: pagerank.setup()

#### 2. Write
Procedure - Writes raw pagerank scores for all entities based on participation relationships.

Usage: pagerank.write()

#### 3. Normalize
Procedure - Writes normalized pagerank scores for the entities specified.

Usage: pagerank.normalize(entityType, subType)

#### 4. Stats
Function - Returns summary stats for entities specified by the type and range provided.

Usage: pagerank.stats(entityType, subType, firstRank, lastRank, normalized) 

#### 5. Scores
Function - Returns pagerank scores for entities specified by the type and range provided.

Usage: pagerank.scores(entityType, subType, firstRank, lastRank, normalized) 

#### 6. Tiers
Function - Returns counts of users in three general pagerank tiers:
- Tier 1 -> normalized pagerank above 10.0
- Tier 2 -> normalized pagerank between 3.0 - 10.0
- Tier 3 -> normalized pagerank below 3.0

Usage: pagerank.tiers()

#### 7. MeanEntities
Function - Returns the number of mean entities reached at the depth specified for entities determined by the type and range provided.

Usage: pagerank.meanEntities(entityType, subType, firstRank, lastRank, depth) 

#### 8. MeanRelationships
Function - Returns the number of mean relationships at the depth specified for entities determined by the type and range provided.

Usage: pagerank.meanRelationships(entityType, subType, firstRank, lastRank, depth) 

#### 9. CompareMetrics
Function - Returns mean entities or mean relationships at depth specified in intervals of 10 for the top x users by pagerank and closeness metrics.

Usage: pagerank.compareMetrics(entityType, subType, firstRank, lastRank, entityOrRelationship, depth)

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

