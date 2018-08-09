# grassroot-graph-analysis
Collection of Neo4j procedures for characterizing and analyzing the grassroot graph

## Installation
1. Verify installation of Neo4j version 3.x or higher
2. Install neo4j graph algos by following the instructions at https://neo4j.com/developer/graph-algorithms/
3. Run “mvn clean package” in this module to produce two jar files in a new "target" folder.
4. Copy "procedures-1.0-SNAPSHOT.jar" produced from preceding step into $NEO4J_HOME/plugins
5. Start (or restart, if already running) Neo4j. 
6. Make the following call in the browser to set up procedures for use: "CALL pagerank.setup"
