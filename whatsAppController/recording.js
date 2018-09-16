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

exports.getMostRecent = (userId) => {
    const cutoff = hoursInPast(config.get('conversation.cutoffHours'));
    console.log('timestamp in past: ', cutoff);
    const params = {
        'TableName': 'chatConversationLogs',
        'KeyConditionExpression': 'userId = :val and #timestamp > :cutoff',
        'ExpressionAttributeNames': {
            '#timestamp': 'timestamp'
        },     
        'ExpressionAttributeValues': {
            ':val': userId,
            ':cutoff': cutoff
        },
        'Limit': 1,
        'ScanIndexForward': false
    }

    return docClient.query(params).promise();
}

// move this into its own lambda
exports.logIncoming = async (content, reply, userId) => {
    console.log('will record: ', reply);
    
    const item = {
        'userId': userId,
        'timestamp': Date.now(),
        'message': content.message,
        'reply': reply.textSingle,
        'domain': reply.domain
    };

    if (reply.hasOwnProperty('menu')) {
        item['menu'] = reply['menu'];
    };

    if (reply.hasOwnProperty('entity')) {
        item['entity'] = reply['entity'];
    };

    console.log('assembled item to record: ', item);

    const params = {
        TableName: 'chatConversationLogs',
        Item: item
    };

    await docClient.put(params).promise();

    console.log('analyics enabled? :', analyticsEnabled);
    if (analyticsEnabled) {
        incomingLog = {
            'userId': userId,
            'text': content['message']
        };
        if (reply.hasOwnProperty('intent')) {
            incomingLog['intent'] = reply['intent']; // is actually intent of user incoming, but extracted from core result
        }
        
        console.log('dispatching to dashbot: ', incomingLog);
        dashbot.logIncoming(incomingLog);

        outgoingLog = {
            'userId': reply.userId,
            'text': reply.textSingle
        };

        if (reply.hasOwnProperty('action')) {
            outgoingLog['platformJson'] = {'action': reply['action']};
        }
        
        console.log('dispatching to dashbot: ', outgoingLog);
        dashbot.logOutgoing(outgoingLog);
    }
}

const hoursInPast = (number) => {
    var d = new Date();
    d.setHours(d.getHours()-number);
    return d.getTime();
}
