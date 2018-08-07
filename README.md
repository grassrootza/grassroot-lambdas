sroot-lambdas
Collection of private lambdas to support Grassroot platform

Instructions for setting up neo4j graph analysis procedures:
1. Make sure you have neo4j version 3.4.1
2. Install neo4j graph algos by following the instructions at https://neo4j.com/developer/graph-algorithms/
3. Run “mvn clean package” in graph-analysis module to build the procedures jar file
4. Copy the jar file produced in the preceding step to $NEO4J_HOME/plugins
5. Start (/restart) neo4j. To verify access to the algorithms, type "pagerank." and a list of procedures should be listed.
6. Write raw pagerank scores to the graph by calling “pagerank.write()”
7. Write normalized pagerank scores to the graph with the following calls:
- CALL pagerank.normalize(“ACTOR”, “GROUP”)
- CALL pagerank.normalize(“ACTOR”, “INDIVIDUAL”)
- CALL pagerank.normalize(“EVENT”, “MEETING”)
- CALL pagerank.normalize(“EVENT”, “VOTE”)
- CALL pagerank.normalize(“EVENT”, “TODO”)
8. Test by calling “CALL pagerank.stats(“ACTOR”, “INDIVIDUAL”, true, 0, 100)"
