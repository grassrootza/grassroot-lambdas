package za.org.grassroot.graph.sqs;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sqs.SQSClient;
import software.amazon.awssdk.services.sqs.model.Message;
import za.org.grassroot.graph.dto.IncomingGraphAction;
import za.org.grassroot.graph.services.IncomingActionProcessor;

import java.io.IOException;

@Component @Slf4j
public class SqsProcessorImpl implements SqsProcessor {

    @Value("${sqs.url}")
    private String sqsUrl;

    private static final long PER_OPERATION_TIME_ESTIMATE = 1000; // conservative, takes one second

    private final ObjectMapper objectMapper;
    private final IncomingActionProcessor incomingActionProcessor;

    public SqsProcessorImpl(ObjectMapper objectMapper, IncomingActionProcessor incomingActionProcessor) {
        this.objectMapper = objectMapper;
        this.incomingActionProcessor = incomingActionProcessor;
    }

    @Override
    public long estimateProcessingTime(Message message) {
        IncomingGraphAction action = deserialize(message.body());
        return action == null ? 0 : action.operationsCount() * PER_OPERATION_TIME_ESTIMATE;
    }

    @Override
    public void handleSqsMessage(Message message, SQSClient sqsClient) {
        final String receiptHandle = message.receiptHandle();
        long timeEstimate = estimateProcessingTime(message);

        log.info("Estimating {} msecs to process message", timeEstimate);
//            if (timeEstimate > QUEUE_DEFAULT_TIME) {
//                ChangeMessageVisibilityResponse extendVisibilityResponse = sqs.changeMessageVisibility(builder -> builder.queueUrl(sqsUrl).receiptHandle(receiptHandle)
//                    .visibilityTimeout((int) (timeEstimate / 1000)));
//                log.info("Visibility change response: {}", extendVisibilityResponse.toString());
//            }

        IncomingGraphAction action = deserialize(message.body());
        if (action == null) {
            log.error("Error deserializing message: ", message);
        }

        log.info("message body deserialized: {}", action);
        boolean success = incomingActionProcessor.processIncomingAction(action);
        log.info("successfully handled? : {}", success);

        if (success) {
            log.info("Handled message, deleting it");
            try {
                sqsClient.deleteMessage(builder -> builder.queueUrl(sqsUrl).receiptHandle(receiptHandle));
                log.info("Message handled, deleting");
            } catch (SdkClientException e) {
                log.error("Error deleting message with handle: {}", receiptHandle);
            }
        }
    }

    private IncomingGraphAction deserialize(String jsonValue) {
        try {
            IncomingGraphAction action = objectMapper.readValue(jsonValue, IncomingGraphAction.class);
            log.info("incoming action: {}", action);
            return action;
        } catch (IOException e) {
            log.error("Error deserializing input: {}", jsonValue);
            log.error("error, could not deserialize: ", e);
            return null;
        }
    }
}
