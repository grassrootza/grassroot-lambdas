package za.org.grassroot.graph.kinesis;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.kinesis.clientlibrary.interfaces.IRecordProcessorFactory;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.InitialPositionInStream;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.KinesisClientLibConfiguration;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.Worker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

@Slf4j @Component
public class KinesisProcessor {

    private static final String STREAM_NAME = "grassroot-graph-test-stream";
    private static ProfileCredentialsProvider credentialsProvider;

    @EventListener({ApplicationReadyEvent.class})
    public void init() {
        java.security.Security.setProperty("networkaddress.cache.ttl", "60");
        credentialsProvider = new ProfileCredentialsProvider();

        String workerId;
        try {
            workerId = InetAddress.getLocalHost().getCanonicalHostName() + ":" + UUID.randomUUID();
        } catch (UnknownHostException e) {
            workerId = "localhost:" + UUID.randomUUID();
        }

        KinesisClientLibConfiguration kinesisClientLibConfiguration =
                new KinesisClientLibConfiguration("grassroot-graph-test",
                        STREAM_NAME,
                        credentialsProvider,
                        workerId);
        kinesisClientLibConfiguration.withInitialPositionInStream(InitialPositionInStream.LATEST);
        kinesisClientLibConfiguration.withRegionName("eu-west-1");

        IRecordProcessorFactory recordProcessorFactory = new KinesisRecordConsumerFactory();
        Worker worker = new Worker(recordProcessorFactory, kinesisClientLibConfiguration);

        log.info("Running {} to process stream {} as worker {}...", "grassroot-graph-test", STREAM_NAME, workerId);

        try {
            worker.run();
        } catch (Throwable t) {
            log.error("Caught throwable while processing data.");
            t.printStackTrace();
        }
    }

}
