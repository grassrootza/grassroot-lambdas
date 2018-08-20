package extensions;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Result;
import org.neo4j.logging.Log;
import org.neo4j.procedure.*;

import java.util.Map;

import static extensions.ExtensionUtils.*;

public class Closeness {

    @Context public GraphDatabaseService db;

    @Context public Log log;

    @Procedure(name = "closeness.setup", mode = Mode.WRITE)
    @Description("Write information needed for closeness extensions")
    public void setupCloseness() {
        log.info("Setting up closeness");
        db.execute("CALL closeness.write()");
        db.execute("CALL metric.normalize('CLOSENESS', 'ACTOR', 'INDIVIDUAL')");
        db.execute("CALL metric.normalize('CLOSENESS', 'ACTOR', 'GROUP')");
        db.execute("CALL metric.normalize('CLOSENESS', 'EVENT', 'MEETING')");
        db.execute("CALL metric.normalize('CLOSENESS', 'EVENT', 'VOTE')");
        db.execute("CALL metric.normalize('CLOSENESS', 'EVENT', 'TODO')");
    }

    @Procedure(name = "closeness.write", mode = Mode.WRITE)
    @Description("Write raw closeness for all entities")
    public void writeCloseness() {
        log.info("Writing raw closeness");
        db.execute("CALL algo.closeness.harmonic(" +
                " 'MATCH (n) WHERE exists( (n)-[:PARTICIPATES]-() ) RETURN id(n) as id'," +
                " 'MATCH (n1)-[:PARTICIPATES]->(n2) RETURN id(n1) as source, id(n2) as target UNION" +
                "  MATCH (n1)-[:PARTICIPATES]->(n2) RETURN id(n2) as source, id(n1) as target'," +
                " {graph:'cypher', write: true, writeProperty:'" + closenessRaw + "'}" +
                ")");
    }

    @UserFunction(name = "closeness.tiers")
    @Description("Return counts of users in four closeness tiers")
    public Map<Object, Object> getUserTiers() {
        log.info("Getting closeness tier counts");
        Result tierCounts = db.execute("" +
                " MATCH (n:Actor) WHERE n.actorType='INDIVIDUAL' AND n." + closenessNorm + " >= 1.5 RETURN 'TIER1' AS tier, (COUNT(n)*1.0) AS count" +
                " UNION MATCH (n:Actor) WHERE n.actorType='INDIVIDUAL' AND n." + closenessNorm + " > 0.5 AND n.pagerankNorm < 1.5 RETURN 'TIER2' AS tier, (COUNT(n)*1.0) AS count" +
                " UNION MATCH (n:Actor) WHERE n.actorType='INDIVIDUAL' AND n." + closenessNorm + " > -0.4 AND n.pagerankNorm < 0.5 RETURN 'TIER3' AS tier, (COUNT(n)*1.0) AS count" +
                " UNION MATCH (n:Actor) WHERE n.actorType='INDIVIDUAL' AND n." + closenessNorm + " < -0.4 RETURN 'TIER4' AS tier, (COUNT(n)*1.0) AS count");
        return tierCounts.hasNext() ? resultToMap(tierCounts, "tier", "count") : null;
    }

}