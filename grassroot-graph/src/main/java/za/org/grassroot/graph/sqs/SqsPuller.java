package za.org.grassroot.graph.sqs;

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
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import za.org.grassroot.graph.dto.IncomingGraphAction;
import za.org.grassroot.graph.services.IncomingActionProcessor;

import javax.annotation.PostConstruct;
import java.io.IOException;

@Slf4j
@Component
@ConditionalOnProperty("sqs.pull.enabled")
public class SqsPuller {

    private final SqsProcessor sqsProcessor;

    @Value("${sqs.url}")
    private String sqsUrl;

    private SQSClient sqs;

    public SqsPuller(SqsProcessor sqsProcessor) {
        this.sqsProcessor = sqsProcessor;
    }

    @PostConstruct
    private void init() {
        try {
            this.sqs = SQSClient.builder().region(Region.EU_WEST_1).build();
        } catch (SdkClientException e) {
            log.error("Could not set up SQS client", e);
        }
    }

    @Scheduled(fixedRate = 10000)
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
            boolean success = sqsProcessor.handleSqsMessage(message.body());
            if (success) {
                sqs.deleteMessage(builder -> builder.queueUrl(sqsUrl).receiptHandle(message.receiptHandle()));
            }
        });
    }


}
