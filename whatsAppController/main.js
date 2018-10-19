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
        'campaign', // i.e., joining a campaign (or a public group, which we include in this domain)
        'service',  // i.e., looking for a service, like a clinic or shelter, etc
        'action'  // i.e., calling a meeting, etc 
    ]

// we use these, which are not like intents, to immediately trigger route into 
const DOMAIN_TRIGGERS = {
    'action': ['act now', 'action', 'frtknx']
}

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
        
        // if there is no content, then just return; if there is an image, stash it
        if (!content || !content.message) {
            console.log('No incoming content or was a status message, exit');
            res.status(200).end();
            return;
        }

        // second, extract a user id, either from prior, or from phone number
        userId = await users.fetchUserId(content['from']);
        console.log('user id: ', userId);

        // third, check for the last message sent, to see if there's a domain
        const lastMessage = await recording.getMostRecent(userId);
        console.log('Raw record of most recent message: ', lastMessage);
        const nonEmptyLastMessage = !!lastMessage && lastMessage.hasOwnProperty('Items') && lastMessage['Items'].length > 0;
        console.log('As processed, non empty last message? : ', nonEmptyLastMessage);

        // fourth, if this has media, retrieve it from WhatsApp and stash it before continuing
        if (api.isMediaType(content['type'])) {
            const mediaFileId = await handleMedia(userId, content, nonEmptyLastMessage ? lastMessage['Items'][0] : null);
            console.log('Returned media file ID: ', mediaFileId);
            content['payload'] = !!mediaFileId ? 'media_record_id::' + mediaFileId : '';
        }
        
        // fifth, get the response from the heart of all this, the NLU/Core engine
        let response = await getMessageReply(content,  nonEmptyLastMessage ? lastMessage['Items'][0] : null, userId);
        console.log('responding: ', response);

        if (noMessagesInResponse(response)) {
            console.log(`Error! Message that is empty, dispatch to DLQ and say something to user`);
            await recording.dispatchToDLQ(noResponseError(response, content, lastMessage));
            response = await conversation.assembleErrorMsg(fallBackUserId, prior['domain'], 'empty');
        }
        
        // log what we are sending back (should move to a separate lambda soon)
        console.time('log_result');
        await recording.logIncoming(content, response, userId);
        console.timeEnd('log_result');

        // last, send the responses back and exit
        await dispatchAndEnd(content, response, res);
        console.timeEnd('full_path');

    } catch (e) {
        console.log('X ########### ERROR: EXIT STARTS HERE ##############');
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

const handleMedia = async (userId, inboundMessage, priorOutbound) => {
    if (!priorOutbound || !priorOutbound['entity']) {
        console.log('Received media, but have no context for it, so just abord');
        return false;
    }

    const assocEntity = utils.extractEntityFromPrior(priorOutbound);
    console.log('Fetching media, associated entity details: ', assocEntity);
    const mediaStoreResponse = await recording.storeInboundMedia(userId, inboundMessage, assocEntity);
    console.log('Response from lambda, raw: ', mediaStoreResponse);
    const payload = JSON.parse(mediaStoreResponse.Payload)
    const messageBody = JSON.parse(payload.body);
    console.log('Extracted payload: ', messageBody);
    return messageBody['media_record_id'];
}

const getMessageReply = async (content, prior, userId) => {
    console.log('User message: ', content['message']);

    // as an initial reference, get an NLU parse, as we'll use it to check for restart, and opening
    console.log('0 ########## Get an initial straight NLU parse for affirmation etc ##########');
    const openingNluResult = await conversation.sendToCore(content, userId, 'opening');
    console.log('Opening NLU result: ', openingNluResult);
    
    // first: check for restart flag - if we have one, send opening message, and tell Rasa service to reset itself
    console.log('1 ########### Checking for restarting ##############');
    const isRestartMsg = await conversation.isRestart(content, openingNluResult);
    if (isRestartMsg) {
        return conversation.restartConversation(userId, !!openingNluResult);
    }

    // second, check if we are in the middle of menus, and a number was provided
    console.log('2 ########### Handling prior menu ##############');
    const priorMessageWasMenu = !!prior && !!prior['menuPayload'] && prior['menuPayload'].length > 0;
    console.log(`Was prior message menu?: ${priorMessageWasMenu} && is message number: ${utils.isMessageMenuNumber(content)}`);
    if (priorMessageWasMenu || utils.isMessageMenuNumber(content)) {
        content = await addSelectedMenuPayloadToMessage(prior, userId, content, openingNluResult);
    }

    // third, if at the start of conversation, or have reset, check for a menu response - if have one, direct based on it; if not, check for join word; else continue
    console.log('3 ########### If at opening, direct accordingly ##############');
    const atOpening = !prior || prior['domain'] == 'opening' || prior['domain'] == 'restart';
    if (atOpening) {
        const possibleReply = await handleFirstMessageInConversation(prior, userId, content, openingNluResult);
        if (possibleReply) return possibleReply; // if there isn't a reply, first let core take a change, before returning 'nothing' 
    }

    // fourth, if we are in platform domain, i.e., entity joins etc., continue
    console.log('4 ########### If within platform, start or direct flow ##############');
    const withinPlatform = prior && prior['domain'] == 'platform';
    if (withinPlatform) {
        const nextFlowStep = await checkForCoherentPlatformResponse(prior, userId, content, openingNluResult);
        if (nextFlowStep) return nextFlowStep
        else console.log('Anomaly! Platform domain but no response, continuing but this should probably be DLQd');
    }
    
    // fifth, ask the Rasa core domain coordinator for a next message / answer, as long as not in platform domain
    // this comes in the form of a dict with 'domain', 'responses', 'intent', 'intent_ranking', and 'entities'
    console.log('5 ########### None of the above resulted in a message, so send to core and return result ##############');
    
    const safeDomain = prior && !!prior['domain'] && !withinPlatform ? prior['domain'] : undefined;
    const coreResult = await conversation.sendToCore(content, userId, safeDomain);    
    console.log('core result: ', coreResult);
    const convertedCoreResult = conversation.convertCoreResult(userId, coreResult); 
    
    console.log(`Reached the end, at opening ? : ${atOpening} and have messages ? : ${noMessagesInResponse(convertedCoreResult)}`);
    if (!noMessagesInResponse(convertedCoreResult) || !atOpening)
        return convertedCoreResult;
    else
        return conversation.openingMsg(userId);
}

const dispatchAndEnd = async (content, response, res) => {
    const sentResult = await (env == 'production' ? api.sendResponse(content['from'], response, res) : 'finished');
    console.log('Sent off result, looks like: ', sentResult);

    if (sentResult == 'dispatched')
        res.status(200).end();
    else
        res.json(response).end();
}

const addSelectedMenuPayloadToMessage = async (prior, userId, content, openingNluResult) => {
    // note: keep this because if menu was a few messages up but user responded with a number, we should fetch it
    const lastMessageHasMenu = !!prior && !!prior['menuPayload'] && prior['menuPayload'].length > 0;
    console.log(`Was it the last message that had the menu? : ${lastMessageHasMenu}`);
    const lastMenu = await (lastMessageHasMenu ? prior['menuPayload'] : recording.getLastMenu(userId));
    console.log('Wherever it came from, the last menu was: ', lastMenu);
    
    const menuOptions = lastMenu.length;
    
    let menuOptionSelected;
    if (utils.isMessageMenuNumber(content)) {
        console.log('User sent us a number, extract it and continue')
        menuOptionSelected = content['message'].match(/\d+/).map(Number);
    } else if (menuOptions == 1) {
        const isIntentAffirmation = conversation.isIntent(openingNluResult, 'affirm');
        console.log(`No number, but we have a menu, so we checked for affirmation and it is : ${isIntentAffirmation}`);
        menuOptionSelected = isIntentAffirmation ? 1 : -1;
    }

    if (menuOptionSelected && menuOptionSelected > 0) {
        const payload = lastMenu[menuOptionSelected - 1];
        console.log(`extracted menu selection: ${menuOptionSelected} and corresponding payload: ${payload}`);
        content['type'] = 'payload';
        content['payload'] = payload;
    } else {
        console.log('Should have got a menu option selected, but for some reason did not');
    }

    return content;
}

const handleFirstMessageInConversation = async (prior, userId, content, openingNluResult) => {
    console.log('Inside opening, restart, or initial conversation, so direct if possible. Content payload: ', content['payload']);
    const likelyDomain = !!content['payload'] ? content['payload'] : checkForTriggerWord(content['message']);
    console.log(`Likely domain: ${likelyDomain} and routing flag: ${!!likelyDomain}`);
    let possibleReply = await (!!likelyDomain ? 
        conversation.getDomainOpening(likelyDomain, userId) : // direct based on a payload - domain map
        platform.checkForJoinPhrase(content['message'], openingNluResult, userId, false)); // look for a join word, narrowly
    console.log('Result of checking for join word or similar phrase: ', possibleReply);
    return possibleReply;
}

const checkForTriggerWord = (userMessage) => {
    const normalized = userMessage.trim().toLowerCase();
    console.log(`Checking if ${normalized} is a trigger word`);
    const platformIndex = Object.keys(DOMAIN_TRIGGERS).find(key => {
        console.log(`Looking for domain ${key} with words ${DOMAIN_TRIGGERS[key]}`);
        return DOMAIN_TRIGGERS[key].findIndex(trigger => userMessage == normalized) != -1;
    });
    console.log('Found a platform for user message? : ', platformIndex);
    return platformIndex;
}

const checkForCoherentPlatformResponse = async (prior, userId, content, openingNluResult) => {
    const priorHasEntity = !!prior['entity'];
    const priorHasPayload = !!prior['menuPayload'] && prior['menuPayload'].length > 0;
    
    console.log(`Inside platform domain, so advance or initiate joining process. Prior has entity? ${priorHasEntity}, and payload: ${priorHasPayload}`);
    
    nextFlowStep = await ((priorHasEntity || priorHasPayload) ? 
        platform.continueJoinFlow(prior, content, userId) :
        platform.checkForJoinPhrase(content['message'], openingNluResult, userId, true)); // look for broad phrase 
    console.log('Got next step in flow: ', nextFlowStep);
    
    return nextFlowStep;
}

const handleErrorFailSafe = async (content, userId) => {
    try {
        const fallBackUserId = !!userId ? userId : 'unknown';
        const fallBackResponse = await conversation.assembleErrorMsg(fallBackUserId, 'restart');

        // if (!!userId) {
            // await conversation.restartConversation(fallBackUserId, true);
            // await recording.logIncoming(content, fallBackResponse, userId);
        // }
        
        console.log('Fall back succeeded, returning it');
        return fallBackResponse;
    } catch (e) {
        console.log('Nice error handling failed. Return failsafe. Within-error error: ', e);
        return conversation.assembleErrorMsg('unknown', 'restart');
    }
}

const noResponseError = (response, content, lastMessage) => {
    let err = new Error('No response to user!');
    err.type = 'EMPTY_RESPONSE_DICT';
    err.lastMessage = lastMessage;
    err.responseDict = response;
    err.inboundContent = content;
    return err;            
}

const noMessagesInResponse = (response) => {
    return !response || !response.replyMessages || response.replyMessages.length == 0
}
