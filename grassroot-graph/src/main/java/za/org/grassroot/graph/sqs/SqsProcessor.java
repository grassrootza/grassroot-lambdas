package za.org.grassroot.graph.sqs;

import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sqs.SQSClient;
import software.amazon.awssdk.services.sqs.model.Message;

public interface SqsProcessor {

    long estimateProcessingTime(Message message);

    Mono<Boolean> handleSqsMessage(Message message);

}
