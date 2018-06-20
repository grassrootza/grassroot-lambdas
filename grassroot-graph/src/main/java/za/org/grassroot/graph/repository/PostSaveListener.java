package za.org.grassroot.graph.repository;

import lombok.extern.slf4j.Slf4j;
import org.neo4j.ogm.session.event.Event;
import org.neo4j.ogm.session.event.EventListenerAdapter;

@Slf4j
public class PostSaveListener extends EventListenerAdapter {

    @Override
    public void onPostSave(Event event) {
        log.debug("Completed saving: {}", event.getObject());
    }

}
