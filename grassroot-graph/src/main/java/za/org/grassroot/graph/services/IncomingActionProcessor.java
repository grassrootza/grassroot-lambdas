package za.org.grassroot.graph.services;

import za.org.grassroot.graph.dto.IncomingGraphAction;

public interface IncomingActionProcessor {

    boolean processIncomingAction(IncomingGraphAction action);

}
