package za.org.grassroot.graph.sqs;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;
import software.amazon.awssdk.core.auth.SystemPropertyCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.regions.Region;
import software.amazon.awssdk.services.sqs.SQSClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityResponse;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import za.org.grassroot.graph.domain.GraphStringUtils;

import javax.annotation.PostConstruct;

@Slf4j
@Component
@ConditionalOnProperty("sqs.pull.enabled")
public class SqsPuller {

    private static final long QUEUE_DEFAULT_TIME = 60 * 1000;

    private final SqsProcessor sqsProcessor;

    @Value("${sqs.crud.url}")
    private String sqsUrl;

    @Value("${sqs.pull.crud.messages:1}")
    private int numberMessagesToPull;

    @Value("${aws.accessKeyId:}")
    private String awsAccessKey;

    @Value("${aws.secretAccessKey:}")
    private String awsSecretKey;

    @Value("${sqs.delete.failure:false}")
    private boolean deleteEvenOnFailure;

    private SQSClient sqs;

    public SqsPuller(SqsProcessor sqsProcessor) {
        this.sqsProcessor = sqsProcessor;
    }

    @PostConstruct
    private void init() {
        log.info("Setting up SQS client, url: {}", sqsUrl);
        if (!GraphStringUtils.isEmpty(awsAccessKey) && !GraphStringUtils.isEmpty(awsSecretKey)) {
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

    @Scheduled(cron = "${sqs.pull.crud.rate:*/60 * * * * *}")
    public void readDataFromSqs() {
        log.info("Pulling from SQS ... queue: {}", sqsUrl);

        if (sqs == null) {
            log.info("Error! sqs client null");
            return;
        }

        ReceiveMessageResponse response  = sqs.receiveMessage(builder -> builder.queueUrl(sqsUrl)
            .maxNumberOfMessages(numberMessagesToPull));

        if (response.messages() == null || response.messages().isEmpty()) {
            log.info("empty message queue, exiting, messages: {}", response.messages());
            return;
        }

        log.info("Fetched {} messages", response.messages().size());

        response.messages().forEach(message -> {
            log.info("Setting up subscription for {}", message.receiptHandle());
            long timeEstimate = sqsProcessor.estimateProcessingTime(message);

            log.info("Processing message, estimating {} msecs, handle {}", timeEstimate, message.receiptHandle());
            if (timeEstimate > QUEUE_DEFAULT_TIME) {
                ChangeMessageVisibilityResponse extendVisibilityResponse = sqs.changeMessageVisibility(builder -> builder
                        .queueUrl(sqsUrl)
                        .receiptHandle(message.receiptHandle())
                    .visibilityTimeout((int) (timeEstimate / 1000)));
                log.info("Visibility change response: {}", extendVisibilityResponse.toString());
            }

            sqsProcessor.handleSqsMessage(message)
                    .subscribeOn(Schedulers.elastic())
                    .subscribe(success -> {
                        log.info("Successfully handled message? : {}", success);
                        if (success || deleteEvenOnFailure) {
                            try {
                                sqs.deleteMessage(builder -> builder.queueUrl(sqsUrl).receiptHandle(message.receiptHandle()));
                                log.info("Message handled, deleted");
                            } catch (SdkClientException e) {
                                log.error("Error deleting message with handle: {}", message.receiptHandle());
                            }
                        }
                    });
        });
    }


}
