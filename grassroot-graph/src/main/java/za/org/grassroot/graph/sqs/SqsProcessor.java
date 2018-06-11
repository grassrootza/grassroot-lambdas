package za.org.grassroot.graph.sqs;

import software.amazon.awssdk.services.sqs.model.Message;

public interface SqsProcessor {

    long estimateProcessingTime(Message message);

    boolean handleSqsMessage(String message);

}
