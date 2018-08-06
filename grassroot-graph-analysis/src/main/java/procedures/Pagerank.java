package procedures;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.logging.Log;
import org.neo4j.procedure.*;
import org.neo4j.graphdb.Result;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class Pagerank {

    @Context public GraphDatabaseService db;

    @Context public Log log;

    public static final String nameRaw = "pagerankScore";
    public static final String nameNorm = "pagerankNorm";

    @Procedure(name = "pagerank.write", mode = Mode.WRITE)
    @Description("Write raw pagerank for all entities")
    public Stream<RecordWrapper> writeScores() {
        Result results = db.execute("CALL algo.pageRank( " +
                " 'MATCH (n) WHERE EXISTS( (n)-[:PARTICIPATES]-() ) RETURN id(n) as id', " +
                " 'MATCH (n1)-[:PARTICIPATES]->(n2) RETURN id(n1) as source, id(n2) as target UNION " +
                "  MATCH (n1)-[:PARTICIPATES]->(n2) RETURN id(n2) as source, id(n1) as target', " +
                " {graph:'cypher', iterations:100, dampingFactor:0.85, write: true, writeProperty:'pagerankScore'} " +
                ") YIELD nodes, loadMillis, computeMillis, writeMillis");
        return getResultStream(results);
    }

    @Procedure(name = "pagerank.normalize", mode = Mode.WRITE)
    @Description("Write normalized pagerank for user entities")
    public void normalizeScores() {
        db.execute("MATCH (n:Actor) " +
                " WHERE n.actorType='INDIVIDUAL' AND n.pagerankScore IS NOT NULL " +
                " WITH n AS user, n.pagerankScore AS pagerankScore " +
                " WITH collect({user:user, pageRank:pagerankScore}) AS usersInfo,  " +
                " avg(pagerankScore) AS average,  " +
                " stDevP(pagerankScore) AS stddev " +
                " UNWIND usersInfo as userInfo " +
                " SET userInfo.user.pagerankNorm=(userInfo.pageRank-average)/stddev");
    }

    @Procedure(name = "pagerank.stats")
    @Description("Return summary pagerank statistics for user entities")
    public Stream<RecordWrapper> getStats() {
        Result results = db.execute("MATCH (n:Actor) " +
                " WHERE n.actorType='INDIVIDUAL' " +
                " WITH min(n.pagerankScore) AS minimum,  " +
                " max(n.pagerankScore) AS maximum,  " +
                " avg(n.pagerankScore) AS average, " +
                " percentileDisc(n.pagerankScore, 0.5) AS median, " +
                " stDevP(n.pagerankScore) AS stddev " +
                " RETURN minimum, maximum, maximum - minimum AS range, average, median, stddev");
        return getResultStream(results);
    }

    @Procedure(name = "pagerank.scores")
    @Description("Return list of pagerank scores for user entities")
    public Stream<RecordWrapper> getScores() {
        Result results = db.execute("MATCH (n:Actor) " +
                " WHERE n.actorType='INDIVIDUAL' AND n.pagerankScore IS NOT NULL " +
                " RETURN n.pagerankScore ORDER BY n.pagerankScore DESC");
        return getResultStream(results);
    }

    @Procedure(name = "pagerank.range")
    @Description("Return user entities in specified pagerank range")
    public Stream<RecordWrapper> getRange(@Name("upperBound") long upperBound, @Name("lowerBound") long lowerBound) {
        long limit = lowerBound - upperBound;
        Result results = db.execute("MATCH (n)" +
                " WHERE n.actorType='INDIVIDUAL' AND n.pagerankScore IS NOT NULL" +
                " RETURN n AS actor, n.pagerankScore AS pagerank" +
                " ORDER BY pagerank DESC " +
                " SKIP " + Long.toString(upperBound) +
                " LIMIT " + Long.toString(limit));
        return getResultStream(results);
    }

    @Procedure(name = "pagerank.meanEntitiesAtDepth")
    @Description("Calculate mean entities reached at the specified depth for entities in a given pagerank range")
    public Stream<RecordWrapper> getMeanEntitiesAtDepth(@Name("depth") long depth, @Name("upperBound") long upperBound,
                                                        @Name("lowerBound") long lowerBound) {
        long limit = lowerBound - upperBound;
        Result results = db.execute("MATCH (n:Actor) " +
                " WHERE n.actorType='INDIVIDUAL' AND n.pagerankNorm IS NOT NULL " +
                " WITH n AS user, n.pagerankNorm as pagerank " +
                " ORDER BY pagerank DESC " +
                " SKIP " + Long.toString(upperBound) +
                " LIMIT " + Long.toString(limit) +
                " WITH COLLECT({user:user}) as users " +
                " UNWIND users AS user " +
                " WITH user.user as actor " +
                getDepthQuery(depth, false));
        return getResultStream(results);
    }

    @Procedure(name = "pagerank.meanRelationshipsAtDepth")
    @Description("Calculate mean relationships at the specified depth for entities in a given pagerank range")
    public Stream<RecordWrapper> getMeanRelationshipsAtDepth(@Name("depth") long depth, @Name("upperBound") long upperBound,
                                                             @Name("lowerBound") long lowerBound) {
        long limit = lowerBound - upperBound;
        Result results = db.execute("MATCH (n:Actor) " +
                " WHERE n.actorType='INDIVIDUAL' AND n.pagerankNorm IS NOT NULL " +
                " WITH n AS user, n.pagerankNorm as pagerank " +
                " ORDER BY pagerank DESC " +
                " SKIP " + Long.toString(upperBound) +
                " LIMIT " + Long.toString(limit) +
                " WITH COLLECT({user:user}) as users " +
                " UNWIND users AS user " +
                " WITH user.user as actor " +
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

    private boolean typesAreValid(String entityType, String subType) {
        if (entityType == null || subType == null) return false;
        if ("".equals(entityType) && !"".equals(subType)) return false;
        entityType = entityType.toUpperCase();
        subType = subType.toUpperCase();
        if (!("ACTOR".equals(entityType) || "EVENT".equals(entityType) || "".equals(entityType))) return false;
        if ("ACTOR".equals(entityType) && !("INDIVIDUAL".equals(subType) || "GROUP".equals(subType) ||
                "MOVEMENT".equals(subType) || "".equals(subType))) return false;
        if ("EVENT".equals(entityType) && !("MEETING".equals(subType) || "VOTE".equals(subType) ||
                "TODO".equals(subType) || "".equals(subType))) return false;
        return true;
    }

    private String getDepthQuery(long depth, boolean participations) {
        String count = "d";
        if (participations) count = "p";
        switch ((int) depth) {
            case 1: return  "MATCH (actor)-[p1:PARTICIPATES]-(d1) " +
                    " WITH actor, COUNT(DISTINCT " + count + "1) AS depth1Connections " +
                    " RETURN avg(depth1Connections)";
            case 2: return  "MATCH (actor)-[p1:PARTICIPATES]-(d1)-[p2:PARTICIPATES]-(d2) " +
                    " WITH actor, COUNT(DISTINCT " + count + "2) AS depth2Connections " +
                    " RETURN avg(depth2Connections)";
            case 3: return  "MATCH (actor)-[p1:PARTICIPATES]-(d1)-[p2:PARTICIPATES]-(d2)-[p3:PARTICIPATES]-(d3) " +
                    " WITH actor, COUNT(DISTINCT " + count + "3) AS depth3Connections " +
                    " RETURN avg(depth3Connections)";
        }
        log.error("Must query for depth 1, 2, or 3.");
        return null;
    }

}