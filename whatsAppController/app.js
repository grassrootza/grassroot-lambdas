const express = require('express');
const bodyParser = require('body-parser');

const MessagingResponse = require('twilio').twiml.MessagingResponse;

const request = require('request-promise');

const AWS = require('aws-sdk');

const conversation = require('./conversation-en.json'); // in time maybe switch to reading this from S3 ...?

AWS.config.update({
    region: "eu-west-1",
});

const app = express();
app.use(bodyParser.urlencoded({extended: false}));

const docClient = new AWS.DynamoDB.DocumentClient();

app.post('/inbound', (req, res) => {
    const content = getMessageContent(req);
    console.log('decoded content: ', content); 

    const response = getMessageReply(content);
    console.log('created response: ', response);

    logIncoming(content, response).then((data, error) => {
        if (error)
            console.log('aargh, error: ', error);
        else
            console.log('logged, result: ', data);

        res.writeHead(200, {'Content-Type': response.content_type});
        res.end(response.body);
    }).catch(error => {
        console.log('failure in dynamo db write: ', error);
    });
});

// module.exports = app;
app.listen(3000, () => console.log(`Listening on port 3000`));

// we will be swapping these out in future, as possibly / probably not using Twilio, so stashing them

const getMessageContent = (req) => {
    console.log('message body: ', req.body);
    const incoming_text = req.body['Body'];
    const incoming_phone = req.body['From'] ? req.body['From'].substring('whatsapp:+'.length) : '<<Error>>';
    return {
        'message': incoming_text,
        'from': incoming_phone
    };
}

// next, figure out the context, i.e., what happened last
// options: (1) user was sent a notification from platform; (2) user is in middle of conversation; (3) message out of the blue
// if (1), then need to know possible options on the reply, e.g., just affirmation / denial, or vote options;
// if (2), then need to know a conversation ID from NLU, and last stage in progression through menu options
// if (3), then need to know if brand new user, or returning user, and hence whether to include a greeting

const getMessageReply = (content) => {
    const twiml = new MessagingResponse();

    // note: message ordering seems pretty unpredictable here
    let replyMsgs = [];
    
    const block = getRelevantConversationBlock(content);
    const body = getResponseChunk(block, "1", 0);

    const optionsRegex = /<<OPTIONS::\w+>>/g;
    var optionsKeys;
    
    body.forEach(msgComponent => {
        if (optionsRegex.test(msgComponent)) {
            const optionsId = msgComponent.substring('<<OPTIONS::'.length, msgComponent.length - 2);
            console.log('extracted options src: ', optionsId); 
            let optionsEntity = getEntity(block, optionsId);
            optionsKeys = extractOptionsKeys(optionsEntity); // may be randomly sorted in future
            replyMsgs.push(extractOptionDescriptions(optionsEntity, optionsKeys).join('\n'));
        } else {
            replyMsgs.push(msgComponent);
        }
    })
    
    replyMsgs.forEach(msg => twiml.message(msg));

    let reply = {
        content_type: 'text/xml',
        body: twiml.toString(),
        context: 'open:::id-1',
        replies: replyMsgs
    };

    if (optionsKeys)
        reply.options = optionsKeys;
    
    return reply;
}

const getRelevantConversationBlock = (content) => {
    // will need to look this up from prior, message, etc, for now, just sending generic
    return conversation.opening;
}

const getEntity = (block, id) => {
    return block.find(item => item._id == id);
}

const getResponseChunk = (block, id, variant = 0) => {
    return getEntity(block, id)._source.response[variant];
}

const extractOptionDescriptions = (optionsEntity, sortedKeys) => {
    return sortedKeys.map((key, index) => (index + 1) + '. ' + optionsEntity._source[key].trim());
}

// may want to randomize this in future, and if so, will want to be sure this is same as above
const extractOptionsKeys = (optionsEntity) => {
    return Object.keys(optionsEntity._source);
}

const logIncoming = (content, reply) => {
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

    return docClient.put(params).promise();
}