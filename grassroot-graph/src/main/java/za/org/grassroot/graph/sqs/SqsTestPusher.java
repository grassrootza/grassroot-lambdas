package za.org.grassroot.graph.sqs;

import com.amazonaws.SdkClientException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.regions.Region;
import software.amazon.awssdk.services.kinesis.model.PutRecordResponse;
import software.amazon.awssdk.services.sqs.SQSClient;
import za.org.grassroot.graph.domain.Actor;
import za.org.grassroot.graph.domain.GrassrootGraphEntity;
import za.org.grassroot.graph.domain.enums.ActorType;

import javax.annotation.PostConstruct;
import java.nio.ByteBuffer;
import java.time.Instant;

@Slf4j @Component
@ConditionalOnProperty("sqs.enabled")
public class SqsTestPusher {

//    private AWSSqsqs;

    private SQSClient sqs;

    @PostConstruct
    private void init() {
        try {
            this.sqs = SQSClient.builder().region(Region.EU_WEST_1).build();
            log.info("SQS client successfully set up");
        } catch (SdkClientException e) {
            log.error("Could not set up SQS client but callback q enabled", e);
        }
    }

    @Scheduled(fixedDelay = 100000)
    public void placeDataInSQS() {
        log.info("inserting into SQS, time now: {}", Instant.now());

        Actor actor = new Actor(ActorType.INDIVIDUAL);
        final String testValue = serialize(actor);
        log.info("placing into stream: {}", testValue);

        sqs.sendMessage(builder -> builder.queueUrl("https://sqs.eu-west-1.amazonaws.com/257542705753/test-grassroot-graph")
            .messageBody(testValue));
//        log.info("put record response: {}", response.toString());
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
