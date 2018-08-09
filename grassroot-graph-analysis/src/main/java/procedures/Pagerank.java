package procedures;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.logging.Log;
import org.neo4j.procedure.*;
import org.neo4j.graphdb.Result;

import java.util.Map;
import java.util.stream.Stream;

public class Pagerank {

    @Context public GraphDatabaseService db;

    @Context public Log log;

    public static final String pagerankRaw = "pagerankRaw";
    public static final String pagerankNorm = "pagerankNorm";

    public static class RecordWrapper {
        public Map<String, Object> results;
        public RecordWrapper(Map<String, Object> results) {
            this.results = results;
        }
    }

    private Stream<RecordWrapper> getResultStream(Result results) {
        return results.hasNext() ? results.stream().map(RecordWrapper::new) : null;
    }

    @Procedure(name = "pagerank.setup", mode = Mode.WRITE)
    @Description("Write information to graph necessary for use of procedures")
    public void setupProcedures() {
        log.info("Writing raw and normalized pagerank scores to graph");
        db.execute("CALL pagerank.write()");
        db.execute("CALL pagerank.normalize('ACTOR', 'INDIVIDUAL')");
        db.execute("CALL pagerank.normalize('ACTOR', 'GROUP')");
        db.execute("CALL pagerank.normalize('EVENT', 'MEETING')");
        db.execute("CALL pagerank.normalize('EVENT', 'VOTE')");
        db.execute("CALL pagerank.normalize('EVENT', 'TODO')");
    }

    @Procedure(name = "pagerank.write", mode = Mode.WRITE)
    @Description("Write raw pagerank for all entities")
    public Stream<RecordWrapper> writeScores() {
        log.info("Writing raw pagerank scores to graph");
        Result results = db.execute("CALL algo.pageRank(" +
                " 'MATCH (n) WHERE EXISTS( (n)-[:PARTICIPATES]-() ) RETURN id(n) as id'," +
                " 'MATCH (n1)-[:PARTICIPATES]->(n2) RETURN id(n1) as source, id(n2) as target UNION" +
                "  MATCH (n1)-[:PARTICIPATES]->(n2) RETURN id(n2) as source, id(n1) as target'," +
                " {graph:'cypher', iterations:100, dampingFactor:0.85, write: true, writeProperty:'" + pagerankRaw + "'}" +
                ") YIELD nodes, loadMillis, computeMillis, writeMillis");
        return getResultStream(results);
    }

    @Procedure(name = "pagerank.normalize", mode = Mode.WRITE)
    @Description("Write normalized pagerank for specified entities")
    public void normalizeScores(@Name(value = "entityType") String entityType, @Name(value = "subType") String subType) {
        log.info("Writing normalized pagerank scores to graph");
        if (!typesAreValid(entityType, subType)) return;
        db.execute(getTypeFilter(entityType, subType, pagerankRaw) +
                " WITH n AS entity, n." + pagerankRaw + " AS " + pagerankRaw +
                " WITH collect({entity:entity, pageRank:" + pagerankRaw + "}) AS entitiesInfo," +
                " avg(" + pagerankRaw + ") AS average," +
                " stDevP(" + pagerankRaw + ") AS stddev" +
                " UNWIND entitiesInfo as entityInfo" +
                " SET entityInfo.entity." + pagerankNorm + "=(entityInfo.pageRank-average)/stddev");
    }

    @Procedure(name = "pagerank.stats")
    @Description("Return pagerank statistics")
    public Stream<RecordWrapper> getStats(@Name(value = "entityType", defaultValue = "") String entityType,
                                          @Name(value = "subType", defaultValue = "") String subType,
                                          @Name(value = "firstRank", defaultValue = "0") long firstRank,
                                          @Name(value = "lastRank", defaultValue = "0") long lastRank,
                                          @Name(value = "normalized", defaultValue = "false") boolean normalized) {
        log.info("Calculating pagerank statistics");
        String pagerank = normalized ? pagerankNorm : pagerankRaw;
        if (!paramsAreValid(entityType, subType, firstRank, lastRank, null)) return null;
        Result results = db.execute(filterQuery(entityType, subType, firstRank, lastRank, pagerank) +
                " WITH min(pagerank) AS minimum," +
                " max(pagerank) AS maximum," +
                " avg(pagerank) AS average," +
                " percentileDisc(pagerank, 0.5) AS median," +
                " stDevP(pagerank) AS stddev" +
                " RETURN minimum, maximum, maximum - minimum AS range, average, median, stddev");
        return getResultStream(results);
    }

    @Procedure(name = "pagerank.scores")
    @Description("Return entities in range")
    public Stream<RecordWrapper> getScores(@Name(value = "entityType", defaultValue = "") String entityType,
                                           @Name(value = "subType", defaultValue = "") String subType,
                                           @Name(value = "firstRank", defaultValue = "0") long firstRank,
                                           @Name(value = "lastRank", defaultValue = "0") long lastRank,
                                           @Name(value = "normalized", defaultValue = "false") boolean normalized) {
        log.info("Obtaining pagerank scores");
        String pagerank = normalized ? pagerankNorm : pagerankRaw;
        if (!paramsAreValid(entityType, subType, firstRank, lastRank, null)) return null;
        Result results = db.execute(filterQuery(entityType, subType, firstRank, lastRank, pagerank) + " RETURN pagerank");
        return getResultStream(results);
    }

    @Procedure(name = "pagerank.meanEntities")
    @Description("Calculate mean entities reached at depth 1, 2, or 3")
    public Stream<RecordWrapper> getMeanEntitiesAtDepth(@Name(value = "entityType") String entityType,
                                                        @Name(value = "subType") String subType,
                                                        @Name(value = "firstRank") long firstRank,
                                                        @Name(value = "lastRank") long lastRank,
                                                        @Name(value = "depth") long depth) {
        log.info("Getting mean entities reached at depth {}", depth);
        if (!paramsAreValid(entityType, subType, firstRank, lastRank, depth)) return null;
        Result results = db.execute(filterQuery(entityType, subType, firstRank, lastRank, pagerankRaw) + depthQuery(depth, true));
        return getResultStream(results);
    }

    @Procedure(name = "pagerank.meanRelationships")
    @Description("Calculate mean relationships at depth 1, 2, or 3")
    public Stream<RecordWrapper> getMeanRelationshipsAtDepth(@Name(value = "entityType") String entityType,
                                                             @Name(value = "subType") String subType,
                                                             @Name(value = "firstRank") long firstRank,
                                                             @Name(value = "lastRank") long lastRank,
                                                             @Name(value = "depth") long depth) {
        log.info("Getting mean relationships reached at depth {}", depth);
        if (!paramsAreValid(entityType, subType, firstRank, lastRank, depth)) return null;
        Result results = db.execute(filterQuery(entityType, subType, firstRank, lastRank, pagerankRaw) + depthQuery(depth, false));
        return getResultStream(results);
    }

    private String filterQuery(String entityType, String subType, long firstRank, long lastRank, String pagerank) {
        String typeFilter = getTypeFilter(entityType, subType, pagerank);
        return typeFilter +
                " WITH n AS entity, n." + pagerank + " AS pagerank" +
                " ORDER BY pagerank DESC" +
                " SKIP " + Long.toString(firstRank) +
                " LIMIT " + Long.toString(getLimit(firstRank, lastRank, typeFilter));
    }

    private String getTypeFilter(String entityType, String subType, String pagerank) {
        String entityFilter = entityType.isEmpty() ? "" :
                isActor(entityType) ? ":Actor" : ":Event";
        String subTypeFilter = subType.isEmpty() ? "" :
                isActor(entityType) ? "n.actorType='" + subType + "' AND " : "n.eventType='" + subType + "' AND ";
        return  "MATCH (n" + entityFilter + ") WHERE " + subTypeFilter + "n." + pagerank + " IS NOT NULL";
    }

    private long getLimit(long firstRank, long lastRank, String typeFilter) {
        if (lastRank == 0) {
            Result userCount = db.execute(typeFilter + " RETURN COUNT(n)");
            lastRank = (long) userCount.next().get(userCount.columns().get(0));
        }
        return lastRank - firstRank;
    }

    private String depthQuery(long depth, boolean countingEntities) {
        String connection = (countingEntities ? "e" : "p") + Long.toString(depth);
        String depthMatchingQuery = getDepthMatchingQuery(depth);
        return  " WITH COLLECT({e:entity}) as entities" +
                " UNWIND entities AS entity" +
                " WITH entity.e as graphEntity " + depthMatchingQuery +
                " WITH graphEntity, COUNT(DISTINCT " + connection + ") AS connections" +
                " RETURN avg(connections) AS connectionCount";
    }

    private String getDepthMatchingQuery(long depth) {
        switch ((int) depth) {
            case 1: return "MATCH (graphEntity)-[p1:PARTICIPATES]-(e1)";
            case 2: return "MATCH (graphEntity)-[p1:PARTICIPATES]-(e1)-[p2:PARTICIPATES]-(e2)";
            case 3: return "MATCH (graphEntity)-[p1:PARTICIPATES]-(e1)-[p2:PARTICIPATES]-(e2)-[p3:PARTICIPATES]-(e3)";
            default: log.error("Error! Invalid depth, should have been caught by upstream validation"); return null;
        }
    }

    private boolean paramsAreValid(String entityType, String subType, Long firstRank, Long lastRank, Long depth) {
        if (typesAreValid(entityType, subType) && boundsAreValid(firstRank, lastRank) && depthIsValid(depth)) return true;
        log.error("Error! Invalid parameters"); return false;
    }

    private boolean typesAreValid(String entityType, String subType) {
        return (entityType != null && subType != null) && !(entityType.isEmpty() && !subType.isEmpty()) &&
                entityTypeIsValid(entityType) && subTypeIsValid(entityType, subType);
    }

    private boolean entityTypeIsValid(String entityType) {
        return entityType.isEmpty() || isActor(entityType) || isEvent(entityType);
    }

    private boolean subTypeIsValid(String entityType, String subType) {
        subType = subType.toUpperCase();
        return entityType.isEmpty() || isActor(entityType) ?
                ("INDIVIDUAL".equals(subType) || "GROUP".equals(subType) || "MOVEMENT".equals(subType) || subType.isEmpty()) :
                ("MEETING".equals(subType) || "VOTE".equals(subType) || "TODO".equals(subType) || subType.isEmpty());
    }

    private boolean boundsAreValid(Long firstRank, Long lastRank) {
        return (firstRank == null && lastRank == null) || lastRank == 0 || lastRank > firstRank;
    }

    private boolean depthIsValid(Long depth) {
        return  depth == null || depth == 1 || depth == 2 || depth == 3;
    }

    private boolean isActor(String entityType) {
        return "ACTOR".equals(entityType.toUpperCase());
    }

    private boolean isEvent(String entityType) {
        return "EVENT".equals(entityType.toUpperCase());
    }

}