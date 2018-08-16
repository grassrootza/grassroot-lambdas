const express = require('express');
const bodyParser = require('body-parser');

const app = express();
app.use(bodyParser.urlencoded({extended: false}));

const conversation = require('./conversation/conversation.js'); // for extracting and handling actual text back and forth
const api = require('./api'); // for interacting with WhatsApp API, or whatever intermediary we are using
const nlu = require ('./nlu'); // for calling and using the NLU
const recording = require('./recording'); // for recording messages we send back and conversation flow
const tasks = require('./tasks'); // for triggering tasks etc on the backend

app.post('/inbound', async (req, res, next) => {
    try {
        console.time('full_path');

        const content = api.getMessageContent(req);
        const lastMessage = await recording.getMostRecent(content);

        const response = await getMessageReply(content, lastMessage ? lastMessage['Items'][0] : null);
        
        console.time('log_result');
        await recording.logIncoming(content, response);
        console.timeEnd('log_result');

        await api.sendResponse(response, res);
        console.timeEnd('full_path');

    } catch (e) {
        console.log('Error: ', e); // todo: stick in a DLQ, or report some other way
        await api.sendResponse(assembleErrorMsg('server'), res);
        console.log('Gracefully exited, hopefully');
    }

});

// module.exports = app;
app.listen(3000, () => console.log(`Listening on port 3000`));


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
    const rawResponse = await nlu.interpretMessage(content.message, prior ? prior.conversationId : '');
    const nluResponse = nlu.transformNluResponse(rawResponse);
    
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

    const block = conversation.getRelevantConversationBlock('opening');
    const body = conversation.getResponseChunk(block, 'start', 0);

    const messages = conversation.extractMessages(block, body);

    let reply = {
        context: 'open:::id-start',
        replies: messages.replyMsgs
    };

    if (messages.optionsKeys)
        reply.options = messages.optionsKeys;
    
    return reply;
}

const handleIntent = async (nluResponse) => {
    const body = conversation.findBlockForIntent(nluResponse.intent)['_source'];
    const replyMsgs  = [body['opening'] + ' ' + body['entities'][0]];
    return {
        replies: replyMsgs,
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
    const block = conversation.getRelevantConversationBlock(optionKey);
    if (!block)
        return conversation.assembleErrorMsg('unimplemented');

    const body = conversation.getResponseChunk(block, 'start', 0);
    if (!body)
        return conversation.assembleErrorMsg('unimplemented');

    const messages = conversation.extractMessages(block, body);

    let reply = {
        context: 'open:::id-1', // next, need to figure this out
        replies: messages.replyMsgs
    };

    if (messages.optionsKeys)
        reply.options = messages.optionsKeys;
    
    return reply;
}

