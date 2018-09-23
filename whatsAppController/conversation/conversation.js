const config = require('config');
const request = require('request-promise');

const conversation = require('./conversation-en.json'); // in time maybe switch to reading this from S3 ...?
const START_WORD = new RegExp(config.get('conversation.startWordRegex')); // in case conversation 'finished', but want to re-initiate

var exports = module.exports = {}

exports.Reply = (userId, domain, replyMessages) => {
    return {
        'userId': userId,
        'domain': domain,
        'replyMessages': replyMessages,
        'textSingle': replyMessages && replyMessages.constructor === Array ? replyMessages.join('\n') : 
            replyMessages ? replyMessages : ''
    }
}

// first some special methods, basically for restarting and getting location info
exports.isRestart = async (content, rasaNluResult) => {
    if (content['type'] !== 'text')
        return false;

    if (!rasaNluResult)
        return content['message'].toLowerCase() == 'restart';

    console.log('restart check from core: ', rasaNluResult);
    return (rasaNluResult && rasaNluResult['intent']['name'] == 'restart' && rasaNluResult['intent']['confidence'] > 0.5); // low threshold to allow way out
}

exports.restartConversation = (userId, resetRasa) => {
    if (resetRasa) 
        return exports.restartRasa(userId);
    else
        return exports.Reply(userId, 'restart', 'Okay reset');
}

exports.extractProvince = async (userText) => {
    const options = {
        method: 'GET',
        uri: config.get('core.url.base') + config.get('core.url.province'),
        qs: {
            'user_id': userId,
            'message': userText
        },
        json: true
    }

    return request(options);
}

// then the generic ones
exports.convertCoreResult = (userId, coreResult) => {
    let stdReply = exports.Reply(userId, coreResult['domain'], coreResult['responses']);
    if (coreResult.hasOwnProperty('menu')) {
        stdReply['menuPayload'] = [];
        stdReply['menuText'] = [];
        coreResult['menu'].forEach(item => {
            stdReply['menuPayload'].push(item['payload']);
            stdReply['menuText'].push(item['title']);
        });
    }
    if (coreResult.hasOwnProperty('intent')) {
        // not recording entities at present, as not sure if need to, and privacy questions
        stdReply['intent'] = {
            'name': stdReply['intent']
        };
    }
    if (coreResult.hasOwnProperty('action')) {
        // for recording how Rasa core is doing
        stdReply['action'] = coreResult['action'];
    }
    return stdReply;
}

exports.sendToCore = async (userMessage, userId, domain) => {
    console.log('domain: ', domain);
    console.log('sending to core: ', userMessage);

    let messageToTransmit;
    if (userMessage['type'] === 'text') {
        messageToTransmit = userMessage['message'];
    } else if (userMessage['type'] === 'location') {
        messageToTransmit = '/select' + JSON.stringify(userMessage['message']);
        console.log('converted message: ', messageToTransmit);
    } else if (userMessage['type'] === 'payload') {
        const payload = userMessage['payload'];
        const slot = payload.substring(0, payload.indexOf('::'));
        const value = payload.substring(payload.indexOf('::') + 2);
        let obj = {};
        obj[slot] = value;
        messageToTransmit = '/select' + JSON.stringify(obj);
        console.log("JSON message: ", messageToTransmit);
    };
    console.log('and message to send: ', messageToTransmit);

    const options = {
        method: 'GET',
        uri: config.get('core.url.base') + (domain || 'opening') + config.get('core.url.suffix'),
        qs: {
            'message': messageToTransmit,
            'user_id': userId
        },
        json: true
    };
    
    console.log('options uri: ', options.uri);

    return request(options);
}

exports.restartRasa = async (userId) => {
    console.log('Alright, telling core to restart all');
    const options = {
        method: 'POST',
        uri: config.get('core.url.base') + config.get('core.url.restart'),
        qs: {
            'user_id': userId
        },
        json: true
    }

    console.log('resetting via URL: ', options.uri);
    const resetResult = await request(options);
    console.log('Result of restart request: ', resetResult);
    
    return exports.openingMsg(userId, 'restart');
}

exports.openingMsg = (userId, domain) => {
    const block = conversation[domain];
    const body = exports.getResponseChunk(block, 'start', 0);

    const messages = exports.extractMessages(block, body);

    return exports.Reply(userId, domain, messages.replyMsgs);
}

exports.assembleErrorMsg = (msgId) => {
    const block = conversation['error'];
    const body = exports.getResponseChunk(block, msgId, 0);

    const messages = exports.extractMessages(block, body);

    return {
        context: 'error::' + msgId,
        replies: messages.replyMsgs
    }
}

const getEntity = (block, id) => {
    return block.find(item => item._id == id);
}

exports.getResponseChunk = (block, id, variant = 0) => {
    return getEntity(block, id)._source.response[variant];
}

exports.extractMessages = (block, body) => {
    let replyMsgs = [];

    const optionsRegex = /<<OPTIONS::\w+>>/g;
    var optionsKeys;

    // console.log('body: ', body);
    console.log('body length: ', body.length);
    
    body.forEach(msgComponent => {
        if (optionsRegex.test(msgComponent)) {
            const optionsId = msgComponent.substring('<<OPTIONS::'.length, msgComponent.length - 2);
            console.log('extracted options src: ', optionsId); 
            let optionsEntity = getEntity(block, optionsId);
            optionsKeys = extractOptionsKeys(optionsEntity); // may be randomly sorted in future
            replyMsgs.push(extractOptionDescriptions(optionsEntity, optionsKeys).join('\n'));
        } else {
            replyMsgs.push(msgComponent);
        }
    })

    return {
        replyMsgs: replyMsgs,
        optionsKeys: optionsKeys
    };
}

const extractOptionDescriptions = (optionsEntity, sortedKeys) => {
    return sortedKeys.map((key, index) => (index + 1) + '. ' + optionsEntity._source[key].trim());
}

// may want to randomize this in future, and if so, will want to be sure this is same as above
const extractOptionsKeys = (optionsEntity) => {
    return Object.keys(optionsEntity._source);
}
