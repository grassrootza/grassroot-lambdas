const express = require('express');
const bodyParser = require('body-parser');

const config = require('config');

const MessagingResponse = require('twilio').twiml.MessagingResponse;

const request = require('request-promise');

const AUTH_TOKEN = 'insert_here_when_have_it';

const AWS = require('aws-sdk');
AWS.config.update({
    region: 'eu-west-1',
});

const conversation = require('./conversation-en.json'); // in time maybe switch to reading this from S3 ...?
const START_WORD = new RegExp(config.get('conversation.startWordRegex')); // in case conversation 'finished', but want to re-initiate

const app = express();
app.use(bodyParser.urlencoded({extended: false}));

const docClient = new AWS.DynamoDB.DocumentClient();

const tasks = require('./tasks.js');

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
        const response = await getMessageReply(content, lastMessage ? lastMessage['Items'][0] : null);
        console.timeEnd('assemble_response');

        if (response) {
            console.time('log_result');
            await logIncoming(content, response);
            console.timeEnd('log_result');
            
            console.timeEnd('full_path');

            res.writeHead(200, {'Content-Type': config.get('response.contentType')});
            res.end(response.body);
        } else {
            res.writeHead(200, {'Content-Type': config.get('response.contentType')});
            res.end(emptyMsgBody());
        }
    } catch (e) {
        console.log('Error: ', e); // todo: stick in a DLQ, or report some other way
        console.log('Gracefully exiting ...');
        const response = await assembleErrorMsg('server');
        res.writeHead(200, {'Content-Type': config.get('response.contentType')});
        console.log('should return: ', response);
        res.end(response.body);
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

interpretMessage = (message_text, conversation_id) => {
    const queryParams = {
        text: message_text
    };

    if (conversation_id)
        queryParams.conversationUid = conversation_id;
    
    const options = {
        url: NLU_URL,
        method: 'GET',
        qs: queryParams
    };

    return request(options);
}

// next, figure out the context, i.e., what happened last, and user's intent, and piece together what to do
// note: since this is stateless, we cannot assume in, e.g., entity completion, that the same method gets hit twice
// with that, we need to check for two main cases:

// (a) Did we just sent the user a message, including from the server? If so, what was it / what is the current context?
// ** Branch 1 : Check if prior message was a task notification. If so, test for affirm/deny, or a vote option. If can't
// ** understand, then prompt for further undersdaning of that option
// ** Branch 2 : Check if prior message had a choose_action context attached to it. If so, check for negation intent, or back up intent.
// ** if not present, then look for next action, and return relevant response
// ** Branch 3 : Check if prior messages had a confirm_intent context attached to it. If so, check for affirmation/negation, and take
// ** appropriate next order action. 
// ** Branch 4 : Check if prior message had an entity_completion context attached to it. If so, check for negation intent, if not present,
// ** check for high confidence on any other intent, if not present, fill slot and continue
// ** Branch 5 : We have completed an entity completion or we have reached the end of an action tree. Call one of the mapped services
// ** and then present a confirmation, and/or the relevant information, to the user
// ** Branch 6 : We just finished a flow, and the user is saying thanks, or similar. Don't respond, but mark last outbound as responded
// ** so if user insists, continue. [think about how to manage this]

// (b) Did we not send the user anything? If so, check for the intent, and proceed:
// ** Branch 1 : We have a high confidence intent with associated fill entities. Just proceed to complete those.
// ** Branch 2 : We have an intermediate confidence intent. Confirm first if this is what user wants. If affirm, then continue. [ ends in a - 3 above? ]
// ** Branch 3 : No idea, or intent is just hello. Initiate with opening messages.
// okay, then done.

const getMessageReply = async (content, prior) => {
    console.time('nlu_call');
    const rawResponse = await interpretMessage(content.message, prior ? prior.conversationId : '');
    const nluResponse = transformNluResponse(rawResponse);
    console.timeEnd('nlu_call');

    if (!prior) {
        console.log('No prior message to user, just return based on NLU understanding');
        return handleNewConversation(nluResponse);
    }

    const context = prior.context;
    const priorType = prior.type;
    
    console.log(`Handling message flow, prior context: ${context}, prior type: ${priorType}`);

    if (priorType.startsWith('outbound') && context.startsWith('task')) {
        // branch 1: handle reply to outbound task, first look at task, and go from there
        const taskType = context.substring('outbound::task::'.length);
        console.log('task type of prior outbound: ', taskType);
        if (nluResponse.intent == 'affirm') {
            tasks.respondToTask();
        } else if (nluResponse.intent == 'negate') {
            tasks.respondToTask();
        } else {
            // probably want this customized in some way
            return assembleErrorMsg('general');
        }
    } else if (priorType.startsWith('choose_action')) {
        // branch 2 : we were picking actions from a menu, so just advance
        return advanceViaOptions(content, prior);
    } else if (priorType.startsWith('confirm_intent')) {
        // branch 3 : we had asked user to confirm that we understood them, so check affirm/negate and continue
    } else if (priorType.startsWith('entity_completion')) {
        // branch 4 : add to entity completion, get next
    
    } else if (priorType.startsWith('complete_journey')) {
        // branch 5 : distribute to some other service and finish

    } else if (priorType.startsWith('confirmation') || priorType.startsWith('error')) {
        // branch 6 : check if the intent is start again, or
    }
    
}

const handleNewConversation = async (nluResponse) => {
    if (nluResponse && nluResponse.confidence && nluResponse.confidence > 0.5) {
        return handleIntent(nluResponse);
    }

    const block = getRelevantConversationBlock('opening');
    const body = getResponseChunk(block, 'start', 0);

    const messages = extractMessages(block, body);

    let reply = {
        body: turnMsgsIntoBody(messages.replyMsgs),
        context: 'open:::id-start',
        replies: messages.replyMsgs
    };

    if (messages.optionsKeys)
        reply.options = messages.optionsKeys;
    
    return reply;
}

const handleIntent = async (nluResponse) => {
    const body = findBlockForIntent(nluResponse.intent)['_source'];
    console.log('here is the body: ', body);
    const replyMsgs  = [body['opening'] + ' ' + body['entities'][0]];
    console.log('messages: ', replyMsgs);
    return {
        body: turnMsgsIntoBody(replyMsgs),
        context: nluResponse.intent
    };
}

const advanceViaOptions = async (content, prior) => {
    // console.log('continuing conversation, prior chat: ', prior);

    if (content.message.match(START_WORD))
        return handleNewConversation(content);

    if (prior['context'] && prior['context'].startsWith('error')) {
        console.log('we have ended the conversation, stop doing anything more');
    } else {
        let optionTriggered = traceOptions(content.message, prior);
        console.log('found option: ', optionTriggered);
        const nextMenu = optionTriggered ? assembleLaterMsg(optionTriggered) : null;
        return nextMenu ? nextMenu : assembleErrorMsg('unimplemented');
    }
}

const traceOptions = (userMessage, priorSent) => {
    const optionKeys = priorSent['options'];
    console.log('prior options: ', optionKeys);
    const digitMatch = /\d+[\W+]?$/
    if (userMessage.match(digitMatch)) {
        console.log('User sent a digit! Extract it and return option key');
        const digit = parseInt(userMessage.replace( /^\D+/g, '')) - 1;
        return optionKeys[digit];
    } else {
        const foundOption = optionKeys.find(option => regexCheckForOption(option, userMessage));
        console.log('found option? : ', foundOption);
        return foundOption;
    }
}

const regexCheckForOption = (option, userMessage) => {
    const regex = new RegExp('(.+)?(' + option + ')(.+)?', 'i');
    return userMessage.match(regex);
}

const assembleLaterMsg = (optionKey) => {
    const block = getRelevantConversationBlock(optionKey);
    if (!block)
        return assembleErrorMsg('unimplemented');

    const body = getResponseChunk(block, 'start', 0);
    if (!body)
        return assembleErrorMsg('unimplemented');

    const messages = extractMessages(block, body);

    let reply = {
        body: turnMsgsIntoBody(messages.replyMsgs),
        context: 'open:::id-1', // next, need to figure this out
        replies: messages.replyMsgs
    };

    if (messages.optionsKeys)
        reply.options = messages.optionsKeys;
    
    return reply;
}

const assembleErrorMsg = async (msgId) => {
    const block = getRelevantConversationBlock('error');
    const body = getResponseChunk(block, msgId, 0);

    const messages = extractMessages(block, body);

    return {
        body: turnMsgsIntoBody(messages.replyMsgs),
        context: 'error::' + msgId,
        replies: messages.replyMsgs
    }
}

const getRelevantConversationBlock = (section) => {
    // will need to look this up from prior, message, etc, for now, just sending generic
    return conversation[section];
}

const findBlockForIntent = (intent) => {
    console.log('hunting for intent: ', intent);
    var returnBlock;
    const conversationSections = Object.keys(conversation).filter(key => key !== 'meta');
    // double iteration (well, tree search), but these are small, and always will be, so in memory will be microseconds
    // also, might be able to do this more elegantly with a find or map, but would still involve that double iteration underneath
    conversationSections.some(section => {
        let blocks = conversation[section];
        blocks.some(block => {
            console.log('checking block: ', block['_id']);
            if (block['_intent'] && block['_intent'] == intent) {
                returnBlock = block;
                return true;
            }
            return false;
        });
        return !!returnBlock;
    });
    console.log('return block: ', returnBlock);
    return returnBlock;
}

const getEntity = (block, id) => {
    // console.log('looking for ' + id + ' in block: ', block);
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

const emptyMsgBody = () => {
    const twiml = new MessagingResponse();
    return twiml.toString();
}

const getMostRecent = (content) => {
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

const transformNluResponse = (raw) => {
    let response = JSON.parse(raw);
    console.log('parsed body: ', response['parsed']);

    let transformed = {
        nlu_id: response['uid'],
        intent: response['parsed']['intent']['name'],
        confidence: response['parsed']['intent']['confidence']
    };

    if (response['entities'])
        transformed['entities'] = response['entities'].map(transformNluEntity);

    return transformed;
}

const transformNluEntity = (entity) => {
    return {
        type: entity['entity'],
        value: entity['value']
    }
}