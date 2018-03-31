package za.org.grassroot.graph.kinesis;


import com.amazonaws.services.kinesis.clientlibrary.interfaces.IRecordProcessor;
import com.amazonaws.services.kinesis.clientlibrary.interfaces.IRecordProcessorFactory;

public class KinesisRecordConsumerFactory implements IRecordProcessorFactory {

    @Override
    public IRecordProcessor createProcessor() {
        return new KinesisRecordConsumer();
    }
}
