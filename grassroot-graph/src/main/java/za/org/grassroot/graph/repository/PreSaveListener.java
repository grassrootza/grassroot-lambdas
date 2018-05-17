package za.org.grassroot.graph.repository;

import org.neo4j.ogm.session.event.Event;
import org.neo4j.ogm.session.event.EventListenerAdapter;
import za.org.grassroot.graph.domain.GrassrootGraphEntity;

import java.time.Instant;

public class PreSaveListener extends EventListenerAdapter {

    @Override
    public void onPreSave(Event event) {
        if (event.getObject() instanceof GrassrootGraphEntity) {
            GrassrootGraphEntity graphEntity = (GrassrootGraphEntity) event.getObject();
            if (graphEntity.getCreationTime() == null) {
                graphEntity.setCreationTime(Instant.now());
            }
        }
    }
}