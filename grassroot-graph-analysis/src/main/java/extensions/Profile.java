package extensions;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.logging.Log;
import org.neo4j.procedure.*;
import org.neo4j.graphdb.Result;

import java.util.List;
import java.util.Map;

import static extensions.ExtensionUtils.*;

public class Profile {

    @Context public GraphDatabaseService db;

    @Context public Log log;

    @UserFunction(name = "profile.counts")
    @Description("Returns counts of graph entities")
    public Map<Object, Object> getEntityAndRelationshipCounts() {
        log.info("Obtaining counts of all entities and relationships");
        return combineMaps(getEntityCounts(), getRelationshipCounts());
    }

    @UserFunction(name = "profile.groupMemberships")
    @Description("Returns group membership counts in rank range")
    public List<Object> getGroupsByMembership(@Name(value = "firstRank", defaultValue = "0") long firstRank,
                                              @Name(value = "lastRank", defaultValue = "100") long lastRank) {
        log.info("Obtaining membership counts of groups in range {} - {}", firstRank, lastRank);
        if (!rangeIsValid(firstRank, lastRank)) return null;
        Result membershipCounts = db.execute("" +
                " MATCH (i:Actor)-[:PARTICIPATES]->(g:Actor)" +
                " WHERE i.actorType='INDIVIDUAL' AND g.actorType='GROUP'" +
                " WITH g, COUNT(i) as count_membership" +
                " RETURN count_membership ORDER BY count_membership DESC" +
                " SKIP " + Long.toString(firstRank) +
                " LIMIT " + Long.toString(lastRank - firstRank));
        return resultToList(membershipCounts, "count_membership");
    }

    @UserFunction(name = "profile.userParticipations")
    @Description("Returns user participation counts in rank range")
    public List<Object> getUsersByParticipation(@Name(value = "firstRank", defaultValue = "0") long firstRank,
                                                @Name(value = "lastRank", defaultValue = "100") long lastRank) {
        log.info("Obtaining participation counts of users in range {} - {}", firstRank, lastRank);
        if (!rangeIsValid(firstRank, lastRank)) return null;
        Result participationCounts = db.execute("" +
                " MATCH (i:Actor)-[:PARTICIPATES]->(entity)" +
                " WHERE i.actorType='INDIVIDUAL'" +
                " WITH i, COUNT(entity) as count_participation" +
                " RETURN  count_participation ORDER BY count_participation DESC " +
                " SKIP " + Long.toString(firstRank) +
                " LIMIT " + Long.toString(lastRank - firstRank));
        return resultToList(participationCounts, "count_participation");
    }

    private Map<Object, Object> getEntityCounts() {
        Result entityCounts = db.execute("" +
                " MATCH (n) WHERE n.actorType IS NOT NULL OR n.eventType IS NOT NULL RETURN 'TOTAL-ENTITIES' AS type, COUNT(*) AS count" +
                " UNION MATCH (n:Actor) WHERE n.actorType IS NOT NULL RETURN 'ACTOR' AS type, COUNT(*) AS count" +
                " UNION MATCH (n:Event) WHERE n.eventType IS NOT NULL RETURN 'EVENT' AS type, COUNT(*) AS count" +
                " UNION MATCH (n:Actor) WHERE n.actorType IS NOT NULL RETURN n.actorType AS type, COUNT(*) AS count" +
                " UNION MATCH (n:Event) WHERE n.eventType IS NOT NULL RETURN n.eventType AS type, COUNT(*) AS count");
        return resultToMap(entityCounts, "type", "count");
    }

    private Map<Object, Object> getRelationshipCounts() {
        Result relationshipCounts = db.execute("" +
                " MATCH ()-[r]-() RETURN 'TOTAL-RELATIONSHIPS' AS type, COUNT(DISTINCT r) AS count" +
                " UNION MATCH ()-[p:PARTICIPATES]-() RETURN 'PARTICIPATES' AS type, COUNT(DISTINCT p) AS count" +
                " UNION MATCH ()-[g:GENERATOR]-() RETURN 'GENERATOR' AS type, COUNT(DISTINCT g) AS count" +
                " UNION MATCH ()-[p:PARTICIPATES]->(:Actor) RETURN 'PARTICIPATIONS-IN-ACTORS' AS type, COUNT(p) AS count" +
                " UNION MATCH ()-[p:PARTICIPATES]->(:Event) RETURN 'PARTICIPATIONS-IN-EVENTS' AS type, COUNT(p) AS count" +
                " UNION MATCH ()-[g:GENERATOR]->(:Actor) RETURN 'ACTORS-GENERATED' AS type, COUNT(g) AS count" +
                " UNION MATCH ()-[g:GENERATOR]->(:Event) RETURN 'EVENTS-GENERATED' AS type, COUNT(g) AS count");
        return resultToMap(relationshipCounts, "type", "count");
    }

}