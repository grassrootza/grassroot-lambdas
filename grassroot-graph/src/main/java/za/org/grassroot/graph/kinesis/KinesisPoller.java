package za.org.grassroot.graph.kinesis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.regions.Region;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.*;

import javax.annotation.PostConstruct;

@Component @Slf4j
@Profile("localtest")
@ConditionalOnProperty("kinesis.enabled")
public class KinesisPoller {

    private KinesisClient kinesisClient;

    @PostConstruct
    public void initClient() {
        kinesisClient = KinesisClient.builder().region(Region.EU_WEST_1).build();
    }

    @Scheduled(fixedRate = 100000)
    public void describeStream() {
        log.info("polling kinesis ... ");
        DescribeStreamRequest request = DescribeStreamRequest.builder().streamName("grassroot-graph-test-stream").build();
        log.info("response: {}", kinesisClient.describeStream(request).streamDescription());
    }

//    @Scheduled(fixedRate = 10000)
    public void pullRecords() {
        String shardId = kinesisClient.describeStream(builder -> builder.streamName("grassroot-graph-test-stream").build())
                .streamDescription().shards().iterator().next().shardId();
        log.info("checking for records, from shard ID: {}", shardId);
        GetShardIteratorResponse shardIterator = kinesisClient.getShardIterator(builder -> builder
                .streamName("grassroot-graph-test-stream").shardId(shardId)
                .shardIteratorType(ShardIteratorType.TRIM_HORIZON));
        GetRecordsResponse recordsResponse = kinesisClient.getRecords(builder -> builder
                .limit(10)
                .shardIterator(shardIterator.shardIterator()));
        log.info("get records response, number of records: {}", recordsResponse.records().size());
    }

}
