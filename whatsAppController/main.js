const express = require('express');
const bodyParser = require('body-parser');

const app = express();
app.use(bodyParser.urlencoded({extended: false}));

const conversation = require('./conversation/conversation.js'); // for extracting and handling actual text back and forth
const api = require('./api'); // for interacting with WhatsApp API, or whatever intermediary we are using
const nlu = require ('./nlu'); // for calling and using the NLU
const recording = require('./recording'); // for recording messages we send back and conversation flow
const users = require('./users'); //for getting a user ID if not in prior
const tasks = require('./tasks'); // for triggering tasks etc on the backend

app.post('/inbound', async (req, res, next) => {
    try {
        console.time('full_path');
        
        // first, decode the inbound message into a content object we can use
        const content = api.getMessageContent(req);

        // second, extract a user id, either from prior, or from phone number
        const userId = await users.fetchUserId(content['from']);

        // third, check for the last message sent, to see if there's a domain
        const lastMessage = await recording.getMostRecent(content);

        // fourth, get the response from NLU
        const response = await getMessageReply(content, lastMessage ? lastMessage['Items'][0] : null, userId);

        // log what we are sending back (should move to a separate lambda soon)
        console.time('log_result');
        await recording.logIncoming(content, response, userId);
        console.timeEnd('log_result');

        // last, send the responses back
        const sentResult = await api.sendResponse(response, res);
        console.log('Sent off result, looks like: ', sentResult);

        if (sentResult == 'dispatched') {
            res.json(response).send();
        }
        console.timeEnd('full_path');

    } catch (e) {
        console.log('Error: ', e); // todo: stick in a DLQ, or report some other way
        await api.sendResponse(conversation.assembleErrorMsg('server'), res);
        console.log('Gracefully exited, hopefully');
    }

});

// module.exports = app;
app.listen(3000, () => console.log(`Listening on port 3000`));

const getMessageReply = async (content, prior, userId) => {
    
    console.log('User message: ', content['message']);

    // second, ask the Rasa core domain coordinator for a next message / answer
    const coreResult = await conversation.sendToCore(content['message'], userId);
    console.log('core result: ', coreResult);

    console.log('core result first item: ', coreResult[0]);


    // third, if we have a finished intent and entity, call corresponding service and exit

    // else, return a response, recoded to our format
    return conversation.Reply(userId, coreResult.map(action => action.text));
}