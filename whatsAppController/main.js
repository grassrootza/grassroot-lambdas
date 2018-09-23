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

const stringSimilarity = require('string-similarity');

// not used but recording for reference
const DOMAINS = ['opening', 
        'retart', // i.e., user just reset things 
        'platform', // i.e., joining a campaign, responding to a meeting notification, etc
        'service',  // i.e., looking for a service, like a clinic or shelter, etc
        'knowledge'] // i.e., finding knowledge on accountability stack, etc

app.get('/status', (req, res) => {
    res.send(`I am alive! My environment: ${process.env.NODE_ENV} and config is: ${config.get('__comment.whoami')}`);
});

app.get('/similarity', (req, res) => {
    res.status(200).json(stringSimilarity.compareTwoStrings(req.query.s1, req.query.s2)).end();
})

app.post('/inbound', async (req, res, next) => {
    try {
        console.time('full_path');
        
        // first, decode the inbound message into a content object we can use
        const content = api.getMessageContent(req);
        console.log('incoming content: ', content);
        if (!content) {
            res.status(200).end();
            return;
        }

        // second, extract a user id, either from prior, or from phone number
        const userId = await users.fetchUserId(content['from']);
        console.log('user id: ', userId);

        // third, check for the last message sent, to see if there's a domain
        const lastMessage = await recording.getMostRecent(userId);
        console.log('and most recent message: ', lastMessage);

        // fourth, get the response from NLU
        const response = await getMessageReply(content, (typeof lastMessage !== 'undefined' && lastMessage) ? lastMessage['Items'][0] : null, userId);
        console.log('responding: ', response);
        
        // log what we are sending back (should move to a separate lambda soon)
        console.time('log_result');
        await recording.logIncoming(content, response, userId);
        console.timeEnd('log_result');

        // last, send the responses back
        // const sentResult = await api.sendResponse(content['from'], response, res);
        const sentResult = 'finished';
        console.log('Sent off result, looks like: ', sentResult);

        if (sentResult == 'dispatched') {
            res.status(200).end();
        } else {
            res.json(response).end();
        }
        console.timeEnd('full_path');

    } catch (e) {
        console.log('Error: ', e); // todo: stick in a DLQ, or report some other way
        // await api.sendResponse(conversation.assembleErrorMsg('server'), res);
        console.log('Gracefully exited, hopefully');
        res.status(200).end(); // have to do this, otherwise W/A will keep delivering in loop forever
    }

});

module.exports.handler = serverless(app);
app.listen(3000, () => console.log(`Listening on port 3000`));

const getMessageReply = async (content, prior, userId) => {
    console.log('User message: ', content['message']);

    // as an initial reference, get an NLU parse, as we'll use it to check for restart, and opening
    const openingNluResult = await conversation.sendToCore(content, userId, 'opening');

    // first: check for restart flag - if we have one, send opening message, and tell Rasa service to reset itself
    const isRestartMsg = await conversation.isRestart(content, openingNluResult);
    if (isRestartMsg) {
        return conversation.restartConversation(userId, !!openingNluResult);
    }

    // second, if at the start, or have reset, check for a campaign or group join word, and if so, initiate that flow; if in that flow, continue it
    // todo : use NLU again to check for 'join' or other such intent
    if (!prior || prior['domain'] == 'restart') {
        possibleReply =  await platform.checkForJoinPhrase(content['message'], openingNluResult, userId);
        console.log('Result of checking for join word or similar phrase: ', possibleReply);
        if (possibleReply) return possibleReply;   
    }

    // third, check if we are in the middle of menus, and a number was provided
    console.log(`prior : ${!!prior} && prior menu: ${prior && prior['menuPayload']} && is message number: ${utils.isMessageNumber(content)}`);
    if (utils.isMessageNumber(content)) {
        let lastMenu;
        // await is not playing nicely with ternary operator, hence this
        if (prior && prior['menuPayload'])
            lastMenu = prior['menuPayload'];
        else
            lastMenu = await recording.getLastMenu(userId);
        console.log('last menu = ', lastMenu);
        content = utils.reshapeContentFromMenu(content, lastMenu);
    }

    // fourth, if we are in platform domain, i.e., entity joins etc., continue
    if (prior['domain'] == 'platform' && prior.hasOwnProperty('entity')) {
        nextFlowStep = await platform.continueJoinFlow(prior, content, userId);
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

const hasSomethingInside = (entity) => {
    return typeof entity !== 'undefined' && entity !== null && entity.length > 0
}