package extensions;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Result;
import org.neo4j.logging.Log;
import org.neo4j.procedure.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static extensions.ExtensionUtils.*;

public class Connections {

    @Context public GraphDatabaseService db;

    @Context public Log log;

    @UserFunction(name = "connections.mean")
    @Description("Calculate mean connections reached at depth 1, 2, or 3")
    public Object getMeanConnections(@Name(value = "metricType") String metricType,
                                     @Name(value = "entityType") String entityType, @Name(value = "subType") String subType,
                                     @Name(value = "firstRank") long firstRank, @Name(value = "lastRank") long lastRank,
                                     @Name(value = "depth") long depth, @Name(value = "countEntities") boolean countEntities) {
        log.info("Getting mean connections, metric: " + metricType);
        if (!paramsAreValid(metricType, entityType, subType, firstRank, lastRank, depth)) return null;
        String metric = getMetricPropertyName(metricType, false);
        return calculateMeanConnections(entityType, subType, firstRank, lastRank, depth, countEntities, metric);
    }

    @UserFunction(name = "connections.meanList")
    @Description("Calculate range of mean connections reached at depth 1, 2, or 3")
    public List<Object> getMeanConnectionsList(@Name(value = "metricType") String metricType,
                                               @Name(value = "entityType") String entityType, @Name(value = "subType") String subType,
                                               @Name(value = "firstRank") long firstRank, @Name(value = "lastRank") long lastRank,
                                               @Name(value = "depth") long depth, @Name(value = "countEntities") boolean countEntities) {
        log.info("Getting mean connections list, metric: " + metricType);
        if (!paramsAreValid(metricType, entityType, subType, firstRank, lastRank, depth)) return null;
        String metric = getMetricPropertyName(metricType, false);
        return calculateMeanConnectionsList(entityType, subType, firstRank, lastRank, depth, countEntities, metric);
    }

    @UserFunction(name = "connections.compareMetrics")
    @Description("Returns comparison of top 100 users of pagerank and closeness metrics")
    public Map<String, List<Object>> compareMetrics(@Name(value = "metricType") String metric1Type, @Name(value = "metricType") String metric2Type,
                                                    @Name(value = "entityType") String entityType, @Name(value = "subType") String subType,
                                                    @Name(value = "firstRank") long firstRank, @Name(value = "lastRank") long lastRank,
                                                    @Name(value = "depth") long depth, @Name(value = "countEntities") boolean countEntities) {
        log.info("Comparing " + metric1Type + " and " + metric2Type + " connections");
        if (!metricsAreValid(metric1Type, metric2Type) || !paramsAreValid(null, entityType, subType, firstRank, lastRank, depth)) return null;
        String metric1 = getMetricPropertyName(metric1Type, false);
        String metric2 = getMetricPropertyName(metric2Type, false);

        Map<String, List<Object>> connectionCounts = new HashMap<>();
        connectionCounts.put(metric1Type.toUpperCase(), calculateMeanConnectionsList(entityType, subType, firstRank, lastRank, depth, countEntities, metric1));
        connectionCounts.put(metric2Type.toUpperCase(), calculateMeanConnectionsList(entityType, subType, firstRank, lastRank, depth, countEntities, metric2));
        return connectionCounts;
    }

    private Object calculateMeanConnections(String entityType, String subType, long firstRank, long lastRank,
                                            long depth, boolean countEntities, String metric) {
        String typeFilter = typeQuery(entityType, subType, metric);
        Result results = db.execute(typeFilter + rangeQuery(entityType, subType, metric, firstRank, lastRank, db) + depthQuery(depth, countEntities));
        return resultToSingleValue(results);
    }

    private List<Object> calculateMeanConnectionsList(String entityType, String subType, long firstRank, long lastRank,
                                                      long depth, boolean countEntities, String metric) {
        int startIndex = firstRank < 10 ? 1 : (int) firstRank / 10;
        int endIndex = lastRank < 10 ? 1 : (int) lastRank / 10;
        return IntStream.rangeClosed(startIndex, endIndex).mapToObj(index -> calculateMeanConnections(entityType, subType,
                (index - 1) * 10, index * 10, depth, countEntities, metric)).collect(Collectors.toList());
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

    private boolean paramsAreValid(String metric, String entityType, String subType, long firstRank, long lastRank, long depth) {
        return (metric == null || metricIsValid(metric)) && typesAreValid(entityType, subType) &&
                rangeIsValid(firstRank, lastRank) && depthIsValid(depth);
    }

    private boolean metricsAreValid(String metric1, String metric2) {
        return metricIsValid(metric1) && metricIsValid(metric2) && !metric1.toUpperCase().equals(metric2.toUpperCase());
    }

}