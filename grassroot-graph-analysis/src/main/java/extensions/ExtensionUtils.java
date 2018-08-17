package extensions;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Result;

import java.util.List;
import java.util.Map;
import java.util.AbstractMap.SimpleEntry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ExtensionUtils {

    public static final String pagerankRaw = "pagerankRaw";
    public static final String pagerankNorm = "pagerankNorm";
    public static final String closenessRaw = "closenessRaw";
    public static final String closenessNorm = "closenessNorm";

    public static String getMetricPropertyName(String metric, boolean normalized) {
        if (isPagerank(metric)) return normalized ? pagerankNorm : pagerankRaw;
        if (isCloseness(metric)) return normalized ? closenessNorm : closenessRaw;
        return null;
    }

    public static Map<Object, Object> resultToMap(Result result, String keyName, String valueName) {
        return result.stream().map(r -> new SimpleEntry<>
                (r.get(keyName), r.get(valueName))).collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue));
    }

    public static List<Object> resultToList(Result result, String keyName) {
        return result.stream().map(r -> r.get(keyName)).collect(Collectors.toList());
    }

    public static Object resultToSingleValue(Result result) {
        return result.next().values().iterator().next();
    }

    public static Map<Object, Object> combineMaps(Map<Object, Object> map1, Map<Object, Object> map2) {
        return Stream.of(map1, map2).flatMap(m -> m.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public static String simpleTypeQuery(String entityType, String subType) {
        String entityFilter = entityType.isEmpty() ? "" :
                isActor(entityType) ? ":Actor" : ":Event";
        String subTypeFilter = subType.isEmpty() ? "" :
                isActor(entityType) ? "n.actorType='" + subType + "'" : "n.eventType='" + subType + "'";
        return  "MATCH (n" + entityFilter + ") WHERE " + subTypeFilter;
    }

    public static String typeQuery(String entityType, String subType, String metric) {
        String entityFilter = entityType.isEmpty() ? "" :
                isActor(entityType) ? ":Actor" : ":Event";
        String subTypeFilter = subType.isEmpty() ? "" :
                isActor(entityType) ? "n.actorType='" + subType + "' AND " : "n.eventType='" + subType + "' AND ";
        return  "MATCH (n" + entityFilter + ") WHERE " + subTypeFilter + "n." + metric + " IS NOT NULL";
    }

    public static String rangeQuery(String entityType, String subType, String metric, long firstRank, long lastRank, GraphDatabaseService db) {
        if (lastRank == 0) lastRank = getEntityCount(entityType, subType, metric, db);
        return  " WITH n AS entity, n." + metric + " AS metric" +
                " ORDER BY metric DESC" +
                " SKIP " + Long.toString(firstRank) +
                " LIMIT " + Long.toString(lastRank - firstRank);
    }
    
    public static String statsQuery(String keyWord) {
        return  " WITH min(" + keyWord + ") AS minimum," +
                " max(" + keyWord + ") AS maximum," +
                " avg(" + keyWord + ") AS average," +
                " percentileDisc(" + keyWord + ", 0.5) AS median," +
                " stDevP(" + keyWord + ") AS stddev" +
                " RETURN minimum, maximum, maximum - minimum AS range, average, median, stddev";
    }

    public static long getEntityCount(String entityType, String subType, String metric, GraphDatabaseService db) {
        return (long) resultToSingleValue(db.execute(typeQuery(entityType, subType, metric) + " RETURN COUNT(n)"));
    }

    public static long getEntityCount(String entityType, String subType, GraphDatabaseService db) {
        return (long) resultToSingleValue(db.execute(simpleTypeQuery(entityType, subType) + " RETURN COUNT(n)"));
    }

    public static boolean metricIsValid(String metric) {
        return isPagerank(metric) || isCloseness(metric);
    }

    public static boolean typesAreValid(String entityType, String subType) {
        return (entityType != null && subType != null) && !(entityType.isEmpty() && !subType.isEmpty()) &&
                entityTypeIsValid(entityType) && subTypeIsValid(entityType, subType);
    }

    public static boolean rangeIsValid(Long firstRank, Long lastRank) {
        return lastRank > firstRank;
    }

    public static boolean depthIsValid(Long depth) {
        return depth == 1 || depth == 2 || depth == 3;
    }

    public static boolean entityTypeIsValid(String entityType) {
        return entityType.isEmpty() || isActor(entityType) || isEvent(entityType);
    }

    public static boolean subTypeIsValid(String entityType, String subType) {
        subType = subType.toUpperCase();
        return entityType.isEmpty() || isActor(entityType) ?
                ("INDIVIDUAL".equals(subType) || "GROUP".equals(subType) || "MOVEMENT".equals(subType) || subType.isEmpty()) :
                ("MEETING".equals(subType) || "VOTE".equals(subType) || "TODO".equals(subType) || subType.isEmpty());
    }

    public static boolean isPagerank(String metric) {
        return "PAGERANK".equals(metric.toUpperCase());
    }

    public static boolean isCloseness(String metric) {
        return "CLOSENESS".equals(metric.toUpperCase());
    }

    public static boolean isActor(String entityType) {
        return "ACTOR".equals(entityType.toUpperCase());
    }

    public static boolean isEvent(String entityType) {
        return "EVENT".equals(entityType.toUpperCase());
    }

}