const config = require('config');

const express = require('express');
const bodyParser = require('body-parser');
const serverless = require('serverless-http');

const app = express();
app.use(bodyParser.json());

const conversation = require('./conversation/conversation.js'); // for extracting and handling actual text back and forth
const api = require('./api'); // for interacting with WhatsApp API, or whatever intermediary we are using
const recording = require('./recording'); // for recording messages we send back and conversation flow
const users = require('./users'); //for getting a user ID if not in prior
const platform = require('./platform.js'); // for triggering tasks etc on the backend
const utils = require('./utils');

var env = process.env.NODE_ENV || 'dev';

// not used but recording for reference
const DOMAINS = ['opening', 
        'retart', // i.e., user just reset things 
        'platform', // i.e., joining a campaign, responding to a meeting notification, etc
        'service',  // i.e., looking for a service, like a clinic or shelter, etc
        'knowledge'] // i.e., finding knowledge on accountability stack, etc

app.get('/status', (req, res) => {
    res.send(`I am alive! My environment: ${env} and config is: ${config.get('__comment.whoami')}, 
        and my core url is: ${config.get('core.url.base')}`);
});

app.post('/inbound', async (req, res, next) => {
    let userId;
    let content;
    try {
        console.time('full_path');
        
        // first, decode the inbound message into a content object we can use
        content = api.getMessageContent(req);
        console.log('incoming content: ', content);
        if (!content || !content.message) {
            throw noContentError(req);
        }

        // second, extract a user id, either from prior, or from phone number
        userId = await users.fetchUserId(content['from']);
        console.log('user id: ', userId);

        // third, check for the last message sent, to see if there's a domain
        const lastMessage = await recording.getMostRecent(userId);
        console.log('and most recent message: ', lastMessage);

        // fourth, get the response from the heart of all this, the NLU/Core engine
        const response = await getMessageReply(content, (typeof lastMessage !== 'undefined' && lastMessage) ? lastMessage['Items'][0] : null, userId);
        console.log('responding: ', response);

        if (!response || !response.replyMessages || response.replyMessages.length == 0) {
            throw noResponseError(response, content, lastMessage);
        }
        
        // log what we are sending back (should move to a separate lambda soon)
        console.time('log_result');
        await recording.logIncoming(content, response, userId);
        console.timeEnd('log_result');

        // last, send the responses back and exit
        await dispatchAndEnd(content, response, res);
        console.timeEnd('full_path');

    } catch (e) {
        console.log('Error: ', e);
        console.log('Inside error, do we have a userId: ? ', userId);
        await recording.dispatchToDLQ(e); // to make sure it gets dispatched, whatever happens
        const fallBackResponse = await handleErrorFailSafe(content, userId);
        await dispatchAndEnd(content, fallBackResponse, res);
        console.log('Gracefully exited, hopefully');
    }

});

module.exports.handler = serverless(app);

if (env !== 'production') {
    app.listen(3000, () => console.log(`Listening on port 3000`));
}

const getMessageReply = async (content, prior, userId) => {
    console.log('User message: ', content['message']);

    // as an initial reference, get an NLU parse, as we'll use it to check for restart, and opening
    // const openingNluResult = await conversation.sendToCore(content, userId, 'opening');
    const openingNluResult = null;

    // first: check for restart flag - if we have one, send opening message, and tell Rasa service to reset itself
    const isRestartMsg = await conversation.isRestart(content, openingNluResult);
    if (isRestartMsg) {
        return conversation.restartConversation(userId, !!openingNluResult);
    }

    // second, check if we are in the middle of menus, and a number was provided
    console.log(`prior : ${!!prior} && prior menu: ${prior && prior['menuPayload']} && is message number: ${utils.isMessageMenuNumber(content)}`);
    if (utils.isMessageMenuNumber(content)) {
        const lastMessageHasMenu = !!prior && !!prior['menuPayload'] && prior['menuPayload'].length > 0;
        console.log(`last message contained menu? : ${lastMessageHasMenu}`);
        const lastMenu = await (lastMessageHasMenu ? prior['menuPayload'] : recording.getLastMenu(userId));
        console.log('last menu = ', lastMenu);
        content = utils.reshapeContentFromMenu(content, lastMenu);
        console.log('reshaped response: ', content);
    }

    // third, if at the start, or have reset, check for a menu response - if have one, direct based on it; if not, check for join word; else continue
    if (!prior || prior['domain'] == 'opening' || prior['domain'] == 'restart') {
        console.log('Inside opening, restart, or initial conversation, so direct if possible');
        let possibleReply = await (!!content ['payload'] ? 
            conversation.directToDomain(content, userId, prior) : // direct based on a payload - domain map
            platform.checkForJoinPhrase(content['message'], openingNluResult, userId)); // look for a join word
        console.log('Result of checking for join word or similar phrase: ', possibleReply);
        if (possibleReply) return possibleReply;   
    }

    // fourth, if we are in platform domain, i.e., entity joins etc., continue
    if (prior && prior['domain'] == 'platform') {
        nextFlowStep = await (prior.hasOwnProperty('entity') ? 
            platform.continueJoinFlow(prior, content, userId) :
            platform.checkForJoinPhrase(content['message'], openingNluResult, userId)); 
        console.log('Got next step in flow: ', nextFlowStep);
        if (nextFlowStep) return nextFlowStep;
    }
    
    // fifth, ask the Rasa core domain coordinator for a next message / answer
    // this comes in the form of a dict with 'domain', 'responses', 'intent', 'intent_ranking', and 'entities'
    const coreResult = await conversation.sendToCore(content, userId, prior ? prior['domain'] : undefined);    
    console.log('core result: ', coreResult);

    // fifth, if there is no text reply, extract one from the domain openings, and return
    if (!hasSomethingInside(coreResult['responses'])) {
        return conversation.openingMsg(userId, coreResult['domain'])
    };

    // // maybe:: if we have a finished intent and entity, call corresponding service and exit

    // else, return a response, recoded to our format
    return conversation.convertCoreResult(userId, coreResult);
}

const dispatchAndEnd = async (content, response, res) => {
    const sentResult = await (env == 'production' ? api.sendResponse(content['from'], response, res) : 'finished');
    console.log('Sent off result, looks like: ', sentResult);

    if (sentResult == 'dispatched') {
        res.status(200).end();
    } else {
        res.json(response).end();
    }
}

const handleErrorFailSafe = async (content, userId) => {
    try {
        const fallBackUserId = !!userId ? userId : 'unknown';
        const fallBackResponse = await conversation.assembleErrorMsg(fallBackUserId, 'restart');

        if (!!userId) {
            await conversation.restartConversation(fallBackUserId, true);
            await recording.logIncoming(content, fallBackResponse, userId);
        }
        
        console.log('Fall back succeeded, returning it');
        return fallBackResponse;
    } catch (e) {
        console.log('Nice error handling failed. Return failsafe. Within-error error: ', e);
        return conversation.assembleErrorMsg('unknown', 'restart');
    }
}

const noContentError = (req) => {
    let err = new Error('No content in body!');
    err.inboundRequest = req;
    return err;
}

const noResponseError = (response, content, lastMessage) => {
    let err = new Error('No response to user!');
    err.responseDict = response;
    err.inboundContent = content;
    err.lastMessage = lastMessage;
    return err;            
}

const hasSomethingInside = (entity) => {
    return typeof entity !== 'undefined' && entity !== null && entity.length > 0
}