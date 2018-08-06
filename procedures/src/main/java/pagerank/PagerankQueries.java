package pagerank;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Result;
import org.neo4j.logging.Log;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Mode;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class PagerankQueries {

    @Context public GraphDatabaseService db;

    @Context public Log log;

    @Procedure(name = "pagerank.write", mode = Mode.WRITE)
    public Stream<RecordWrapper> writeAllScores() {
        Result results = db.execute("CALL algo.pageRank( " +
                "  'MATCH (n) WHERE exists( (n)-[:PARTICIPATES]-() ) RETURN id(n) as id', " +
                "  'MATCH (n1)-[:PARTICIPATES]->(n2) RETURN id(n1) as source, id(n2) as target UNION " +
                "   MATCH (n1)-[:PARTICIPATES]->(n2) RETURN id(n2) as source, id(n1) as target', " +
                "  {graph:'cypher', iterations:100, dampingFactor:0.85, write: true, writeProperty:'pagerankScore'} " +
                ") YIELD nodes, iterations, loadMillis, computeMillis, writeMillis");
        return getResultStream(results);
    }

    @Procedure(name = "pagerank.normalizeUsers", mode = Mode.WRITE)
    public Stream<RecordWrapper> normalizeUserScores() {
        Result results = db.execute("MATCH (n:Actor) " +
                "WHERE n.actorType='INDIVIDUAL' AND n.pagerankScore IS NOT NULL " +
                "WITH n AS user, n.pagerankScore AS pagerankScore " +
                "WITH collect({person:user, pageRank:pagerankScore}) as usersInfo,  " +
                "avg(pagerankScore) AS average,  " +
                "stDevP(pagerankScore) AS stdDev " +
                "UNWIND usersInfo as userInfo " +
                "SET userInfo.person.pagerankNorm=(userInfo.pageRank-average)/stdDev");
        return getResultStream(results);
    }

    @Procedure(name = "pagerank.stats")
    public Stream<RecordWrapper> getSummaryStatistics() {
        Result results = db.execute("MATCH (n:Actor) " +
                "WHERE n.actorType='INDIVIDUAL' " +
                "WITH max(n.pagerankScore) AS maxScore,  " +
                "min(n.pagerankScore) AS minScore,  " +
                "avg(n.pagerankScore) AS average, " +
                "percentileDisc(n.pagerankScore, 0.5) AS median, " +
                "stDevP(n.pagerankScore) AS stdDev " +
                "RETURN maxScore, minScore, maxScore-minScore AS range, average, median, stdDev");
        return getResultStream(results);
    }

    @Procedure(name = "pagerank.topEntities")
    public Stream<RecordWrapper> getTopEntities(@Name("limit") long limit, @Name("entityType") String entityType,
                                                @Name("specificType") String specificType) {
        Result results = db.execute("CALL algo.pageRank.stream( " +
                "  'MATCH (n) WHERE exists( (n)-[:PARTICIPATES]-() ) RETURN id(n) as id', " +
                "  'MATCH (n1)-[:PARTICIPATES]->(n2) RETURN id(n1) as source, id(n2) as target UNION " +
                "   MATCH (n1)-[:PARTICIPATES]->(n2) RETURN id(n2) as source, id(n1) as target', " +
                "  {graph:'cypher', dampingFactor:0.85, iterations:100} " +
                ") YIELD node,score " +
                "WHERE (node.entityType='" + entityType + "' AND node.actorType='" + specificType + "') " +
                "RETURN node.platformUid, score " +
                "ORDER BY score DESC LIMIT " + Long.toString(limit));
        return getResultStream(results);
    }

    @Procedure(name = "pagerank.meanEntitiesReached")
    public Stream<RecordWrapper> getMeanEntitiesReachedAtDepth(@Name("depth") long depth, @Name("upperBound") long upperBound,
                                                               @Name("lowerBound") long lowerBound) {
        long limit = lowerBound - upperBound;
        Result results = db.execute("MATCH (n:Actor) " +
                "WHERE n.actorType='INDIVIDUAL' AND n.pagerankNorm IS NOT NULL " +
                "WITH n AS actor, n.pagerankNorm as pagerank " +
                "ORDER BY pagerank DESC SKIP " + Long.toString(upperBound) + " LIMIT " + Long.toString(limit) + " " +
                "WITH COLLECT({person:actor}) as users " +
                " " +
                "UNWIND users AS user " +
                "WITH user.person as actor " +
                getDepthQuery(depth, false));
        return getResultStream(results);
    }

    @Procedure(name = "pagerank.meanParticipations")
    public Stream<RecordWrapper> getMeanParticipationsAtDepth(@Name("depth") long depth, @Name("upperBound") long upperBound,
                                                              @Name("lowerBound") long lowerBound) {
        long limit = lowerBound - upperBound;
        Result results = db.execute("MATCH (n:Actor) " +
                "WHERE n.actorType='INDIVIDUAL' AND n.pagerankNorm IS NOT NULL " +
                "WITH n AS actor, n.pagerankNorm as pagerank " +
                "ORDER BY pagerank DESC SKIP " + Long.toString(upperBound) + " LIMIT " + Long.toString(limit) + " " +
                "WITH COLLECT({person:actor}) as users " +
                " " +
                "UNWIND users AS user " +
                "WITH user.person as actor " +
                getDepthQuery(depth, true));
        return getResultStream(results);
    }

    public static class RecordWrapper {
        public Map<String, Object> results;
        public RecordWrapper(Map<String, Object> results) {
            this.results = results;
        }
    }

    private Stream<RecordWrapper> getResultStream(Result results) {
        if (results.hasNext()) {
            List<RecordWrapper> resultsList = new ArrayList<>();
            while (results.hasNext())
                resultsList.add(new RecordWrapper(results.next()));
            return resultsList.stream();
        } else {
            log.error("Error! Attempted procedure but received no results, aborting.");
            return null;
        }
    }

    private String getDepthQuery(long depth, boolean participations) {
        String count = "d";
        if (participations) count = "p";
        switch ((int) depth) {
            case 1: return  "MATCH (actor)-[p1:PARTICIPATES]-(d1) " +
                            "WITH actor, COUNT(DISTINCT " + count + "1) AS depth1Connections " +
                            "RETURN avg(depth1Connections)";
            case 2: return  "MATCH (actor)-[p1:PARTICIPATES]-(d1)-[p2:PARTICIPATES]-(d2) " +
                            "WITH actor, COUNT(DISTINCT " + count + "2) AS depth2Connections " +
                            "RETURN avg(depth2Connections)";
            case 3: return  "MATCH (actor)-[p1:PARTICIPATES]-(d1)-[p2:PARTICIPATES]-(d2)-[p3:PARTICIPATES]-(d3) " +
                            "WITH actor, COUNT(DISTINCT " + count + "3) AS depth3Connections " +
                            "RETURN avg(depth3Connections)";
        }
        log.error("Must query for depth 1, 2, or 3.");
        return null;
    }

}