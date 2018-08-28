const config = require('config');

const analyticsEnabled = config.has('analytics.apiKey');
console.log('analytics enabled? :', analyticsEnabled);

const dashbot = analyticsEnabled ? require('dashbot')(config.get('analytics.apiKey')).generic : null;

const AWS = require('aws-sdk');
AWS.config.update({
    region: 'eu-west-1',
});
const docClient = new AWS.DynamoDB.DocumentClient();

var exports = module.exports = {};

exports.getMostRecent = (content) => {
    const userMsisdn = content.from;
    const cutoff = hoursInPast(config.get('conversation.cutoffHours'));
    console.log('timestamp in past: ', cutoff);
    const params = {
        'TableName': 'chatConversationLogs',
        'KeyConditionExpression': 'userId = :val and #timestamp > :cutoff',
        'ExpressionAttributeNames': {
            '#timestamp': 'timestamp'
        },     
        'ExpressionAttributeValues': {
            ':val': userMsisdn,
            ':cutoff': cutoff
        },
        'Limit': 1,
        'ScanIndexForward': false
    }

    return docClient.query(params).promise();
}

// move this into its own lambda
exports.logIncoming = async (content, reply) => {
    const item = {
        'userId': content.from,
        'timestamp': Date.now(),
        'message': content.message,
        'reply': reply.message,
        'context': reply.context
    };

    if (reply.options) {
        item['options'] = reply.options;
    }

    console.log('assembled item: ', item);

    const params = {
        TableName: 'chatConversationLogs',
        Item: item
    };

    // await docClient.put(params).promise();

    console.log('analyics enabled? :', analyticsEnabled);
    if (analyticsEnabled) {
        dashbot.logIncoming({
            content: content
        });

        dashbot.logOutgoing({
            reply: reply
        });
    }
}

const hoursInPast = (number) => {
    var d = new Date();
    d.setHours(d.getHours()-number);
    return d.getTime();
}
