const { Pool } = require('pg')
const AWS = require ('aws-sdk')
var moment = require('moment');

AWS.config.update({
    region: 'eu-west-1'
});

const pool = new Pool()
const docClient = new AWS.DynamoDB.DocumentClient();

const DAYS_PRIOR_DATE = process.env.DAYS_PRIOR_DATE;

pool.on('error', (err, client) => {
    console.error('Unexpected error on idle client', err)
    process.exit(-1)
  })

function deleteOldNotifications(numberDaysAgo) {
    // console.log('executing item: ', metric);
    let endTime = moment().subtract(numberDaysAgo, 'days');
    console.log(`deleting notifications prior to: ${endTime.format()}`);
    const query = 'delete FROM notification WHERE creation_time < TO_TIMESTAMP($1)';
    return pool.query(query, [endTime.unix()]).then(result => {
        console.log('result of query, first row: ', result.rows[0]);
        return result.rows;
    })
}

exports.handler = async (event) => {
    await deleteOldNotifications(DAYS_PRIOR_DATE);
    console.log('All done');
}

// exports.handler().then(console.log('Done ...'));
