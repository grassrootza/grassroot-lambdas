package za.org.grassroot.graph.sqs;

import com.amazonaws.SdkClientException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.regions.Region;
import software.amazon.awssdk.services.sqs.SQSClient;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import za.org.grassroot.graph.dto.IncomingGraphAction;
import za.org.grassroot.graph.services.IncomingActionProcessor;

import javax.annotation.PostConstruct;
import java.io.IOException;

@Slf4j
@Component
@ConditionalOnProperty("sqs.pull.enabled")
public class SqsPuller {

    @Value("${sqs.url}")
    private String sqsUrl;

    private SQSClient sqs;

    private final ObjectMapper objectMapper;
    private final IncomingActionProcessor incomingActionProcessor;

    @Autowired
    public SqsPuller(ObjectMapper objectMapper, IncomingActionProcessor incomingActionProcessor) {
        this.objectMapper = objectMapper;
        this.incomingActionProcessor = incomingActionProcessor;
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
                    IncomingGraphAction action = deserialize(message.body());
                    log.info("message body deserialized: {}", action);
                    boolean success = incomingActionProcessor.processIncomingAction(action);
                    log.info("successfully handled? : ", success);
                    sqs.deleteMessage(builder -> builder.queueUrl(sqsUrl).receiptHandle(message.receiptHandle()));
                    log.info("message cleared from queue");
                } catch (IOException e) {
                    log.error("Error deserializing input: {}", message.body());
                    log.error("error, could not deserialize: ", e);
                }
        });
    }

    private IncomingGraphAction deserialize(String jsonValue) throws IOException {
        IncomingGraphAction action = objectMapper.readValue(jsonValue, IncomingGraphAction.class);
        log.info("incoming action: {}", action);
        return action;
    }


}
