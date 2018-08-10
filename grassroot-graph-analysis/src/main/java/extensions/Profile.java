package extensions;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.logging.Log;
import org.neo4j.procedure.*;
import org.neo4j.graphdb.Result;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Profile {

    @Context public GraphDatabaseService db;

    @Context public Log log;

    @UserFunction(name = "profile.counts")
    @Description("Returns counts of graph entities")
    public Map<String, Long> getEntityAndRelationshipCounts() {
        log.info("Obtaining counts of all entities and relationships");
        Map<String, Long> entityCounts = getEntityCounts();
        Map<String, Long> relationshipCounts = getRelationshipCounts();
        if (entityCounts != null && relationshipCounts != null) {
            entityCounts.putAll(relationshipCounts);
            return entityCounts;
        }
        log.error("Error! Entity or relationship counts could not be gathered from graph");
        return null;
    }

    @UserFunction(name = "profile.groupMemberships")
    @Description("Returns group membership counts in rank range")
    public List<Long> getGroupsByMembership(@Name(value = "firstRank", defaultValue = "0") long firstRank,
                                            @Name(value = "lastRank", defaultValue = "100") long lastRank) {
        log.info("Obtaining membership counts of groups in range {} - {}", firstRank, lastRank);
        if (!boundsAreValid(firstRank, lastRank)) return null;
        List<Long> countList = new ArrayList<>();
        Result membershipCounts = db.execute("" +
                " MATCH (i:Actor)-[:PARTICIPATES]->(g:Actor)" +
                " WHERE i.actorType='INDIVIDUAL' AND g.actorType='GROUP'" +
                " WITH g, COUNT(i) as count_membership" +
                " RETURN count_membership ORDER BY count_membership DESC" +
                " SKIP " + Long.toString(firstRank) +
                " LIMIT " + Long.toString(lastRank - firstRank));
        membershipCounts.forEachRemaining(count -> countList.add((long) count.get("count_membership")));
        return countList;
    }

    @UserFunction(name = "profile.userParticipations")
    @Description("Returns user participation counts in rank range")
    public List<Long> getUsersByParticipation(@Name(value = "firstRank", defaultValue = "0") long firstRank,
                                                       @Name(value = "lastRank", defaultValue = "100") long lastRank) {
        log.info("Obtaining participation counts of users in range {} - {}", firstRank, lastRank);
        if (!boundsAreValid(firstRank, lastRank)) return null;
        List<Long> countList = new ArrayList<>();
        Result participationCounts = db.execute("" +
                " MATCH (i:Actor)-[:PARTICIPATES]->(entity)" +
                " WHERE i.actorType='INDIVIDUAL'" +
                " WITH i, COUNT(entity) as count_participation" +
                " RETURN  count_participation ORDER BY count_participation DESC " +
                " SKIP " + Long.toString(firstRank) +
                " LIMIT " + Long.toString(lastRank - firstRank));
        participationCounts.forEachRemaining(count -> countList.add((long) count.get("count_participation")));
        return countList;
    }

    private Map<String, Long> getEntityCounts() {
        Map<String, Long> counts = new HashMap<>();
        Result entityCounts = db.execute("" +
                " MATCH (n) WHERE n.actorType IS NOT NULL OR n.eventType IS NOT NULL RETURN 'TOTAL-ENTITIES' AS type, COUNT(*) AS count" +
                " UNION MATCH (n:Actor) WHERE n.actorType IS NOT NULL RETURN 'ACTOR' AS type, COUNT(*) AS count" +
                " UNION MATCH (n:Event) WHERE n.eventType IS NOT NULL RETURN 'EVENT' AS type, COUNT(*) AS count" +
                " UNION MATCH (n:Actor) WHERE n.actorType IS NOT NULL RETURN n.actorType AS type, COUNT(*) AS count" +
                " UNION MATCH (n:Event) WHERE n.eventType IS NOT NULL RETURN n.eventType AS type, COUNT(*) AS count");
        entityCounts.forEachRemaining(count -> counts.put((String) count.get("type"), (long) count.get("count")));
        return counts;
    }

    private Map<String, Long> getRelationshipCounts() {
        Map<String, Long> counts = new HashMap<>();
        Result relationshipCounts = db.execute("" +
                " MATCH ()-[r]-() RETURN 'TOTAL-RELATIONSHIPS' AS type, COUNT(DISTINCT r) AS count" +
                " UNION MATCH ()-[p:PARTICIPATES]-() RETURN 'PARTICIPATES' AS type, COUNT(DISTINCT p) AS count" +
                " UNION MATCH ()-[g:GENERATOR]-() RETURN 'GENERATOR' AS type, COUNT(DISTINCT g) AS count" +
                " UNION MATCH ()-[p:PARTICIPATES]->(:Actor) RETURN 'PARTICIPATIONS-IN-ACTORS' AS type, COUNT(p) AS count" +
                " UNION MATCH ()-[p:PARTICIPATES]->(:Event) RETURN 'PARTICIPATIONS-IN-EVENTS' AS type, COUNT(p) AS count" +
                " UNION MATCH ()-[g:GENERATOR]->(:Actor) RETURN 'ACTORS-GENERATED' AS type, COUNT(g) AS count" +
                " UNION MATCH ()-[g:GENERATOR]->(:Event) RETURN 'EVENTS-GENERATED' AS type, COUNT(g) AS count");
        relationshipCounts.forEachRemaining(count -> counts.put((String) count.get("type"), (long) count.get("count")));
        return counts;
    }

    private boolean boundsAreValid(Long firstRank, Long lastRank) {
        if (lastRank <= firstRank) {
            log.error("Error! Last rank must be lower than first rank");
            return false;
        }
        return true;
    }

}