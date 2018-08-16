package extensions;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Result;
import org.neo4j.logging.Log;
import org.neo4j.procedure.*;

import java.util.Map;

import static extensions.ExtensionUtils.*;

public class Pagerank {

    @Context public GraphDatabaseService db;

    @Context public Log log;

    @Procedure(name = "pagerank.setup", mode = Mode.WRITE)
    @Description("Write information needed for pagerank extensions")
    public void setupPagerank() {
        log.info("Setting up pagerank");
        db.execute("CALL pagerank.write()");
        db.execute("CALL metric.normalize('PAGERANK', 'ACTOR', 'INDIVIDUAL')");
        db.execute("CALL metric.normalize('PAGERANK', 'ACTOR', 'GROUP')");
        db.execute("CALL metric.normalize('PAGERANK', 'EVENT', 'MEETING')");
        db.execute("CALL metric.normalize('PAGERANK', 'EVENT', 'VOTE')");
        db.execute("CALL metric.normalize('PAGERANK', 'EVENT', 'TODO')");
    }

    @Procedure(name = "pagerank.write", mode = Mode.WRITE)
    @Description("Write raw pagerank for all entities")
    public void writePagerank() {
        log.info("Writing raw pagerank");
        db.execute("CALL algo.pageRank(" +
                " 'MATCH (n) WHERE EXISTS( (n)-[:PARTICIPATES]-() ) RETURN id(n) as id'," +
                " 'MATCH (n1)-[:PARTICIPATES]->(n2) RETURN id(n1) as source, id(n2) as target UNION" +
                "  MATCH (n1)-[:PARTICIPATES]->(n2) RETURN id(n2) as source, id(n1) as target'," +
                " {graph:'cypher', iterations:100, dampingFactor:0.85, write: true, writeProperty:'" + pagerankRaw + "'}" +
                ")");
    }

    @UserFunction(name = "pagerank.tiers")
    @Description("Return counts of users in three pagerank tiers")
    public Map<Object, Object> getUserTiers() {
        log.info("Getting pagerank tier counts");
        Result tierCounts = db.execute("" +
                " MATCH (n:Actor) WHERE n.actorType='INDIVIDUAL' AND n.pagerankNorm > 10.0 RETURN 'TIER1' AS tier, COUNT(n) AS count" +
                " UNION MATCH (n:Actor) WHERE n.actorType='INDIVIDUAL' AND n.pagerankNorm > 3.0 AND n.pagerankNorm < 10.0 RETURN 'TIER2' AS tier, COUNT(n) AS count" +
                " UNION MATCH (n:Actor) WHERE n.actorType='INDIVIDUAL' AND n.pagerankNorm < 3.0 RETURN 'TIER3' AS tier, COUNT(n) AS count");
        return tierCounts.hasNext() ? resultToMap(tierCounts, "tier", "count") : null;
    }

}