package za.org.grassroot.graph.sqs;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.org.grassroot.graph.dto.IncomingGraphAction;
import za.org.grassroot.graph.services.IncomingActionProcessor;

import java.io.IOException;

@Component @Slf4j
public class SqsProcessorImpl implements SqsProcessor {

    private final ObjectMapper objectMapper;
    private final IncomingActionProcessor incomingActionProcessor;

    public SqsProcessorImpl(ObjectMapper objectMapper, IncomingActionProcessor incomingActionProcessor) {
        this.objectMapper = objectMapper;
        this.incomingActionProcessor = incomingActionProcessor;
    }

    @Override
    public boolean handleSqsMessage(String message) {
        try {
            IncomingGraphAction action = deserialize(message);
            log.info("message body deserialized: {}", action);
            boolean success = incomingActionProcessor.processIncomingAction(action);
            log.info("successfully handled? : ", success);
            return success;
        } catch (IOException e) {
            log.error("Error deserializing input: {}", message);
            log.error("error, could not deserialize: ", e);
            return false;
        }
    }

    private IncomingGraphAction deserialize(String jsonValue) throws IOException {
        IncomingGraphAction action = objectMapper.readValue(jsonValue, IncomingGraphAction.class);
        log.info("incoming action: {}", action);
        return action;
    }
}
