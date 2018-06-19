package za.org.grassroot.graph.sqs;

import software.amazon.awssdk.services.sqs.SQSClient;
import software.amazon.awssdk.services.sqs.model.Message;

public interface SqsProcessor {

    long estimateProcessingTime(Message message);

    void handleSqsMessage(Message message, SQSClient sqsClient);

}
