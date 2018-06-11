package za.org.grassroot.graph.sqs;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.model.Message;
import za.org.grassroot.graph.dto.IncomingGraphAction;
import za.org.grassroot.graph.services.IncomingActionProcessor;

import java.io.IOException;

@Component @Slf4j
public class SqsProcessorImpl implements SqsProcessor {

    private static final long PER_OPERATION_TIME_ESTIMATE = 2000; // conservative, takes two seconds

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
    public boolean handleSqsMessage(String message) {
        IncomingGraphAction action = deserialize(message);
        if (action == null) {
            log.error("Error deserializing message: ", message);
        }

        log.info("message body deserialized: {}", action);
        boolean success = incomingActionProcessor.processIncomingAction(action);
        log.info("successfully handled? : {}", success);
        return success;
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
