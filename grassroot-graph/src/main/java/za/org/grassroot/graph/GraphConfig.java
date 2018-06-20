package za.org.grassroot.graph;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.neo4j.ogm.session.event.EventListener;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.EnableScheduling;
import za.org.grassroot.graph.repository.PostSaveListener;
import za.org.grassroot.graph.repository.PreSaveListener;

@Configuration
@EnableScheduling
public class GraphConfig {

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
    public ObjectMapper objectMapper(){
        return new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .registerModule(new JavaTimeModule());
    }

    @Bean
    public EventListener preSaveEventListener() {
        return new PreSaveListener();
    }

    @Bean
    public EventListener postSaveEventListener() {
        return new PostSaveListener();
    }

}