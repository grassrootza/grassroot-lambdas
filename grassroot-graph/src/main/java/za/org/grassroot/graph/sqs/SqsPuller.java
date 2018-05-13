package za.org.grassroot.graph.sqs;

import com.amazonaws.SdkClientException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.regions.Region;
import software.amazon.awssdk.services.sqs.SQSClient;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import za.org.grassroot.graph.domain.Actor;
import za.org.grassroot.graph.domain.GrassrootGraphEntity;
import za.org.grassroot.graph.repository.ActorRepository;

import javax.annotation.PostConstruct;
import java.io.IOException;

@Slf4j
@Component
@ConditionalOnProperty("sqs.enabled")
public class SqsPuller {

    @Value("${sqs.url}")
    private String sqsUrl;

    private SQSClient sqs;

    private final ObjectMapper objectMapper;
    private final ActorRepository actorRepository;

    @Autowired
    public SqsPuller(ObjectMapper objectMapper, ActorRepository actorRepository) {
        this.objectMapper = objectMapper;
        this.actorRepository = actorRepository;
    }

    @PostConstruct
    private void init() {
        try {
            this.sqs = SQSClient.builder().region(Region.EU_WEST_1).build();
        } catch (SdkClientException e) {
            log.error("Could not set up SQS client", e);
        }
    }

    @Scheduled(fixedRate = 100000)
    public void readDataFromSqs() {
        log.info("pulling from SQS ...");

        if (sqs == null) {
            log.info("error! sqs client null");
            return;
        }

        ReceiveMessageResponse response  = sqs.receiveMessage(builder -> builder.queueUrl(sqsUrl)
            .maxNumberOfMessages(10));

        if (response.messages() == null || response.messages().isEmpty()) {
            log.info("empty message queue, exiting");
            return;
        }

        log.info("fetched {} messages", response.messages().size());

        response.messages().forEach(message -> {
                try {
                    GrassrootGraphEntity entity = deserialize(message.body());
                    log.info("message body deserialized: {}", entity);
                    if (entity instanceof Actor) {
                        log.info("storing an actor ...");
                        actorRepository.save((Actor) entity);
                    }
                    sqs.deleteMessage(builder -> builder.queueUrl(sqsUrl).receiptHandle(message.receiptHandle()));
                    log.info("message cleared from queue");
                } catch (IOException e) {
                    log.error("error, could not deserialize: {}", e);
                }
        });
    }

    private GrassrootGraphEntity deserialize(String jsonValue) throws IOException {
        GrassrootGraphEntity graphEntity = objectMapper.readValue(jsonValue, GrassrootGraphEntity.class);
        log.info("data object: {}", graphEntity);
        if (graphEntity instanceof Actor) {
            Actor actor = (Actor) graphEntity;
            log.info("deserialized actor type: {}", actor.getActorType());
        }
        return graphEntity;
    }


}
