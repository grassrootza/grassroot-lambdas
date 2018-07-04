package za.org.grassroot.graph.sqs;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
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
    public Mono<Boolean> handleSqsMessage(Message message) {
        IncomingGraphAction action = deserialize(message.body());
        if (action == null) {
            log.error("Error deserializing message: ", message);
        }

        log.debug("message body deserialized: {}", action);
        return incomingActionProcessor.processIncomingAction(action);
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
