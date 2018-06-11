package za.org.grassroot.graph.sqs;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.auth.SystemPropertyCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.regions.Region;
import software.amazon.awssdk.services.sqs.SQSClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityResponse;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import javax.annotation.PostConstruct;

@Slf4j
@Component
@ConditionalOnProperty("sqs.pull.enabled")
public class SqsPuller {

    private static final long QUEUE_DEFAULT_TIME = 2 * 60 * 1000;

    private final SqsProcessor sqsProcessor;

    @Value("${sqs.url}")
    private String sqsUrl;

    @Value("${aws.accessKeyId:}")
    private String awsAccessKey;

    @Value("${aws.secretAccessKey:}")
    private String awsSecretKey;

    private SQSClient sqs;

    public SqsPuller(SqsProcessor sqsProcessor) {
        this.sqsProcessor = sqsProcessor;
    }

    @PostConstruct
    private void init() {
        log.info("Setting up SQS client, url: {}", sqsUrl);
        if (!StringUtils.isEmpty(awsAccessKey) && !StringUtils.isEmpty(awsSecretKey)) {
            setUpSqsFromCredentials();
        } else {
            setUpDefaultCredentials();
        }
    }

    private void setUpSqsFromCredentials() {
        log.info("Have AWS credentials, using them ...");
        System.setProperty("aws.accessKeyId", awsAccessKey);
        System.setProperty("aws.secretAccessKey", awsSecretKey);
        SystemPropertyCredentialsProvider provider = SystemPropertyCredentialsProvider.create();
        log.info("created provider, credentials: {}", provider.getCredentials());
        this.sqs = SQSClient.builder()
                .credentialsProvider(provider)
                .region(Region.EU_WEST_1)
                .build();
    }

    private void setUpDefaultCredentials() {
        try {
            this.sqs = SQSClient.builder()
                    .region(Region.EU_WEST_1).build();
        } catch (SdkClientException e) {
            log.error("Could not set up SQS client", e);
        }
    }

    @Scheduled(fixedRate = 10000)
    public void readDataFromSqs() {
        log.info("Pulling from SQS ... queue: {}", sqsUrl);

        if (sqs == null) {
            log.info("Error! sqs client null");
            return;
        }

        ReceiveMessageResponse response  = sqs.receiveMessage(builder -> builder.queueUrl(sqsUrl)
            .maxNumberOfMessages(3));

        if (response.messages() == null || response.messages().isEmpty()) {
            log.info("empty message queue, exiting, messages: {}", response.messages());
            return;
        }

        log.info("Fetched {} messages", response.messages().size());

        response.messages().forEach(message -> {
            final String receiptHandle = message.receiptHandle();
            long timeEstimate = sqsProcessor.estimateProcessingTime(message);

            log.info("Estimating {} msecs to process message", timeEstimate);
            if (timeEstimate > QUEUE_DEFAULT_TIME) {
                ChangeMessageVisibilityResponse extendVisibilityResponse = sqs.changeMessageVisibility(builder -> builder.queueUrl(sqsUrl).receiptHandle(receiptHandle)
                    .visibilityTimeout((int) (timeEstimate / 1000)));
                log.info("Visibility change response: {}", extendVisibilityResponse.toString());
            }

            boolean success = sqsProcessor.handleSqsMessage(message.body());
            if (success) {
                log.info("Handled message, deleting it");
                try {
                    sqs.deleteMessage(builder -> builder.queueUrl(sqsUrl).receiptHandle(receiptHandle));
                    log.info("Message handled, deleting");
                } catch (SdkClientException e) {
                    log.error("Error deleting message with handle: {}", receiptHandle);
                }
            }
        });
    }


}
