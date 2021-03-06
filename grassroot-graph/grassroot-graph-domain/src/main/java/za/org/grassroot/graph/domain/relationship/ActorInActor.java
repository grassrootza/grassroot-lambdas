package za.org.grassroot.graph.domain.relationship;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.neo4j.ogm.annotation.*;
import org.neo4j.ogm.id.UuidStrategy;
import za.org.grassroot.graph.domain.Actor;
import za.org.grassroot.graph.domain.GraphStringUtils;
import za.org.grassroot.graph.domain.enums.GrassrootRelationship;

import java.time.Instant;
import java.util.Set;
import java.util.HashSet;

@Getter @Setter @NoArgsConstructor
@RelationshipEntity(type= GrassrootRelationship.TYPE_PARTICIPATES)
public class ActorInActor {

    @Id @GeneratedValue(strategy = UuidStrategy.class) private String id;

    @StartNode private Actor participant;
    @EndNode private Actor participatesIn;

    @Property private Instant establishedTime; // on the platform

    @Property private String[] stdTags;

    public ActorInActor(Actor participant, Actor participatesIn, Instant establishedTime) {
        this.participant = participant;
        this.participatesIn = participatesIn;
        this.establishedTime = establishedTime;
    }

    public void addTags(Set<String> newTags) {
        this.stdTags = GraphStringUtils.addStringsToArray(this.stdTags, newTags);
    }

    public void removeTags(Set<String> tagsToRemove) {
        this.stdTags = GraphStringUtils.removeStringsFromArray(this.stdTags, tagsToRemove);
    }

    @Override
    public String toString() {
        return "ActorInActor{" +
                "id='" + id + '\'' +
                ", participant=" + participant +
                ", participatesIn=" + participatesIn +
                ", establishedTime=" + establishedTime +
                '}';
    }

}