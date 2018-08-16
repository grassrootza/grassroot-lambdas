package extensions;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Result;
import org.neo4j.logging.Log;
import org.neo4j.procedure.*;

import java.util.List;
import java.util.Map;

import static extensions.ExtensionUtils.*;

public class Metrics {

    @Context public GraphDatabaseService db;

    @Context public Log log;

    @Procedure(name = "metric.normalize", mode = Mode.WRITE)
    @Description("Write normalized metric scores for specified entities")
    public void normalizeScores(@Name(value = "metricType") String metricType,
                                @Name(value = "entityType") String entityType, @Name(value = "subType") String subType) {
        log.info("Writing normalized metric scores");
        if (!metricIsValid(metricType) || !typesAreValid(entityType, subType)) return;
        String metricRaw = getMetricPropertyName(metricType, false);
        String metricNorm = getMetricPropertyName(metricType, true);
        db.execute(typeQuery(entityType, subType, metricRaw) +
                " WITH n AS entity, n." + metricRaw + " AS " + metricRaw +
                " WITH collect({entity:entity, pageRank:" + metricRaw + "}) AS entitiesInfo," +
                " avg(" + metricRaw + ") AS average," +
                " stDevP(" + metricRaw + ") AS stddev" +
                " UNWIND entitiesInfo as entityInfo" +
                " SET entityInfo.entity." + metricNorm + "=(entityInfo.pageRank-average)/stddev");
    }

    @UserFunction(name = "metric.stats")
    @Description("Return statistics for metric specified")
    public Map<String, Object> getStats(@Name(value = "metricType") String metricType,
                                        @Name(value = "entityType", defaultValue = "") String entityType,
                                        @Name(value = "subType", defaultValue = "") String subType,
                                        @Name(value = "firstRank", defaultValue = "0") long firstRank,
                                        @Name(value = "lastRank", defaultValue = "0") long lastRank,
                                        @Name(value = "normalized", defaultValue = "false") boolean normalized) {
        log.info("Getting metric statistics");
        if (!paramsAreValid(metricType, entityType, subType, firstRank, lastRank)) return null;
        String metric = getMetricPropertyName(metricType, normalized);
        String typeFilter = typeQuery(entityType, subType, metric);
        Result stats = db.execute(typeFilter + rangeQuery(entityType, subType, metric, firstRank, lastRank, db) +
                " WITH min(metric) AS minimum," +
                " max(metric) AS maximum," +
                " avg(metric) AS average," +
                " percentileDisc(metric, 0.5) AS median," +
                " stDevP(metric) AS stddev" +
                " RETURN minimum, maximum, maximum - minimum AS range, average, median, stddev");
        return stats.hasNext() ? stats.next() : null;
    }

    @UserFunction(name = "metric.scores")
    @Description("Return entities in rank range for metric specified")
    public List<Object> getScores(@Name(value = "metricType") String metricType,
                                  @Name(value = "entityType", defaultValue = "") String entityType,
                                  @Name(value = "subType", defaultValue = "") String subType,
                                  @Name(value = "firstRank", defaultValue = "0") long firstRank,
                                  @Name(value = "lastRank", defaultValue = "0") long lastRank,
                                  @Name(value = "normalized", defaultValue = "false") boolean normalized) {
        log.info("Getting metric scores");
        if (!paramsAreValid(metricType, entityType, subType, firstRank, lastRank)) return null;
        String metric = getMetricPropertyName(metricType, normalized);
        String typeFilter = typeQuery(entityType, subType, metric);
        Result scores = db.execute(typeFilter + rangeQuery(entityType, subType, metric, firstRank, lastRank, db) + " RETURN metric");
        return scores.hasNext() ? resultToList(scores, "metric") : null;
    }

    private boolean paramsAreValid(String metric, String entityType, String subType, long firstRank, long lastRank) {
        return metricIsValid(metric) && typesAreValid(entityType, subType) && rangeIsValid(firstRank, lastRank);
    }

    private boolean rangeIsValid(Long firstRank, Long lastRank) {
        return lastRank == 0 || ExtensionUtils.rangeIsValid(firstRank, lastRank);
    }

}