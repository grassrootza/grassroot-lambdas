const config = require('config');

const express = require('express');
const bodyParser = require('body-parser');
const serverless = require('serverless-http');

const app = express();
app.use(bodyParser.json());

const conversation = require('./conversation/conversation.js'); // for extracting and handling actual text back and forth
const api = require('./api'); // for interacting with WhatsApp API, or whatever intermediary we are using
const nlu = require ('./nlu'); // for calling and using the NLU
const recording = require('./recording'); // for recording messages we send back and conversation flow
const users = require('./users'); //for getting a user ID if not in prior
const platform = require('./platform.js'); // for triggering tasks etc on the backend

const MENU_NUMBER_REGEX = /\d+\W?/;

const DOMAINS = ['opening', 
        'reset', // i.e., user just reset things 
        'platform', // i.e., joining a campaign, responding to a meeting notification, etc
        'service',  // i.e., looking for a service, like a clinic or shelter, etc
        'knowledge'] // i.e., finding knowledge on accountability stack, etc

app.get('/status', (req, res) => {
    res.send(`I am alive! My environment: ${process.env.NODE_ENV} and config is: ${config.get('__comment.whoami')}`);
});

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
        // await recording.logIncoming(content, response, userId);
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

    // first: check for restart flag - if we have one, send opening message, and tell Rasa service to reset itself
    if (content['type'] == 'text' && content['message'].toLowerCase() == 'reset') {
        // send out the resets etc
        return conversation.openingMsg(userId, 'reset');
    }

    // second, if at the start, or have reset, check for a campaign or group join word, and if so, initiate that flow
    if (!prior || prior['domain'] == 'reset') {
        possibleReply = newSession(content['message'], userId);
        console.log('possibly reply based on platform: ', possibleReply);
        if (possibleReply) return possibleReply;        
    }
    
    // third, check if we are in the middle of menus, and a number was provided
    console.log(`prior : ${!!prior} && prior menu: ${prior && prior['menu']} && is message number: ${isMessageNumber(content)}`);
    const replyingToMenu = !!prior && !!prior['menu'] && isMessageNumber(content);
    if (replyingToMenu) {
        content = reshapeContentFromMenu(content, prior);        
    }

    // fourth, ask the Rasa core domain coordinator for a next message / answer
    // this comes in the form of a dict with 'domain', 'responses', 'intent', 'intent_ranking', and 'entities'
    const coreResult = await conversation.sendToCore(content, userId, prior ? prior['domain'] : undefined);    
    console.log('core result: ', coreResult);

    // 4-a : check if core result intent is 'reset' (but was not caught in simple check above), in which case, reset
    if (coreResult.hasOwnProperty('intent') && coreResult['intent'] == 'reset') {
        return conversation.openingMsg(userId, 'reset');
    }

    // fifth, if there is no text reply, extract one from the domain openings, and return
    if (!hasSomethingInside(coreResult['responses'])) {
        return conversation.openingMsg(userId, coreResult['domain'])
    };

    // // maybe:: if we have a finished intent and entity, call corresponding service and exit

    // else, return a response, recoded to our format
    return conversation.convertCoreResult(userId, coreResult);
}

const newSession = async (userMessage, userId) => {
    let checkForJoinWord = await platform.checkForJoinPhrase(userMessage, userId);
    console.log('platform check result: ', checkForJoinWord);
    if (checkForJoinWord && checkForJoinWord['entityFound']) {
        console.log('well we found something');
        let reply = conversation.Reply(userId, 'platform', checkForJoinWord['responseMessages']);
        if (checkForJoinWord.hasOwnProperty('responseMenu')) {
            const menu = checkForJoinWord['responseMenu'];
            reply['menu'] = Object.keys(menu);
        }
        reply['entity'] = checkForJoinWord['entityType'] + '::' + checkForJoinWord['entityUid'];
        return reply;
    } else {
        return false;
    }
}

const reshapeContentFromMenu = (content, prior) => {
    console.log('extracting menu selection ... boolean check: ', /\d+/.test(content['message']));
    const menuSelected = content['message'].match(/\d+/).map(Number);
    const payload = prior['menu'][menuSelected - 1];
    console.log(`extracted menu selection: ${menuSelected} and corresponding payload: ${payload}`);
    content['type'] = 'payload';
    content['payload'] = payload;
    return content;
}

const hasSomethingInside = (entity) => {
    return typeof entity !== 'undefined' && entity !== null && entity.length > 0
}

const isMessageNumber = (inboundMsg) => {
    return inboundMsg && inboundMsg['type'] && inboundMsg['type'] == 'text' && MENU_NUMBER_REGEX.test(inboundMsg['message']);
}