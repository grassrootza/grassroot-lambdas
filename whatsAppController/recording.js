const config = require('config');

const analyticsEnabled = config.has('analytics.apiKey');
console.log('analytics enabled? :', analyticsEnabled);
const dashbot = analyticsEnabled ? require('dashbot')(config.get('analytics.apiKey')).generic : null;

const dlqEnabled = config.has('error.dlq');
console.log('dlq enabled?: ', dlqEnabled);

const AWS = require('aws-sdk');
AWS.config.update({
    region: 'eu-west-1',
});
const docClient = new AWS.DynamoDB.DocumentClient();
const lambdaClient = new AWS.Lambda();

var exports = module.exports = {};

exports.storeInboundMedia = (userId, userMessage) => {
    const payload = {
        "media_id": userMessage['message']['media_id'],
        "mime_type": userMessage['message']['media_type'],
        "user_id": userId,
        "entity_uid": "campaign_12345",
        "entity_type": "CAMPAIGN"
    }

    const lambdaParams = {
        FunctionName: "whatsAppMediaStorage-production-store",
        Payload: JSON.stringify(payload)
    };

    const lambdaResponse = lambdaClient.invoke(lambdaParams).promise();

    console.log('Response from Lambda: ', lambdaResponse);

    return lambdaResponse;
}

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
    // console.log('DynamoDB menu check: ', result.Items);
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
    
    const currentMillis = Date.now();
    const expirySeconds = Math.round((currentMillis / 1000) + (3 * 24 * 60 * 60));

    console.log(`Recording, with expiry seconds: ${expirySeconds} and current millis: ${currentMillis}`);

    const item = {
        'userId': userId,
        'timestamp': currentMillis,
        'message': content.message,
        'expiry': expirySeconds,
        'domain': reply.domain
    };

    if (content.hasOwnProperty('textSingle') && content.textSingle.length > 0) {
        item['reply'] = reply.textSingle;
    }

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

exports.dispatchToDLQ = async (error) => {
    if (dlqEnabled) {
        const snsClient = new AWS.SNS();

        // strip tokens from DLQ as it's going out by email etc
        if (error.hasOwnProperty('options')) {
            error['options']['auth'] = '';
        }

        if (error.response && error.response.request && error.response.request.headers) {
            error['response']['request']['headers']['authorization'] = '';
        }

        const message = error.hasOwnProperty('type') && error['type'] == 'EMPTY_RESPONSE_DICT' ?
            'ALERT! Another empty response dict. Details follow' + JSON.stringify(error, null, '\t'):
            'ALERT! WhatsApp Controller sprang an error. Error payload as follows:\n\n' + JSON.stringify(error, null, '\t');

        const params = {
            Message: message,
            TopicArn: config.get('error.dlq')
        };

        const publishResult = await snsClient.publish(params).promise();
        console.log('Result of sending to DLQ: ', publishResult);
        return true;
    }
}

const hoursInPast = (number) => {
    var d = new Date();
    d.setHours(d.getHours()-number);
    return d.getTime();
}
