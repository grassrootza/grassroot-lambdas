package extensions;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Result;
import org.neo4j.logging.Log;
import org.neo4j.procedure.*;

import java.util.List;
import java.util.Map;

import static extensions.ExtensionUtils.*;

public class Pagerank {

    @Context public GraphDatabaseService db;

    @Context public Log log;

    @Procedure(name = "pagerank.setup", mode = Mode.WRITE)
    @Description("Write information to graph necessary for use of extensions")
    public void setupProcedures() {
        log.info("Writing pagerank and closeness scores");
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
        log.info("Writing normalized pagerank");
        if (!typesAreValid(entityType, subType)) return;
        db.execute(typeQuery(entityType, subType, pagerankRaw) +
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
        log.info("Getting pagerank statistics");
        if (!typesAreValid(entityType, subType) || !rangeIsValid(firstRank, lastRank)) return null;
        String pagerank = normalized ? pagerankNorm : pagerankRaw;
        String typeFilter = typeQuery(entityType, subType, pagerank);
        Result stats = db.execute(rangeQuery(typeFilter, pagerank, firstRank, getLimit(firstRank, lastRank, typeFilter)) +
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
        log.info("Getting pagerank scores");
        if (!typesAreValid(entityType, subType) || !rangeIsValid(firstRank, lastRank)) return null;
        String pagerank = normalized ? pagerankNorm : pagerankRaw;
        String typeFilter = typeQuery(entityType, subType, pagerank);
        Result scores = db.execute(rangeQuery(typeFilter, pagerank, firstRank,
                getLimit(firstRank, lastRank, typeFilter)) + " RETURN pagerank");
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

    private long getLimit(long firstRank, long lastRank, String typeFilter) {
        if (lastRank == 0) lastRank = (long) resultToSingleValue(db.execute(typeFilter + " RETURN COUNT(n)"));
        return lastRank - firstRank;
    }

    private String writeClosenessQuery() {
        return  " CALL algo.closeness.harmonic(" +
                " 'MATCH (n) WHERE exists( (n)-[:PARTICIPATES]-() ) RETURN id(n) as id'," +
                " 'MATCH (n1)-[:PARTICIPATES]->(n2) RETURN id(n1) as source, id(n2) as target UNION" +
                "  MATCH (n1)-[:PARTICIPATES]->(n2) RETURN id(n2) as source, id(n1) as target'," +
                " {graph:'cypher', write: true, writeProperty:'" + closenessRaw + "'}" +
                ")";
    }

    private boolean rangeIsValid(Long firstRank, Long lastRank) {
        return lastRank == 0 || ExtensionUtils.rangeIsValid(firstRank, lastRank);
    }

}