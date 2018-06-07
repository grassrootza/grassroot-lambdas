const { Pool } = require('pg')
const AWS = require('aws-sdk');

AWS.config.update({
    region: "eu-west-1",
});

console.log('about to connect to pool ...');

const pool = new Pool()
const docClient = new AWS.DynamoDB.DocumentClient();

pool.on('error', (err, client) => {
    console.error('Unexpected error on idle client', err)
    process.exit(-1)
  })

var queryPromises = [];

exports.handler = async (event) => {
    try {
        console.log('about to execute query ... ');

        const accountData = await pool.query('SELECT * FROM paid_account WHERE enabled = true');
        console.log('fetched ' + accountData.rows.size + ' accounts, first row: ', accountData.rows[0]);
        
        const priorTimes = await Promise.all(accountData.rows.map(getLastCalculatedTimeForAccount));
        console.log(priorTimes[0]);

        const newCounts = await Promise.all(priorTimes.map(calculateNotificationsForAccount));
        console.log(newCounts);

        await Promise.all(newCounts.map(putAccountIntoDynamoDB));

        console.log('*** DONE IN MAIN ***');
        pool.end();

        return 'succcess';
    } catch (err) {
        console.log('ERROR: ', err);
        return err;
    }
}

// exports.handler().then(console.log);

function getLastCalculatedTimeForAccount(paid_account) {
    console.log('fetching a time ...');
    params = {
        TableName: 'account_records',
        Key: {
            'account_id': paid_account['uid']
        }
    }

    return docClient.get(params).promise().then(item => {
        // console.log('item: ', item);
        let thisRecord = item['Item'];
        if (!thisRecord) {
            console.log('no item record, returning empties');
            return {
                'account_long': paid_account['id'],
                'account_id': paid_account['uid'], 
                'last_notification_calc': 0, 
                'cumulative_notifications': 0
            };
        } else {
            console.log('got a record, returning it');
            return {
                'account_long': paid_account['id'],
                'account_id': paid_account['uid'],
                'last_notification_calc': thisRecord['last_notification_calc'] || 0,
                'cumulative_notifications': thisRecord['cumulative_notifications'] || 0
            };
        }
    });
}

function calculateNotificationsForAccount(account_record) {
    console.log('calculating row ...');
    // add this when refactored branch is live: event_log_id IN (SELECT id from event_log WHERE event_id in (SELECT id FROM event where ancestor_group_id in (SELECT id from group_profile where account_id = X))));
    return pool.query("SELECT COUNT(*) FROM notification WHERE creation_time > TO_TIMESTAMP($1) AND " + 
            "delivery_channel NOT IN ('EMAIL_3RDPARTY', 'EMAIL_GRASSROOT', 'EMAIL_USERACCOUNT') AND (" + 
            "group_log_id in (SELECT ID FROM group_log WHERE target_account_id = $2) OR " +
            "account_log_id IN (SELECT ID FROM account_log WHERE account_id = $2) OR " +
            "campaign_log_id IN (SELECT ID FROM campaign_log WHERE campaign_id IN (SELECT id FROM campaign where account_id = $2)))", 
        [account_record['last_notification_calc'], account_record['account_long']]).then(res => {
            // console.log(`paid account: ${paid_account['account_name']}, count: ${res.rows[0]['count']}`);
            account_record['notification_count'] = res.rows[0]['count'];
            return account_record;
        })
        .catch(err => {
            console.log(err.stack);
        });
}

function putAccountIntoDynamoDB(account_record) {
    const timestamp = new Date().getTime();
    const since_time = account_record['last_notification_calc'];

    let params = {
        TableName: 'account-notification-counts',
        Item: {
            'accountid_time': account_record['account_id'] + '_' + timestamp,
            'calc_timestamp': timestamp,
            'start_timestamp': since_time,
            'notification_count': account_record['notification_count']
        }
    };

    // console.log('item: ', params);

    return docClient.put(params).promise().then(data => {
        console.log('succeeded ..., updating account');
        let cumulative_numbers = parseInt(account_record['notification_count']) + parseInt(account_record['cumulative_notifications']);
        let updateParams = {
            TableName: 'account_records',
            Item: {
                'account_id': account_record['account_id'],
                'last_notification_calc': timestamp,
                'cumulative_notifications': cumulative_numbers
            }
        }

        return docClient.put(updateParams).promise()
            .then(data => console.log('And cumulative record updated'))
            .catch(err => console.log('Error with cumulative'));
    }).catch(err => console.log('Error', err));
}
