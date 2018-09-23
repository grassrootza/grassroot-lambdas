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

exports.getLastMenu = async (userId) => {
    console.log('Looking for last menu sent to user ...');

    const cutoff = hoursInPast(config.get('conversation.cutoffHours'));
    
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
        'FilterExpression': 'attribute_exists(menuPayload)',
        'ScanIndexForward': false
    }
    
    console.log('about to call dynamodb');
    let result = await docClient.query(params).promise();
    console.log('DynamoDB menu check: ', result.Items);
    if (!result.Items.length || result.Items.length == 0)
        return undefined;

    // filter expression is behaving unreliably, hence this
    let menuIndex = result.Items.findIndex(item => item.hasOwnProperty('menuPayload') && item['menuPayload'].length > 0);
    if (menuIndex == -1)
        return undefined;
    console.log('index of menu item: ', menuIndex);

    console.log('found item: ', result.Items[menuIndex]['menuPayload']);

    return result.Items[menuIndex]['menuPayload'];
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

    // dynamodb does not reliably preserve ordering on maps, hence using menu for payloads and texts for what's shown to user
    if (reply.hasOwnProperty('menuPayload')) {
        item['menuPayload'] = reply['menuPayload'];
        item['menuText'] = reply['menuText'];
    };

    if (reply.hasOwnProperty('entity')) {
        item['entity'] = reply['entity'];
    };

    // todo: probably want a util for is non-empty dict
    if (reply.hasOwnProperty('auxProperties') && reply['auxProperties'] && Object.keys(reply['auxProperties']).length > 0) {
        console.log('Have aux properties present, storing');
        item['auxProperties'] = reply['auxProperties'];
    }

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
