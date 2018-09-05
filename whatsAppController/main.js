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

app.get('/status', (req, res) => {
    res.send('I am alive!');
});

app.post('/inbound', async (req, res, next) => {
    try {
        console.time('full_path');
        
        // first, decode the inbound message into a content object we can use
        const content = api.getMessageContent(req);
        console.log('incoming content: ', content);

        // second, extract a user id, either from prior, or from phone number
        const userId = await users.fetchUserId(content['from']);
        console.log('user id: ', userId);

        // third, check for the last message sent, to see if there's a domain
        const lastMessage = await recording.getMostRecent(content);
        console.log('and most recent message: ', lastMessage);

        // fourth, get the response from NLU
        const response = await getMessageReply(content, (typeof lastMessage !== 'undefined' && lastMessage) ? lastMessage['Items'][0] : null, userId);
        console.log('responding: ', response);
        
        // log what we are sending back (should move to a separate lambda soon)
        // console.time('log_result');
        // await recording.logIncoming(content, response, userId);
        // console.timeEnd('log_result');

        // last, send the responses back
        // const sentResult = await api.sendResponse(response, res);
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

// module.exports.handler = serverless(app);
app.listen(3000, () => console.log(`Listening on port 3000`));

const getMessageReply = async (content, prior, userId) => {
    
    console.log('User message: ', content['message']);

    // start (?) with a regex check on campaign and group join words, and/or locations and other media, and/or restart

    // first, ask the Rasa core domain coordinator for a next message / answer
    // this comes in the form of a dict with 'domain', 'responses', 'intent', 'intent_ranking', and 'entities'
    const coreResult = await conversation.sendToCore(content, userId, prior ? prior['domain'] : undefined);
    
    console.log('core result: ', coreResult);

    // second, if there is no text reply, extract one from the domain openings, and return
    if (!hasSomethingInside(coreResult['responses'])) {
        return conversation.openingMsg(userId, coreResult['domain'])
    };

    // third, if there is text reply, assemble it into a response and send back


    // fourth, if we have a finished intent and entity, call corresponding service and exit

    // else, return a response, recoded to our format
    let responses = coreResult['responses'].map(response => response['text']);
    return conversation.Reply(userId, coreResult['domain'], responses);
}

const hasSomethingInside = (entity) => {
    return typeof entity !== 'undefined' && entity !== null && entity.length > 0
}