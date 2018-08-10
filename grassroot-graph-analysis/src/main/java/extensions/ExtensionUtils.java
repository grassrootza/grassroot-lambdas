package extensions;

public class ExtensionUtils {

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

    public static boolean boundsAreValid(Long firstRank, Long lastRank) {
        return (firstRank == null && lastRank == null) || lastRank == 0 || lastRank > firstRank;
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