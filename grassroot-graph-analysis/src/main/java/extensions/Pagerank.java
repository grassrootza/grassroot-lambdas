package extensions;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.logging.Log;
import org.neo4j.procedure.*;
import org.neo4j.graphdb.Result;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static extensions.ExtensionUtils.*;

public class Pagerank {

    private static final String pagerankRaw = "pagerankRaw";
    private static final String pagerankNorm = "pagerankNorm";
    private static final String closenessRaw = "closenessRaw";

    @Context public GraphDatabaseService db;

    @Context public Log log;

    @Procedure(name = "pagerank.setup", mode = Mode.WRITE)
    @Description("Write information to graph necessary for use of extensions")
    public void setupProcedures() {
        log.info("Writing pagerank and closeness scores to graph");
        db.execute(writeClosenessQuery()); // needed for metric comparison
        db.execute("CALL pagerank.write()");
        db.execute("CALL pagerank.normalize('ACTOR', 'INDIVIDUAL')");
        db.execute("CALL pagerank.normalize('ACTOR', 'GROUP')");
        db.execute("CALL pagerank.normalize('EVENT', 'MEETING')");
        db.execute("CALL pagerank.normalize('EVENT', 'VOTE')");
        db.execute("CALL pagerank.normalize('EVENT', 'TODO')");
    }

    @Procedure(name = "pagerank.write", mode = Mode.WRITE)
    @Description("Write raw pagerank for all entities")
    public void writePagerank() {
        log.info("Writing raw pagerank");
        db.execute("CALL algo.pageRank(" +
                " 'MATCH (n) WHERE EXISTS( (n)-[:PARTICIPATES]-() ) RETURN id(n) as id'," +
                " 'MATCH (n1)-[:PARTICIPATES]->(n2) RETURN id(n1) as source, id(n2) as target UNION" +
                "  MATCH (n1)-[:PARTICIPATES]->(n2) RETURN id(n2) as source, id(n1) as target'," +
                " {graph:'cypher', iterations:100, dampingFactor:0.85, write: true, writeProperty:'" + pagerankRaw + "'}" +
                ")");
    }

    @Procedure(name = "pagerank.normalize", mode = Mode.WRITE)
    @Description("Write normalized pagerank for specified entities")
    public void normalizeScores(@Name(value = "entityType") String entityType, @Name(value = "subType") String subType) {
        log.info("Writing normalized pagerank, type:" + entityType + ", sub-type:" + subType);
        if (!typesAreValid(entityType, subType)) return;
        db.execute(getTypeFilter(entityType, subType, pagerankRaw) +
                " WITH n AS entity, n." + pagerankRaw + " AS " + pagerankRaw +
                " WITH collect({entity:entity, pageRank:" + pagerankRaw + "}) AS entitiesInfo," +
                " avg(" + pagerankRaw + ") AS average," +
                " stDevP(" + pagerankRaw + ") AS stddev" +
                " UNWIND entitiesInfo as entityInfo" +
                " SET entityInfo.entity." + pagerankNorm + "=(entityInfo.pageRank-average)/stddev");
    }

    @UserFunction(name = "pagerank.stats")
    @Description("Return pagerank statistics")
    public Map<String, Object> getStats(@Name(value = "entityType", defaultValue = "") String entityType,
                                        @Name(value = "subType", defaultValue = "") String subType,
                                        @Name(value = "firstRank", defaultValue = "0") long firstRank,
                                        @Name(value = "lastRank", defaultValue = "0") long lastRank,
                                        @Name(value = "normalized", defaultValue = "false") boolean normalized) {
        log.info("Getting pagerank statistics, type:" + entityType + ", sub-type:" + subType);
        String pagerank = normalized ? pagerankNorm : pagerankRaw;
        if (!typesAreValid(entityType, subType) || !rangeIsValid(firstRank, lastRank)) return null;
        Result stats = db.execute(filterQuery(entityType, subType, firstRank, lastRank, pagerank) +
                " WITH min(pagerank) AS minimum," +
                " max(pagerank) AS maximum," +
                " avg(pagerank) AS average," +
                " percentileDisc(pagerank, 0.5) AS median," +
                " stDevP(pagerank) AS stddev" +
                " RETURN minimum, maximum, maximum - minimum AS range, average, median, stddev");
        return stats.hasNext() ? stats.next() : null;
    }

    @UserFunction(name = "pagerank.scores")
    @Description("Return entities in rank range")
    public List<Object> getScores(@Name(value = "entityType", defaultValue = "") String entityType,
                                  @Name(value = "subType", defaultValue = "") String subType,
                                  @Name(value = "firstRank", defaultValue = "0") long firstRank,
                                  @Name(value = "lastRank", defaultValue = "0") long lastRank,
                                  @Name(value = "normalized", defaultValue = "false") boolean normalized) {
        log.info("Getting pagerank scores, type:" + entityType + ", sub-type:" + subType);
        String pagerank = normalized ? pagerankNorm : pagerankRaw;
        if (!typesAreValid(entityType, subType) || !rangeIsValid(firstRank, lastRank)) return null;
        Result scores = db.execute(filterQuery(entityType, subType, firstRank, lastRank, pagerank) + " RETURN pagerank");
        return scores.hasNext() ? resultToList(scores, "pagerank") : null;
    }

    @UserFunction(name = "pagerank.tiers")
    @Description("Return counts of users in three pagerank tiers")
    public Map<Object, Object> getTiers() {
        log.info("Getting tier counts");
        Result tierCounts = db.execute("" +
                " MATCH (n:Actor) WHERE n.actorType='INDIVIDUAL' AND n.pagerankNorm > 10.0 RETURN 'TIER1' AS tier, COUNT(n) AS count" +
                " UNION MATCH (n:Actor) WHERE n.actorType='INDIVIDUAL' AND n.pagerankNorm > 3.0 AND n.pagerankNorm < 10.0 RETURN 'TIER2' AS tier, COUNT(n) AS count" +
                " UNION MATCH (n:Actor) WHERE n.actorType='INDIVIDUAL' AND n.pagerankNorm < 3.0 RETURN 'TIER3' AS tier, COUNT(n) AS count");
        return tierCounts.hasNext() ? resultToMap(tierCounts, "tier", "count") : null;
    }

    @UserFunction(name = "pagerank.meanEntities")
    @Description("Calculate mean entities reached at depth 1, 2, or 3")
    public Object getMeanEntitiesAtDepth(@Name(value = "entityType") String entityType, @Name(value = "subType") String subType,
                                         @Name(value = "firstRank") long firstRank, @Name(value = "lastRank") long lastRank,
                                         @Name(value = "depth") long depth) {
        log.info("Getting mean entities, depth: " + Long.toString(depth));
        if (!typesAreValid(entityType, subType) || !rangeIsValid(firstRank, lastRank) || !depthIsValid(depth)) return null;
        return calculateMeanEntities(pagerankRaw, entityType, subType, firstRank, lastRank, depth);
    }

    @UserFunction(name = "pagerank.meanRelationships")
    @Description("Calculate mean relationships at depth 1, 2, or 3")
    public Object getMeanRelationshipsAtDepth(@Name(value = "entityType") String entityType, @Name(value = "subType") String subType,
                                              @Name(value = "firstRank") long firstRank, @Name(value = "lastRank") long lastRank,
                                              @Name(value = "depth") long depth) {
        log.info("Getting mean relationships, depth: " + Long.toString(depth));
        if (!typesAreValid(entityType, subType) || !rangeIsValid(firstRank, lastRank) || !depthIsValid(depth)) return null;
        return calculateMeanRelationships(pagerankRaw, entityType, subType, firstRank, lastRank, depth);
    }

    @UserFunction(name = "pagerank.compareMetrics")
    @Description("Returns comparison of top 100 users of pagerank and closeness metrics")
    public Map<String, List<Object>> compareMetricsAtDepth(@Name(value = "entityType") String entityType, @Name(value = "subType") String subType,
                                                           @Name(value = "firstRank") long firstRank, @Name(value = "lastRank") long lastRank,
                                                           @Name(value = "entity", defaultValue = "true") boolean entity,
                                                           @Name(value = "depth", defaultValue = "1") long depth) {
        log.info("Comparing metrics, depth: " + Long.toString(depth));
        if (!rangeIsValid(firstRank, lastRank) || !depthIsValid(depth)) return null;
        Map<String, List<Object>> connectionCounts = new HashMap<>();
        connectionCounts.put("pagerank", entity ? getMeanEntities(pagerankRaw, entityType, subType, firstRank, lastRank, depth) :
                getMeanRelationships(pagerankRaw, entityType, subType, firstRank, lastRank, depth));
        connectionCounts.put("closeness", entity ? getMeanEntities(closenessRaw, entityType, subType, firstRank, lastRank, depth) :
                getMeanRelationships(closenessRaw, entityType, subType, firstRank, lastRank, depth));
        return connectionCounts;
    }

    private List<Object> getMeanEntities(String metric, String entityType, String subType, long firstRank, long lastRank, long depth) {
        int startIndex = firstRank < 10 ? 1 : (int) firstRank / 10;
        int endIndex = lastRank < 10 ? 1 : (int) lastRank / 10;
        return IntStream.range(startIndex, endIndex).mapToObj(index -> calculateMeanEntities(metric, entityType, subType,
                (index - 1) * 10, index * 10, depth)).collect(Collectors.toList());
    }

    private List<Object> getMeanRelationships(String metric, String entityType, String subType, long firstRank, long lastRank, long depth) {
        int startIndex = firstRank < 10 ? 1 : (int) firstRank / 10;
        int endIndex = lastRank < 10 ? 1 : (int) lastRank / 10;
        return IntStream.range(startIndex, endIndex).mapToObj(index -> calculateMeanRelationships(metric, entityType, subType,
                (index - 1) * 10, index * 10, depth)).collect(Collectors.toList());
    }

    private Object calculateMeanEntities(String metric, String entityType, String subType, long firstRank, long lastRank, long depth) {
        Result results = db.execute(filterQuery(entityType, subType, firstRank, lastRank, metric) + depthQuery(depth, true));
        return resultToSingleValue(results);
    }

    private Object calculateMeanRelationships(String metric, String entityType, String subType, long firstRank, long lastRank, long depth) {
        Result results = db.execute(filterQuery(entityType, subType, firstRank, lastRank, metric) + depthQuery(depth, false));
        return resultToSingleValue(results);
    }

    private String filterQuery(String entityType, String subType, long firstRank, long lastRank, String pagerank) {
        String typeFilter = getTypeFilter(entityType, subType, pagerank);
        return typeFilter +
                " WITH n AS entity, n." + pagerank + " AS pagerank" +
                " ORDER BY pagerank DESC" +
                " SKIP " + Long.toString(firstRank) +
                " LIMIT " + Long.toString(getLimit(firstRank, lastRank, typeFilter));
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

    private String writeClosenessQuery() {
        return  " CALL algo.closeness.harmonic(" +
                " 'MATCH (n) WHERE exists( (n)-[:PARTICIPATES]-() ) RETURN id(n) as id'," +
                " 'MATCH (n1)-[:PARTICIPATES]->(n2) RETURN id(n1) as source, id(n2) as target UNION" +
                "  MATCH (n1)-[:PARTICIPATES]->(n2) RETURN id(n2) as source, id(n1) as target'," +
                " {graph:'cypher', write: true, writeProperty:'" + closenessRaw + "'}" +
                ")";
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

    private String getDepthMatchingQuery(long depth) {
        switch ((int) depth) {
            case 1: return "MATCH (graphEntity)-[p1:PARTICIPATES]-(e1)";
            case 2: return "MATCH (graphEntity)-[p1:PARTICIPATES]-(e1)-[p2:PARTICIPATES]-(e2)";
            case 3: return "MATCH (graphEntity)-[p1:PARTICIPATES]-(e1)-[p2:PARTICIPATES]-(e2)-[p3:PARTICIPATES]-(e3)";
            default: log.error("Error! Invalid depth, should have been caught by upstream validation"); return null;
        }
    }

    private boolean rangeIsValid(Long firstRank, Long lastRank) {
        return lastRank == 0 || ExtensionUtils.rangeIsValid(firstRank, lastRank);
    }

}