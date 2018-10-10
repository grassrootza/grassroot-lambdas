// module for handling calls to main platform for task creation and response (task = meeting, vote, action, livewire alert, etc)
const config = require('config');
const request = require('request-promise');
const conversation = require('./conversation/conversation.js');
const utils = require('./utils.js');

const authHeader = {
    'bearer': config.get('auth.platform')
};

const DATA_TYPES_REQUESTING_LOCATION = ['LOCATION_GPS_REQUIRED', 'LOCATION_PROVINCE_OKAY'];

const PROVINCE_MAP = {
    'gauteng': 'ZA_GP',
    'western_cape': 'ZA_WC',
    'eastern_cape': 'ZA_EC',
    'northern_cape': 'ZA_NC',
    'limpopo': 'ZA_LP',
    'mpumalanga': 'ZA_MP',
    'kzn': 'ZA_KZN',
    'north_west': 'ZA_NW',
    'free_state': 'ZA_FS'
}

var exports = module.exports = {};

exports.checkForJoinPhrase = async (incomingPhrase, rasaNluResult, userId, broadSearch) => {
    // probably want to abstract this generic intent & confidence check 
    console.log(`Checking for a join phrase, incoming phrase = ${incomingPhrase}`)
    if (!!rasaNluResult && rasaNluResult['intent'] == 'join' && !!rasaNluResult['entities']) {
        incomingPhrase = rasaNluResult['entities'][0];
    }

    const options = {
        method: 'POST',
        uri: config.get('platform.url') + config.get('platform.paths.phrase.search'),
        qs: {
            'phrase': incomingPhrase,
            'userId': userId,
            'broadSearch': broadSearch
        },
        auth: authHeader,
        json: true
    };

    const phraseSearchResult = await request(options);
    console.log('Result of phrase search on platform: ', phraseSearchResult);

    if (phraseSearchResult && (phraseSearchResult['entityFound'] || phraseSearchResult['possibleEntities'])) {
        return convertJoinResult(phraseSearchResult, userId);
    } else {
        console.log('Found nothing, out');
        return false;
    }
}

const convertJoinResult = (phraseSearchResult, userId) => {
    console.log('Converting join result: ', phraseSearchResult);
    let reply = conversation.Reply(userId, 'platform', phraseSearchResult['responseMessages']);
        
    const numberOfPossibleEntities = !phraseSearchResult.hasOwnProperty('possibleEntities') ? 0 : 
        Object.keys(phraseSearchResult['possibleEntities']).length;
    console.log('How many possible entities? : ', numberOfPossibleEntities);

    if (numberOfPossibleEntities > 1) {
        const menu = phraseSearchResult['responseMenu'];
        reply['menuPayload'] = Object.keys(menu);
        reply['menuText'] = Object.values(menu);
    }

    if (numberOfPossibleEntities == 1) {
        Object.keys(phraseSearchResult['possibleEntities']).forEach(key => {
            reply['menuPayload'] = [key + '::' + phraseSearchResult['possibleEntities'][key]];
        });
    }
    
    if (phraseSearchResult.hasOwnProperty('requestDataType')) {
        reply = exports.setReplyForDataRequest(reply, phraseSearchResult);
    }
    
    if (phraseSearchResult['entityFound']) {
        reply['entity'] = phraseSearchResult['entityType'] + '::' + phraseSearchResult['entityUid'];
    }

    console.log('Completed join result conversion, returning: ', reply);
    return reply;
}

exports.continueJoinFlow = async (priorMessage, userMessage, userId) => {
    console.log(`Continuing join flow, userMessage = ${userMessage} and userId = ${userId}`)
    if (priorMessage['entity'])
        return exports.continueJoinFlowEntityKnown(priorMessage, userMessage, userId);
    else if (userMessage['payload'])
        return exports.continueJoinFlowSelectEntity(priorMessage, userMessage, userId);
}

exports.continueJoinFlowSelectEntity = async (priorMessage, userMessage, userId) => {
    entityType = userMessage['payload'].substring(0, userMessage['payload'].indexOf('::'));
    entityUid = userMessage['payload'].substring(userMessage['payload'].indexOf('::') + 2);

    const fullUrl = config.get('platform.url') + config.get('platform.paths.entity.select') + `/${entityType}/${entityUid}`;
    
    const options = {
        method: 'POST',
        uri: fullUrl,
        qs: { 
            'userId': userId 
        },
        auth: authHeader,
        json: true
    }

    const entityFlowResponse = await request(options);

    return convertJoinResult(entityFlowResponse, userId);
}

exports.continueJoinFlowEntityKnown = async (priorMessage, userMessage, userId) => {
    entityType = priorMessage['entity'].substring(0, priorMessage['entity'].indexOf('::'));
    entityUid = priorMessage['entity'].substring(priorMessage['entity'].indexOf('::') + 2);

    const fullUrl = config.get('platform.url') + config.get('platform.paths.entity.respond') + `/${entityType}/${entityUid}`;

    let replyDict;
    console.log('assembling reply dict for type: ', userMessage['type']);
    if (userMessage['type'] == 'text' || userMessage['type'] == 'payload') {
        replyDict = await exports.handleTextDataFlow(priorMessage, userMessage);
    } else if (userMessage['type'] == 'location') {
        replyDict = {
            location: userMessage['message'],
            auxProperties: priorMessage['auxProperties'],
            menuOptionPayload: userMessage['payload']
        };
    }

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
    console.log('response from platform, raw: ', entityFlowResponse);

    let reply = conversation.Reply(userId, 'platform', entityFlowResponse['messages']);
    reply['entity'] = entityFlowResponse['entityType'] + '::' + entityFlowResponse['entityUid'];
    reply['auxProperties'] = entityFlowResponse['auxProperties'];

    console.log('entity response data request type: ', entityFlowResponse['requestDataType']);
    if (entityFlowResponse['requestDataType'] == 'MENU_SELECTION') {
        reply['menuPayload'] = Object.keys(entityFlowResponse['menu']);
        reply['menuText'] = Object.values(entityFlowResponse['menu']);
    } else if (entityFlowResponse['requestDataType'] !== 'NONE') { // nor does it equal menu, so must be one of the others
        reply = exports.setReplyForDataRequest(reply, entityFlowResponse);
    }
    
    return reply;
}

exports.handleTextDataFlow = async (priorMessage, userMessage) => {
    // if the last outbound message was a menu, and this inbound does not have a payload in it, test for string similariy
    if (priorMessage.hasOwnProperty('menuPayload') && !userMessage.hasOwnProperty('payload')) {
        // first do a quick similarity and contains check
        console.log('prior message: ', priorMessage);
        const possibleIndex = utils.isMessageLikeMenuTextOrPayload(userMessage, priorMessage['menuText']);
        console.log('possible index: ', possibleIndex);
        if (possibleIndex != -1) {
            userMessage['type'] = 'payload';
            userMessage['payload'] = priorMessage['menuPayload'][possibleIndex];
        }
    } else if (exports.isSkipIntent(userMessage)) {
        userMessage['payload'] = "<<SKIP>>";
    } else if (exports.requiresLocation(priorMessage)) {
        userMessage['payload'] = await exports.convertLocation(userMessage);
    }

    console.log('reshaped user message: ', userMessage);

    const replyDict = {
        userMessage: userMessage['message'],
        auxProperties: priorMessage['auxProperties'],
        menuOptionPayload: userMessage['payload']
    };

    return replyDict;
}

exports.setReplyForDataRequest = (reply, entityFlowResponse) => {    
    reply['auxProperties'] = reply['auxProperties'] || {};
    reply['auxProperties']['requestDataType'] = entityFlowResponse['requestDataType'];
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

exports.requiresLocation = (priorMessage) => {
    // send it to the nlu, to extract a province entity, then map that to platform syntax

    if (!priorMessage.hasOwnProperty('auxProperties') || !priorMessage['auxProperties']) // we know it doesn't need a province, so just abort
        return false;

    if (!priorMessage['auxProperties'].hasOwnProperty('requestDataType') || !priorMessage['auxProperties']['requestDataType']) // as above
        return false;

    const dataType = priorMessage['auxProperties']['requestDataType'];
    if (!DATA_TYPES_REQUESTING_LOCATION.includes(dataType)) // data type does not need a location
        return false;

    // could have done all the above in a single line in a more robust language, but proceeding now
    return true;
}

exports.isSkipIntent = (userMessage) => {
    return userMessage['message'] == '0'; // for now
}

exports.convertLocation = async (userMessage) => {
    const nluResult = await conversation.extractProvince(userMessage['message']);
    console.log('province check nlu result: ', nluResult);

    if (nluResult['intent']['name'] == 'select' && !!nluResult['entities'] && nluResult['entities'][0]['entity'] == 'province') {
        const nlu_province = nluResult['entities'][0]['value'];
        console.log('extracted this province: ', nlu_province);
        return PROVINCE_MAP[nlu_province];
    }

    return userMessage['message'];
}