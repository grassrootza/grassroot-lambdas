const express = require('express');
const bodyParser = require('body-parser');

const RESPONSE_CONTENT_TYPE = 'text/xml';
const MessagingResponse = require('twilio').twiml.MessagingResponse;

const request = require('request-promise');
const NLU_URL = 'http://learning-staging.eu-west-1.elasticbeanstalk.com/parse';

const AWS = require('aws-sdk');

const conversation = require('./conversation-en.json'); // in time maybe switch to reading this from S3 ...?

const CUTOFF_CONVERSATION_LENGTH = 1; // in hours; in production, will adjust to 3, leaving 1 here for easier testing

AWS.config.update({
    region: "eu-west-1",
});

const app = express();
app.use(bodyParser.urlencoded({extended: false}));

const docClient = new AWS.DynamoDB.DocumentClient();

app.post('/inbound', async (req, res, next) => {
    try {
        console.time('full_path');

        const content = getMessageContent(req);
        console.log('decoded content: ', content); 

        console.time('get_prior');
        const lastMessage = await getMostRecent(content);
        // console.log('last message: ', lastMessage);
        console.timeEnd('get_prior');

        console.time('assemble_response');
        const response = await getMessageReply(content, lastMessage['Items'][0]);
        console.timeEnd('assemble_response');

        if (response) {
            console.time('log_result');
            await logIncoming(content, response);
            console.timeEnd('log_result');

            console.timeEnd('full_path');

            res.writeHead(200, {'Content-Type': RESPONSE_CONTENT_TYPE});
            res.end(response.body);
        } else {
            res.status(200).end();
        }
    } catch (e) {
        next(e);
    }

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

interpretMessage = (message_text) => {
    const options = {
        url: NLU_URL,
        method: 'GET',
        qs: {
            text: message_text
        }
    };

    return request(options);
}

// next, figure out the context, i.e., what happened last
// options: (1) user was sent a notification from platform; (2) user is in middle of conversation; (3) message out of the blue
// if (1), then need to know possible options on the reply, e.g., just affirmation / denial, or vote options;
// if (2), then need to know a conversation ID from NLU, and last stage in progression through menu options
// if (3), then need to know if brand new user, or returning user, and hence whether to include a greeting

const getMessageReply = async (content, prior) => {
    if (prior) {
        console.log('Have prior message: ', prior);
        return continueConversation(content, prior);
    } else {
        return handleNewConversation(content);
    }
    
    // console.time('nlu_call');
    // const thisMsgInterpred = await interpretMessage(content.message);
    // // console.log('message interepreted: ', thisMsgInterpred);
    // console.timeEnd('nlu_call');

    // console.log('interpreted msg: ', thisMsgInterpred);
}

const handleNewConversation = async (content) => {
    // note: message ordering seems pretty unpredictable here
    const block = getRelevantConversationBlock('opening');
    const body = getResponseChunk(block, "1", 0);

    const messages = extractMessages(block, body);

    let reply = {
        body: turnMsgsIntoBody(messages.replyMsgs),
        context: 'open:::id-1',
        replies: messages.replyMsgs
    };

    if (messages.optionsKeys)
        reply.options = messages.optionsKeys;
    
    return reply;
}

const continueConversation = async (content, prior) => {
    console.log('prior: ', prior);
    if (prior['context'] == 'error::id-unimplemented') {
        console.log('we have ended the conversation, stop doing anything more');
    } else {
        return assembleErrorMsg('unimplemented');
    }
}

const assembleErrorMsg = async (msgId) => {
    const block = getRelevantConversationBlock('error');
    const body = getResponseChunk(block, msgId, 0);

    const messages = extractMessages(block, body);

    return {
        body: turnMsgsIntoBody(messages.replyMsgs),
        context: 'error::id-unimplemented',
        replies: messages.replyMsgs
    }
}

const getRelevantConversationBlock = (section) => {
    // will need to look this up from prior, message, etc, for now, just sending generic
    return conversation[section];
}

const getEntity = (block, id) => {
    console.log('looking for ' + id + ' in block: ', block);
    return block.find(item => item._id == id);
}

const getResponseChunk = (block, id, variant = 0) => {
    return getEntity(block, id)._source.response[variant];
}

const extractMessages = (block, body) => {
    let replyMsgs = [];

    const optionsRegex = /<<OPTIONS::\w+>>/g;
    var optionsKeys;

    // console.log('body: ', body);
    console.log('body length: ', body.length);
    
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

    return {
        replyMsgs: replyMsgs,
        optionsKeys: optionsKeys
    };
}

const extractOptionDescriptions = (optionsEntity, sortedKeys) => {
    return sortedKeys.map((key, index) => (index + 1) + '. ' + optionsEntity._source[key].trim());
}

// may want to randomize this in future, and if so, will want to be sure this is same as above
const extractOptionsKeys = (optionsEntity) => {
    return Object.keys(optionsEntity._source);
}

const turnMsgsIntoBody = (replyMsgs) => {
    const twiml = new MessagingResponse();
    replyMsgs.forEach(msg => twiml.message(msg));
    return twiml.toString();
}

const getMostRecent = (content) => {
    const userMsisdn = content.from;
    const cutoff = hoursInPast(3);
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

const hoursInPast = (number) => {
    var d = new Date();
    d.setHours(d.getHours()-number);
    return d.getTime();
}