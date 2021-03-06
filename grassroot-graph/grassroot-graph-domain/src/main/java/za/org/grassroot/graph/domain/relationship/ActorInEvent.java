package za.org.grassroot.graph.domain.relationship;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.neo4j.ogm.annotation.*;
import org.neo4j.ogm.id.UuidStrategy;
import za.org.grassroot.graph.domain.Actor;
import za.org.grassroot.graph.domain.Event;
import za.org.grassroot.graph.domain.GraphStringUtils;
import za.org.grassroot.graph.domain.enums.GrassrootRelationship;

import java.util.Set;

@Getter @Setter @NoArgsConstructor
@RelationshipEntity(type= GrassrootRelationship.TYPE_PARTICIPATES)
public class ActorInEvent {

    @Id @GeneratedValue(strategy = UuidStrategy.class) private String id;

    @StartNode private Actor participant;
    @EndNode private Event participatesIn;

    @Property private boolean responded;
    @Property private String response;

    public ActorInEvent(Actor participant, Event participatesIn) {
        this.participant = participant;
        this.participatesIn = participatesIn;
        this.responded = false;
    }

    @Override
    public String toString() {
        return "ActorInActor{" +
                "id='" + id + '\'' +
                ", participant=" + participant +
                ", participatesIn=" + participatesIn +
                '}';
    }

}