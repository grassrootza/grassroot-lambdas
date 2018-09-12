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
const tasks = require('./tasks'); // for triggering tasks etc on the backend

const MENU_NUMBER_REGEX = /\d+\W?/;

app.get('/status', (req, res) => {
    res.send('I am alive!');
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
        await recording.logIncoming(content, response, userId);
        console.timeEnd('log_result');

        // // last, send the responses back
        // // const sentResult = await api.sendResponse(response, res);
        const sentResult = 'finished';
        // // console.log('Sent off result, looks like: ', sentResult);

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

// module.exports.handler = serverless(app);
app.listen(3000, () => console.log(`Listening on port 3000`));

const getMessageReply = async (content, prior, userId) => {
    console.log('User message: ', content['message']);
    
    // first, check if we are in the middle of menus, and a number was provided
    console.log(`prior : ${!!prior} && prior menu: ${prior && prior['menu']} && is message number: ${isMessageNumber(content)}`);
    const replyingToMenu = !!prior && !!prior['menu'] && isMessageNumber(content);
    console.log('replying to menu: ', replyingToMenu); 
    if (replyingToMenu) {
        console.log('extracting menu selection ... boolean check: ', /\d+/.test(content['message']));

        const menuSelected = content['message'].match(/\d+/).map(Number);
        const payload = prior['menu'][menuSelected - 1];
        console.log(`extracted menu selection: ${menuSelected} and corresponding payload: ${payload}`);
        
        content['type'] = 'payload';
        content['payload'] = payload;
    }

    // next, do a regex check on campaign and group join words, and/or locations and other media, and/or restart

    // third, ask the Rasa core domain coordinator for a next message / answer
    // this comes in the form of a dict with 'domain', 'responses', 'intent', 'intent_ranking', and 'entities'
    const coreResult = await conversation.sendToCore(content, userId, prior ? prior['domain'] : undefined);
    
    // console.log('core result: ', coreResult);

    // // fourth, if there is no text reply, extract one from the domain openings, and return
    // if (!hasSomethingInside(coreResult['responses'])) {
    //     return conversation.openingMsg(userId, coreResult['domain'])
    // };

    // // maybe:: if we have a finished intent and entity, call corresponding service and exit

    // else, return a response, recoded to our format
    return conversation.convertCoreResult(userId, coreResult);
}

const hasSomethingInside = (entity) => {
    return typeof entity !== 'undefined' && entity !== null && entity.length > 0
}

const isMessageNumber = (inboundMsg) => {
    return inboundMsg && inboundMsg['type'] && inboundMsg['type'] == 'text' && MENU_NUMBER_REGEX.test(inboundMsg['message']);
}