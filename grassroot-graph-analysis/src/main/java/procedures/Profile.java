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
    @Description("Obtains counts of graph entities")
    public Stream<CountRecord> getEntityAndRelationshipCounts() {
        log.info("Obtaining counts of all entities and relationships");
        Stream<CountRecord> entityCounts = getEntityCounts("type", "count");
        Stream<CountRecord> relationshipCounts = getRelationshipCounts("type", "count");
        if (entityCounts != null && relationshipCounts != null) return Stream.concat(entityCounts, relationshipCounts);
        log.error("Error! Entity or relationship counts could not be gathered from graph"); return null;
    }

    private Stream<CountRecord> getEntityCounts(String prop, String value) {
        Result entityCounts = db.execute("" +
                " MATCH (n) WHERE n.actorType IS NOT NULL OR n.eventType IS NOT NULL RETURN 'TOTAL-ENTITIES' AS " + prop + ", COUNT(*) AS " + value +
                " UNION MATCH (n:Actor) WHERE n.actorType IS NOT NULL RETURN 'ACTOR' AS " + prop + ", COUNT(*) AS " + value +
                " UNION MATCH (n:Event) WHERE n.eventType IS NOT NULL RETURN 'EVENT' AS " + prop + ", COUNT(*) AS " + value +
                " UNION MATCH (n:Actor) WHERE n.actorType IS NOT NULL RETURN n.actorType AS " + prop + ", COUNT(*) AS " + value +
                " UNION MATCH (n:Event) WHERE n.eventType IS NOT NULL RETURN n.eventType AS " + prop + ", COUNT(*) AS " + value);
        return getCountStream(entityCounts, prop, value);
    }

    private Stream<CountRecord> getRelationshipCounts(String prop, String value) {
        Result relationshipCounts = db.execute("" +
                " MATCH ()-[r]-() RETURN 'TOTAL-RELATIONSHIPS' AS " + prop + ", COUNT(DISTINCT r) AS " + value +
                " UNION MATCH ()-[p:PARTICIPATES]-() RETURN 'PARTICIPATES' AS " + prop + ", COUNT(DISTINCT p) AS " + value +
                " UNION MATCH ()-[g:GENERATOR]-() RETURN 'GENERATOR' AS " + prop + ", COUNT(DISTINCT g) AS " + value +
                " UNION MATCH ()-[p:PARTICIPATES]->(:Actor) RETURN 'PARTICIPATIONS-IN-ACTORS' AS " + prop + ", COUNT(p) AS " + value +
                " UNION MATCH ()-[p:PARTICIPATES]->(:Event) RETURN 'PARTICIPATIONS-IN-EVENTS' AS " + prop + ", COUNT(p) AS " + value +
                " UNION MATCH ()-[g:GENERATOR]->(:Actor) RETURN 'ACTORS-GENERATED' AS " + prop + ", COUNT(g) AS " + value +
                " UNION MATCH ()-[g:GENERATOR]->(:Event) RETURN 'EVENTS-GENERATED' AS " + prop + ", COUNT(g) AS " + value);
        return getCountStream(relationshipCounts, prop, value);
    }

    private Stream<CountRecord> getCountStream(Result results, String propKey, String valueKey) {
        return results.hasNext() ? results.stream().map(result ->
                new CountRecord((String) result.get(propKey), (long) result.get(valueKey))) : null;
    }

    public static class CountRecord {
        public String property;
        public long value;
        public CountRecord(String property, long value) {
            this.property = property;
            this.value = value;
        }
    }

}