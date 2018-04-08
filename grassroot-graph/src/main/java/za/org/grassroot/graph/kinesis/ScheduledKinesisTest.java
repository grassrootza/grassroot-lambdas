package za.org.grassroot.graph.kinesis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.regions.Region;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.PutRecordResponse;
import za.org.grassroot.graph.domain.Actor;
import za.org.grassroot.graph.domain.GrassrootGraphEntity;
import za.org.grassroot.graph.domain.enums.ActorType;

import javax.annotation.PostConstruct;
import java.nio.ByteBuffer;
import java.time.Instant;

// little utility for local things
@Component @Slf4j
@Profile("localtest")
@ConditionalOnProperty("kinesis.enabled")
public class ScheduledKinesisTest {

    private KinesisClient kinesisClient;

    @PostConstruct
    public void initClient() {
        kinesisClient = KinesisClient.builder().region(Region.EU_WEST_1).build();
    }

    @Scheduled(fixedDelay = 1000)
    public void placeDataInKinesisStream() {
        log.info("inserting into stream, time now: {}", Instant.now());

        Actor actor = new Actor(ActorType.INDIVIDUAL);
        final String testValue = serialize(actor);
        log.info("placing into stream: {}", testValue);

        PutRecordResponse response = kinesisClient.putRecord(builder -> builder.streamName("grassroot-graph-test-stream")
                .partitionKey("test-1")
                .data(ByteBuffer.wrap(testValue.getBytes())));
        log.info("put record response: {}", response.toString());
    }

    private String serialize(GrassrootGraphEntity graphEntity) {
        try {
            return new ObjectMapper().writeValueAsString(graphEntity);
        } catch (JsonProcessingException e) {
            log.error("failed to serialize, error: ", e);
            return "__error__";
        }
    }
}
