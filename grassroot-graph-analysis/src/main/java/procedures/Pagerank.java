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

    public static final String pagerankRaw = "pagerankRaw";
    public static final String pagerankNorm = "pagerankNorm";

    @Procedure(name = "pagerank.write", mode = Mode.WRITE)
    @Description("Write raw pagerank for all entities")
    public Stream<RecordWrapper> writeScores() {
        Result results = db.execute("CALL algo.pageRank( " +
                " 'MATCH (n) WHERE EXISTS( (n)-[:PARTICIPATES]-() ) RETURN id(n) as id', " +
                " 'MATCH (n1)-[:PARTICIPATES]->(n2) RETURN id(n1) as source, id(n2) as target UNION " +
                "  MATCH (n1)-[:PARTICIPATES]->(n2) RETURN id(n2) as source, id(n1) as target', " +
                " {graph:'cypher', iterations:100, dampingFactor:0.85, write: true, writeProperty:'" + pagerankRaw + "'} " +
                ") YIELD nodes, loadMillis, computeMillis, writeMillis");
        return getResultStream(results);
    }

    @Procedure(name = "pagerank.normalize", mode = Mode.WRITE)
    @Description("Write normalized pagerank for user entities")
    public void normalizeScores(@Name(value = "entityType", defaultValue = "ACTOR") String entityType,
                                @Name(value = "subType", defaultValue = "INDIVIDUAL") String subType) {
        String entityTypeQuery = "ACTOR".equals(entityType.toUpperCase()) ? "n.actorType" : "n.eventType";
        db.execute("MATCH (n) " +
                " WHERE " + entityTypeQuery + "='" + subType + "' AND n." + pagerankRaw + " IS NOT NULL " +
                " WITH n AS entity, n." + pagerankRaw + " AS " + pagerankRaw + " " +
                " WITH collect({entity:entity, pageRank:" + pagerankRaw + "}) AS entitiesInfo,  " +
                " avg(" + pagerankRaw + ") AS average,  " +
                " stDevP(" + pagerankRaw + ") AS stddev " +
                " UNWIND entitiesInfo as entityInfo " +
                " SET entityInfo.entity." + pagerankNorm + "=(entityInfo.pageRank-average)/stddev");
    }

    @Procedure(name = "pagerank.stats")
    @Description("Return summary pagerank statistics for user entities")
    public Stream<RecordWrapper> getStats(@Name(value = "entityType", defaultValue = "ACTOR") String entityType,
                                          @Name(value = "subType", defaultValue = "INDIVIDUAL") String subType,
                                          @Name(value = "normalized", defaultValue = "true") boolean normalized,
                                          @Name(value = "upperBound", defaultValue = "0") long upperBound,
                                          @Name(value = "lowerBound", defaultValue = "0") long lowerBound) {
        if (!typesAreValid(entityType, subType)) {
            log.error("Error! Invalid entity type or sub type provided, aborting.");
            return null;
        }
        String pagerank = String.valueOf(normalized).equals("true") ? pagerankNorm : pagerankRaw;
        Result results = db.execute(getRangeQuery(entityType, subType, upperBound, lowerBound, pagerank) +
                " WITH min(pagerank) AS minimum,  " +
                " max(pagerank) AS maximum,  " +
                " avg(pagerank) AS average, " +
                " percentileDisc(pagerank, 0.5) AS median, " +
                " stDevP(pagerank) AS stddev " +
                " RETURN minimum, maximum, maximum - minimum AS range, average, median, stddev");
        return getResultStream(results);
    }

    @Procedure(name = "pagerank.scores")
    @Description("Return user entities in specified pagerank range")
    public Stream<RecordWrapper> getScores(@Name(value = "entityType", defaultValue = "ACTOR") String entityType,
                                           @Name(value = "subType", defaultValue = "INDIVIDUAL") String subType,
                                           @Name(value = "upperBound", defaultValue = "0") long upperBound,
                                           @Name(value = "lowerBound", defaultValue = "0") long lowerBound,
                                           @Name(value = "normalized", defaultValue = "true") boolean normalized) {
        if (!typesAreValid(entityType, subType)) {
            log.error("Error! Invalid entity type or sub type provided, aborting.");
            return null;
        }
        String pagerank = String.valueOf(normalized).equals("true") ? pagerankNorm : pagerankRaw;
        Result results = db.execute(getRangeQuery(entityType, subType, upperBound, lowerBound, pagerank) + " RETURN entity, pagerank");
        return getResultStream(results);
    }

    @Procedure(name = "pagerank.meanEntitiesAtDepth")
    @Description("Calculate mean entities reached at depth 1, 2, or 3")
    public Stream<RecordWrapper> getMeanEntitiesAtDepth(@Name(value = "depth") long depth,
                                                        @Name(value = "entityType") String entityType,
                                                        @Name(value = "subType") String subType,
                                                        @Name(value = "upperBound") long upperBound,
                                                        @Name(value = "lowerBound") long lowerBound,
                                                        @Name(value = "normalized", defaultValue = "true") boolean normalized) {
        if (!typesAreValid(entityType, subType)) {
            log.error("Error! Invalid entity type or sub type provided, aborting.");
            return null;
        }
        String pagerank = String.valueOf(normalized).equals("true") ? pagerankNorm : pagerankRaw;
        Result results = db.execute(getRangeQuery(entityType, subType, upperBound, lowerBound, pagerank) + getDepthQuery(depth, false));
        return getResultStream(results);
    }

    @Procedure(name = "pagerank.meanRelationshipsAtDepth")
    @Description("Calculate mean relationships at depth 1, 2, or 3")
    public Stream<RecordWrapper> getMeanRelationshipsAtDepth(@Name(value = "depth") long depth,
                                                             @Name(value = "entityType") String entityType,
                                                             @Name(value = "subType") String subType,
                                                             @Name(value = "upperBound") long upperBound,
                                                             @Name(value = "lowerBound") long lowerBound,
                                                             @Name(value = "normalized", defaultValue = "true") boolean normalized) {
        if (!typesAreValid(entityType, subType)) {
            log.error("Error! Invalid entity type or sub type provided, aborting.");
            return null;
        }
        String pagerank = String.valueOf(normalized).equals("true") ? pagerankNorm : pagerankRaw;
        Result results = db.execute(getRangeQuery(entityType, subType, upperBound, lowerBound, pagerank) + getDepthQuery(depth, true));
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
//        if ("".equals(entityType) && !"".equals(subType)) return false;

        entityType = entityType.toUpperCase();
        subType = subType.toUpperCase();

        if (!("ACTOR".equals(entityType) || "EVENT".equals(entityType))) return false;
        if ("ACTOR".equals(entityType) && !("INDIVIDUAL".equals(subType) || "GROUP".equals(subType) || "MOVEMENT".equals(subType))) return false;
        if ("EVENT".equals(entityType) && !("MEETING".equals(subType) || "VOTE".equals(subType) || "TODO".equals(subType))) return false;
        return true;
    }

    private String getRangeQuery(String entityType, String subType, long upperBound, long lowerBound, String pagerank) {
        String entityTypeQuery = "ACTOR".equals(entityType.toUpperCase()) ? "n.actorType" : "n.eventType";
        if (lowerBound == 0) {
            Result userCount = db.execute("MATCH (n:Actor) WHERE n.actorType='INDIVIDUAL' RETURN COUNT(n)");
            for (String key : userCount.columns()) lowerBound = (long) userCount.next().get(key);
        }
        long limit = lowerBound - upperBound;
        return  " MATCH (n)" +
                " WHERE " + entityTypeQuery + "='" + subType + "' AND n." + pagerank + " IS NOT NULL" +
                " WITH n AS entity, n." + pagerank + " AS pagerank" +
                " ORDER BY pagerank DESC " +
                " SKIP " + Long.toString(upperBound) +
                " LIMIT " + Long.toString(limit);
    }

    private String getDepthQuery(long depth, boolean participations) {
        String letter = participations ? "p" : "d";
        String baseQuery = " WITH COLLECT({entity:entity}) as entities " +
                " UNWIND entities AS entity " +
                " WITH entity.entity as graphEntity ";
        switch ((int) depth) {
            case 1: return  baseQuery + "MATCH (graphEntity)-[p1:PARTICIPATES]-(d1) " +
                    " WITH graphEntity, COUNT(DISTINCT " + letter + "1) AS depth1Connections " +
                    " RETURN avg(depth1Connections)";
            case 2: return  baseQuery + "MATCH (graphEntity)-[p1:PARTICIPATES]-(d1)-[p2:PARTICIPATES]-(d2) " +
                    " WITH graphEntity, COUNT(DISTINCT " + letter + "2) AS depth2Connections " +
                    " RETURN avg(depth2Connections)";
            case 3: return  baseQuery + "MATCH (graphEntity)-[p1:PARTICIPATES]-(d1)-[p2:PARTICIPATES]-(d2)-[p3:PARTICIPATES]-(d3) " +
                    " WITH graphEntity, COUNT(DISTINCT " + letter + "3) AS depth3Connections " +
                    " RETURN avg(depth3Connections)";
        }
        log.error("Must query for depth 1, 2, or 3.");
        return null;
    }

}