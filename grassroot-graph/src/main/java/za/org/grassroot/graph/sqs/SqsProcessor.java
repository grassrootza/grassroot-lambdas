package za.org.grassroot.graph.sqs;

public interface SqsProcessor {

    boolean handleSqsMessage(String message);

}
