const { Pool } = require('pg')
const AWS = require ('aws-sdk')

AWS.config.update({
    region: 'eu-west-1'
});

const pool = new Pool()
const docClient = new AWS.DynamoDB.DocumentClient();

const METRICS_TABLE_NAME = process.env.METRICS_TABLE_NAME;
const COUNTS_TABLE_NAME = process.env.COUNTS_TABLE_NAME;

pool.on('error', (err, client) => {
    console.error('Unexpected error on idle client', err)
    process.exit(-1)
  })

function getKeysQueriesAndPriorTimes() {
    console.log('fetching activity counts ...');
    params = {
        TableName: METRICS_TABLE_NAME
    }

    return docClient.scan(params).promise().then(result => result.Items);
}

function executeActivityCount(metric) {
    // console.log('executing item: ', metric);
    let currentTime = (new Date).getTime();
    console.log(`calculating between ${metric.lastCalcTime} and ${currentTime}, query: ${metric.query}`);
    return pool.query(metric.query, [metric.lastCalcTime / 1000, currentTime / 1000]).then(result => {
        console.log('result of query, first row: ', result.rows[0]);
        metric.newCalc = parseFloat(result.rows[0].count);
        metric.newCalcTime = currentTime;
        return metric;
    })
}

function storeActivityCount(metric) {
    console.log('storing metric result: ', metric);
    let params = {
        TableName: COUNTS_TABLE_NAME,
        Item: {
            'metric_name': metric.name,
            'end_time': metric.newCalcTime,
            'start_time': metric.lastCalcTime,
            'this_count': metric.newCalc,
            'total_count': (metric.totalCount | 0) + metric.newCalc
        }
    };

    return docClient.put(params).promise().then(_ => {
        console.log('inserted row calc, doing next ...');
        let updateParams = {
            TableName: METRICS_TABLE_NAME,
            Item: {
                'name': metric.name,
                'query': metric.query, // else overwrites
                'lastCalcTime': metric.newCalcTime,
                'totalCount': (metric.totalCount | 0) + metric.newCalc
            }
        };

        console.log('params: ', updateParams);

        return docClient.put(updateParams).promise()
        .then(data => console.log('And cumulative record updated'))
        .catch(err => console.log('Error with cumulative: ', err));
    });
}

exports.handler = async (event) => {
    const metricItems = await getKeysQueriesAndPriorTimes();
    const countResults = await Promise.all(metricItems.map(executeActivityCount));
    console.log('countResults: ', countResults);
    await Promise.all(countResults.map(storeActivityCount));
    console.log('All done');
}

// exports.handler().then(console.log('Done ...'));
