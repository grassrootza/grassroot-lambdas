// module for handling calls to main platform for task creation and response (task = meeting, vote, action, livewire alert, etc)
const config = require('config');
const request = require('request-promise');
const conversation = require('./conversation/conversation.js');
const utils = require('./utils.js');

const authHeader = {
    'bearer': config.get('auth.platform')
};

var exports = module.exports = {};

exports.checkForJoinPhrase = async (incomingPhrase, userId) => {
    const options = {
        method: 'POST',
        uri: config.get('platform.url') + config.get('platform.paths.phrase.search'),
        qs: {
            'phrase': incomingPhrase,
            'userId': userId
        },
        auth: authHeader,
        json: true
    };

    const phraseSearchResult = await request(options);

    if (phraseSearchResult && phraseSearchResult['entityFound']) {
        let reply = conversation.Reply(userId, 'platform', phraseSearchResult['responseMessages']);
        if (phraseSearchResult.hasOwnProperty('responseMenu')) {
            const menu = phraseSearchResult['responseMenu'];
            reply['menu'] = Object.keys(menu);
        }
        reply['entity'] = phraseSearchResult['entityType'] + '::' + phraseSearchResult['entityUid'];
        return reply;
    } else {
        return false;
    }
}

exports.continueJoinFlow = async (priorMessage, userMessage, userId) => {
    entityType = priorMessage['entity'].substring(0, priorMessage['entity'].indexOf('::'));
    entityUid = priorMessage['entity'].substring(priorMessage['entity'].indexOf('::') + 2);

    const fullUrl = config.get('platform.url') + config.get('platform.paths.entity.respond') + `/${entityType}/${entityUid}`;

    console.log(`prior message has menu: ${priorMessage.hasOwnProperty('menu')} and is number: ${utils.isMessageNumber(userMessage)}`);

    if (priorMessage.hasOwnProperty('menu') && utils.isMessageNumber(userMessage)) {
        userMessage = utils.reshapeContentFromMenu(userMessage, priorMessage);    
    } else if (userMessage['message'] == 'regex of option') {
        console.log('Looks like the message we sent')
    } else if (priorMessage.hasOwnProperty('menu')) {
        // send to NLU to figure out an intent, then map that to a response; we use opening because we are only interested in the intent
        nluResult = conversation.sendToCore(userMessage, userId, 'opening');
        if (nluResult['intent']['confidence'] > 0.7) { // need to abstract this / stick somewhere as constant
            userMessage['type'] = 'payload';
            userMessage['payload'] = nluResult['intent']['name'];
            console.log('initially reshaped message: ', userMessage);

        }
    }

    console.log('reshaped user message: ', userMessage);

    const replyDict = {
        userMessage: userMessage['message'],
        auxProperties: priorMessage['auxProperties'],
        menuOptionPayload: userMessage['payload']
    };

    console.log('reply dict to platform: ', replyDict);

    const options = {
        method: 'POST',
        uri: fullUrl,
        qs: { 'userId': userId },
        body: replyDict,
        auth: authHeader,
        json: true
    }

    const entityFlowResponse = await request(options);
    console.log('resposne from platform, raw: ', entityFlowResponse);

    let reply = conversation.Reply(userId, 'platform', entityFlowResponse['messages']);
    reply['entity'] = entityFlowResponse['entityType'] + '::' + entityFlowResponse['entityUid'];
    if (entityFlowResponse.hasOwnProperty('menu')) {
        reply['menu'] = Object.keys(entityFlowResponse['menu']);
    } else {
        console.log('no menu ... maybe reset this?');
    }
    
    reply['auxProperties'] = entityFlowResponse['auxProperties'];
    return reply;
}

exports.respondToTask = async (taskType, taskUid, response) => {
    const options = {
        method: 'POST',
        uri: config.get('tasks.url') + config.get('tasks.path.respond') + taskType + '/' + taskUid,
        qs: {
            'response': response
        },
        auth: authHeader
    };
    return request(options);
}

exports.createTask = async (taskType, taskParams) => {
    const options = {
        method: 'POST',
        uri: config.get('tasks.url') + config.get('tasks.path.create') + taskType,
        qs: taskParams,
        auth: authHeader
    };
    return request(options);
}
