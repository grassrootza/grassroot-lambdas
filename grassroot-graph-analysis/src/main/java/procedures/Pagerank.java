package procedures;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.logging.Log;
import org.neo4j.procedure.*;
import org.neo4j.graphdb.Result;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Pagerank {

    @Context public GraphDatabaseService db;

    @Context public Log log;

    public static final String pagerankRaw = "pagerankRaw";
    public static final String pagerankNorm = "pagerankNorm";

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

    @Procedure(name = "pagerank.tiers")
    @Description("Return counts of users in pagerank tiers as determined by pagerank scores")
    public Stream<PropertyRecord> getTiers() {
        log.info("Getting user counts in three pagerank tiers");
        Result entityCounts = db.execute("" +
                " MATCH (n:Actor) WHERE n.actorType='INDIVIDUAL' AND n.pagerankNorm > 10.0 RETURN 'TIER1' AS tier, COUNT(n) AS count" +
                " UNION MATCH (n:Actor) WHERE n.actorType='INDIVIDUAL' AND n.pagerankNorm > 3.0 AND n.pagerankNorm < 10.0 RETURN 'TIER2' AS tier, COUNT(n) AS count" +
                " UNION MATCH (n:Actor) WHERE n.actorType='INDIVIDUAL' AND n.pagerankNorm < 3.0 RETURN 'TIER3' AS tier, COUNT(n) AS count");
        return getPropertyStream(entityCounts, "tier", "count");
    }

    @UserFunction(name = "pagerank.meanEntities")
    @Description("Calculate mean entities reached at depth 1, 2, or 3")
    public Double getMeanEntitiesAtDepth(@Name(value = "entityType") String entityType, @Name(value = "subType") String subType,
                                         @Name(value = "firstRank") long firstRank, @Name(value = "lastRank") long lastRank,
                                         @Name(value = "depth") long depth) {
        log.info("Getting mean entities reached at depth {}", depth);
        if (!paramsAreValid(entityType, subType, firstRank, lastRank, depth)) return null;
        return calculateMeanEntitiesPR(entityType, subType, firstRank, lastRank, depth);
    }

    @UserFunction(name = "pagerank.meanRelationships")
    @Description("Calculate mean relationships at depth 1, 2, or 3")
    public Double getMeanRelationshipsAtDepth(@Name(value = "entityType") String entityType, @Name(value = "subType") String subType,
                                              @Name(value = "firstRank") long firstRank, @Name(value = "lastRank") long lastRank,
                                              @Name(value = "depth") long depth) {
        log.info("Getting mean relationships reached at depth {}", depth);
        if (!paramsAreValid(entityType, subType, firstRank, lastRank, depth)) return null;
        return calculateMeanRelationshipsPR(entityType, subType, firstRank, lastRank, depth);
    }

    private double calculateMeanEntitiesPR(String entityType, String subType, long firstRank, long lastRank, long depth) {
        Result results = db.execute(filterQuery(entityType, subType, firstRank, lastRank, pagerankRaw) + depthQuery(depth, true));
        return (double) results.next().values().iterator().next();
    }

    private double calculateMeanRelationshipsPR(String entityType, String subType, long firstRank, long lastRank, long depth) {
        Result results = db.execute(filterQuery(entityType, subType, firstRank, lastRank, pagerankRaw) + depthQuery(depth, false));
        return (double) results.next().values().iterator().next();
    }

    @Procedure(name = "pagerank.meanEntitiesList")
    public Stream<Profile.CountRecord> getMeanEntitiesPR(@Name(value = "depth") long depth) {
        return IntStream.range(1, 10).mapToObj(index -> new Profile.CountRecord(calculateMeanEntitiesPR("ACTOR",
                "INDIVIDUAL", (index - 1) * 10, index * 10, depth))).collect(Collectors.toList()).stream();
    }

    @Procedure(name = "pagerank.meanRelationshipsList")
    public Stream<Profile.CountRecord> getMeanRelationshipsPR(@Name(value = "depth") long depth) {
        return IntStream.range(1, 10).mapToObj(index -> new Profile.CountRecord(calculateMeanRelationshipsPR("ACTOR",
                "INDIVIDUAL", (index - 1) * 10, index * 10, depth))).collect(Collectors.toList()).stream();
    }

    private double calculateMeanEntitiesCL(String entityType, String subType, long firstRank, long lastRank, long depth) {
        Result results = db.execute(filterQuery(entityType, subType, firstRank, lastRank, "closenessScore") + depthQuery(depth, true));
        return (double) results.next().values().iterator().next();
    }

    private double calculateMeanRelationshipsCL(String entityType, String subType, long firstRank, long lastRank, long depth) {
        Result results = db.execute(filterQuery(entityType, subType, firstRank, lastRank, "closenessScore") + depthQuery(depth, false));
        return (double) results.next().values().iterator().next();
    }

    @Procedure(name = "closeness.meanEntitiesList")
    public Stream<Profile.CountRecord> getMeanEntitiesCL(@Name(value = "depth") long depth) {
        return IntStream.range(1, 10).mapToObj(index -> new Profile.CountRecord(calculateMeanEntitiesCL("ACTOR",
                "INDIVIDUAL", (index - 1) * 10, index * 10, depth))).collect(Collectors.toList()).stream();
    }

    @Procedure(name = "closeness.meanRelationshipsList")
    public Stream<Profile.CountRecord> getMeanRelationshipsCL(@Name(value = "depth") long depth) {
        return IntStream.range(1, 10).mapToObj(index -> new Profile.CountRecord(calculateMeanRelationshipsCL("ACTOR",
                "INDIVIDUAL", (index - 1) * 10, index * 10, depth))).collect(Collectors.toList()).stream();
    }

    @Procedure(name = "profile.compareMetrics")
    @Description("Returns comparison of top 100 users of pagerank and closeness metrics")
    public Stream<ListRecord> compareMetricsAtDepth(@Name(value = "comparate", defaultValue = "ENTITY") String comparate,
                                                             @Name(value = "depth", defaultValue = "1") long depth) {
        log.info("Obtaining comparison of pagerank and closeness metrics");
        if (isRelationship(comparate)) {

        }
        else if (isEntity(comparate)) {

        }
        else log.error("Error! Must compare metrics with regard to entities or relationships"); return null;
    }

    private boolean isRelationship(String comparate) {
        return "RELATIONSHIP".equals(comparate);
    }

    private boolean isEntity(String comparate) {
        return "ENTITY".equals(comparate);
    }

    public static class ListRecord {
        public String property;
        public List<Long> values;
        public ListRecord(String property, List<Long> values) {
            this.property = property;
            this.values = values;
        }
    }


    public static class RecordWrapper {
        public Map<String, Object> results;
        public RecordWrapper(Map<String, Object> results) {
            this.results = results;
        }
    }

    public static class PropertyRecord {
        public String property;
        public long value;
        public PropertyRecord(String property, long value) {
            this.property = property;
            this.value = value;
        }
    }

    private Stream<RecordWrapper> getResultStream(Result results) {
        return results.hasNext() ? results.stream().map(RecordWrapper::new) : null;
    }

    private Stream<PropertyRecord> getPropertyStream(Result results, String propKey, String valueKey) {
        return results.hasNext() ? results.stream().map(result ->
                new PropertyRecord((String) result.get(propKey), (long) result.get(valueKey))) : null;
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