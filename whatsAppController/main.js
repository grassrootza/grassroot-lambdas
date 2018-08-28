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

        console.log('incoming request: ', req);
        const content = api.getMessageContent(req);
        const lastMessage = await recording.getMostRecent(content);

        const response = await getMessageReply(content, lastMessage ? lastMessage['Items'][0] : null);
        
        console.time('log_result');
        await recording.logIncoming(content, response);
        console.timeEnd('log_result');

        // await api.sendResponse(response, res);
        res.json(response).send();
        console.timeEnd('full_path');

    } catch (e) {
        console.log('Error: ', e); // todo: stick in a DLQ, or report some other way
        await api.sendResponse(conversation.assembleErrorMsg('server'), res);
        console.log('Gracefully exited, hopefully');
    }

});

// module.exports = app;
app.listen(3000, () => console.log(`Listening on port 3000`));

const getMessageReply = async (content, prior) => {
    
    // first, extract a user id, either from prior, or from phone number
    let userId;
    if (prior && prior['userId']) {
        userId = prior['userId'];
    } else {
        // userId = users.fetchUserId(content['from'])
        userId = '1234';
    }

    console.log('User message: ', content['message']);

    // second, ask the Rasa core domain coordinator for a next message / answer
    const coreResult = conversation.sendToCore(content['message'], userId, 'knowledge');
    console.log('core result: ', coreResult);
    
    // third, if we have a finished intent and entity, call corresponding service and exit


    // else, return a response
    return coreResult;
}