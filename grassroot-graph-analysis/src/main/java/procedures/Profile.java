package procedures;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.logging.Log;
import org.neo4j.procedure.*;
import org.neo4j.graphdb.Result;

import java.util.stream.Stream;

public class Profile {

    @Context public GraphDatabaseService db;

    @Context public Log log;

    @Procedure(name = "profile.counts")
    @Description("Returns counts of graph entities")
    public Stream<PropertyRecord> getEntityAndRelationshipCounts() {
        log.info("Obtaining counts of all entities and relationships");
        Stream<PropertyRecord> entityCounts = getEntityCounts("type", "count");
        Stream<PropertyRecord> relationshipCounts = getRelationshipCounts("type", "count");
        if (entityCounts != null && relationshipCounts != null) return Stream.concat(entityCounts, relationshipCounts);
        log.error("Error! Entity or relationship counts could not be gathered from graph"); return null;
    }

    @Procedure(name = "profile.groupMemberships")
    @Description("Returns group membership counts in rank range")
    public Stream<CountRecord> getGroupsByMembership(@Name(value = "firstRank", defaultValue = "0") long firstRank,
                                                     @Name(value = "lastRank", defaultValue = "100") long lastRank) {
        log.info("Obtaining membership counts of groups in range {} - {}", firstRank, lastRank);
        if (!boundsAreValid(firstRank, lastRank)) return null;
        Result membershipCounts = db.execute("" +
                " MATCH (i:Actor)-[:PARTICIPATES]->(g:Actor)" +
                " WHERE i.actorType='INDIVIDUAL' AND g.actorType='GROUP'" +
                " WITH g, COUNT(i) as count_membership" +
                " RETURN count_membership ORDER BY count_membership DESC" +
                " SKIP " + Long.toString(firstRank) +
                " LIMIT " + Long.toString(lastRank - firstRank));
        return getCountStream(membershipCounts, "count_membership");
    }

    @Procedure(name = "profile.userParticipations")
    @Description("Returns user participation counts in rank range")
    public Stream<CountRecord> getUsersByParticipation(@Name(value = "firstRank", defaultValue = "0") long firstRank,
                                                       @Name(value = "lastRank", defaultValue = "100") long lastRank) {
        log.info("Obtaining participation counts of users in range {} - {}", firstRank, lastRank);
        if (!boundsAreValid(firstRank, lastRank)) return null;
        Result participationCounts = db.execute("" +
                " MATCH (i:Actor)-[:PARTICIPATES]->(entity)" +
                " WHERE i.actorType='INDIVIDUAL'" +
                " WITH i, COUNT(entity) as count_participation" +
                " RETURN  count_participation ORDER BY count_participation DESC " +
                " SKIP " + Long.toString(firstRank) +
                " LIMIT " + Long.toString(lastRank - firstRank));
        return getCountStream(participationCounts, "count_participation");
    }

    public static class PropertyRecord {
        public String property;
        public long value;
        public PropertyRecord(String property, long value) {
            this.property = property;
            this.value = value;
        }
    }

    public static class CountRecord {
        public long count;
        public CountRecord(long count) {
            this.count = count;
        }
    }


    private Stream<PropertyRecord> getEntityCounts(String prop, String value) {
        Result entityCounts = db.execute("" +
                " MATCH (n) WHERE n.actorType IS NOT NULL OR n.eventType IS NOT NULL RETURN 'TOTAL-ENTITIES' AS " + prop + ", COUNT(*) AS " + value +
                " UNION MATCH (n:Actor) WHERE n.actorType IS NOT NULL RETURN 'ACTOR' AS " + prop + ", COUNT(*) AS " + value +
                " UNION MATCH (n:Event) WHERE n.eventType IS NOT NULL RETURN 'EVENT' AS " + prop + ", COUNT(*) AS " + value +
                " UNION MATCH (n:Actor) WHERE n.actorType IS NOT NULL RETURN n.actorType AS " + prop + ", COUNT(*) AS " + value +
                " UNION MATCH (n:Event) WHERE n.eventType IS NOT NULL RETURN n.eventType AS " + prop + ", COUNT(*) AS " + value);
        return getPropertyStream(entityCounts, prop, value);
    }

    private Stream<PropertyRecord> getRelationshipCounts(String prop, String value) {
        Result relationshipCounts = db.execute("" +
                " MATCH ()-[r]-() RETURN 'TOTAL-RELATIONSHIPS' AS " + prop + ", COUNT(DISTINCT r) AS " + value +
                " UNION MATCH ()-[p:PARTICIPATES]-() RETURN 'PARTICIPATES' AS " + prop + ", COUNT(DISTINCT p) AS " + value +
                " UNION MATCH ()-[g:GENERATOR]-() RETURN 'GENERATOR' AS " + prop + ", COUNT(DISTINCT g) AS " + value +
                " UNION MATCH ()-[p:PARTICIPATES]->(:Actor) RETURN 'PARTICIPATIONS-IN-ACTORS' AS " + prop + ", COUNT(p) AS " + value +
                " UNION MATCH ()-[p:PARTICIPATES]->(:Event) RETURN 'PARTICIPATIONS-IN-EVENTS' AS " + prop + ", COUNT(p) AS " + value +
                " UNION MATCH ()-[g:GENERATOR]->(:Actor) RETURN 'ACTORS-GENERATED' AS " + prop + ", COUNT(g) AS " + value +
                " UNION MATCH ()-[g:GENERATOR]->(:Event) RETURN 'EVENTS-GENERATED' AS " + prop + ", COUNT(g) AS " + value);
        return getPropertyStream(relationshipCounts, prop, value);
    }

    private Stream<PropertyRecord> getPropertyStream(Result results, String propKey, String valueKey) {
        return results.hasNext() ? results.stream().map(result ->
                new PropertyRecord((String) result.get(propKey), (long) result.get(valueKey))) : null;
    }
    
    private Stream<CountRecord> getCountStream(Result results, String countKey) {
        return results.hasNext() ? results.stream().map(result -> new CountRecord((long) result.get(countKey))) : null;
    }

    private boolean boundsAreValid(Long firstRank, Long lastRank) {
        if (lastRank <= firstRank) {
            log.error("Error! Last rank must be lower than first rank");
            return false;
        }
        return true;
    }

}