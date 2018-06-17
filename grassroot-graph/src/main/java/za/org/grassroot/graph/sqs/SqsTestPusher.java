package za.org.grassroot.graph.sqs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.regions.Region;
import software.amazon.awssdk.services.sqs.SQSClient;
import za.org.grassroot.graph.domain.Actor;
import za.org.grassroot.graph.domain.GrassrootGraphEntity;
import za.org.grassroot.graph.domain.enums.ActorType;

import javax.annotation.PostConstruct;
import java.time.Instant;

@Slf4j @Component
@ConditionalOnProperty("sqs.push.enabled")
public class SqsTestPusher {

    @Value("${sqs.url}")
    private String sqsUrl;

    private SQSClient sqs;
    private final ObjectMapper objectMapper;

    @Autowired
    public SqsTestPusher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    private void init() {
        try {
            this.sqs = SQSClient.builder().region(Region.EU_WEST_1).build();
            log.info("SQS client successfully set up");
        } catch (SdkClientException e) {
            log.error("Could not set up SQS client but SQS enabled", e);
        }
    }

    @Scheduled(fixedDelay = 100000)
    public void placeDataInSQS() {
        log.info("inserting into SQS, time now: {}", Instant.now());

        Actor actor = new Actor(ActorType.INDIVIDUAL, "test-actor-" + Instant.now());
        final String testValue = serialize(actor);
        log.info("placing into stream: {}", testValue);

        sqs.sendMessage(builder -> builder.queueUrl(sqsUrl)
            .messageBody(testValue));
    }

    private String serialize(GrassrootGraphEntity testEntity) {
        try {
            return objectMapper.writeValueAsString(testEntity);
        } catch (JsonProcessingException e) {
            log.error("failed to serialize, error: ", e);
            return "__error__";
        }
    }



}
