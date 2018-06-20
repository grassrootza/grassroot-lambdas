package za.org.grassroot.graph.services;

import reactor.core.publisher.Mono;
import za.org.grassroot.graph.dto.IncomingGraphAction;

public interface IncomingActionProcessor {

    Mono<Boolean> processIncomingAction(IncomingGraphAction action);

}
