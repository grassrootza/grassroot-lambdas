package za.org.grassroot.graph.scheduled;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.regions.Region;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.PutRecordResponse;

import javax.annotation.PostConstruct;
import java.nio.ByteBuffer;
import java.time.Instant;

// little utility for local things
@Component @Slf4j
@Profile("localtest")
public class ScheduledTestTasks {

    private KinesisClient kinesisClient;

    @PostConstruct
    public void initClient() {
        kinesisClient = KinesisClient.builder().region(Region.EU_WEST_1).build();
    }

    @Scheduled(fixedDelay = 10000)
    public void placeDataInStream() {
        log.info("inserting into stream, time now: {}", Instant.now());
        PutRecordResponse response = kinesisClient.putRecord(builder -> builder.streamName("grassroot-graph-test-stream")
                .partitionKey("test-1")
                .data(ByteBuffer.wrap(String.format("time now: %s", Instant.now().toString()).getBytes())));
        log.info("put record response: {}", response.toString());
    }
}
