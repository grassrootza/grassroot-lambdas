package extensions;

import org.neo4j.graphdb.Result;

import java.util.List;
import java.util.Map;
import java.util.AbstractMap.SimpleEntry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ExtensionUtils {

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

    public static boolean typesAreValid(String entityType, String subType) {
        return (entityType != null && subType != null) && !(entityType.isEmpty() && !subType.isEmpty()) &&
                entityTypeIsValid(entityType) && subTypeIsValid(entityType, subType);
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

    public static boolean rangeIsValid(Long firstRank, Long lastRank) {
        return lastRank > firstRank;
    }

    public static boolean depthIsValid(Long depth) {
        return  depth == null || depth == 1 || depth == 2 || depth == 3;
    }

    public static boolean isActor(String entityType) {
        return "ACTOR".equals(entityType.toUpperCase());
    }

    public static boolean isEvent(String entityType) {
        return "EVENT".equals(entityType.toUpperCase());
    }

    public static boolean isRelationship(String toCompare) {
        return "RELATIONSHIP".equals(toCompare);
    }

    public static boolean isEntity(String toCompare) {
        return "ENTITY".equals(toCompare);
    }

}