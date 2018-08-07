grassroot-lambdas
Collection of private lambdas to support Grassroot platform

Instructions for setting up neo4j graph analysis procedures:
1. Make sure neo4j version 3.4.1 is installed
2. Install neo4j graph algos by following the instructions at https://neo4j.com/developer/graph-algorithms/
3. Run “mvn clean package” in graph-analysis module to build the procedures jar file
4. Copy the jar file produced in the preceding step to $NEO4J_HOME/plugins
5. Start (/restart) neo4j. 
6. To verify access to the algorithms, type "pagerank." and a list of callable procedures should be listed.
6. Write raw pagerank scores to the graph by calling "pagerank.write()"
7. Write normalized pagerank scores to the graph with the following calls:
- CALL pagerank.normalize("ACTOR", "GROUP")
- CALL pagerank.normalize("ACTOR", "INDIVIDUAL")
- CALL pagerank.normalize("EVENT", "MEETING")
- CALL pagerank.normalize("EVENT", "VOTE")
- CALL pagerank.normalize("EVENT", "TODO")
8. Test by calling "CALL pagerank.stats("ACTOR", "INDIVIDUAL", true, 0, 10)". The following output should be yielded:
{
  "maximum": 139.95452372521055,
  "range": 119.7943979455026,
  "average": 51.90294189280517,
  "median": 38.88166884023575,
  "stddev": 33.810661706567814,
  "minimum": 20.160125779707954
}
