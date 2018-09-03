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

exports.sendToCore = async (userMessage, userId, domain) => {
    const options = {
        method: 'GET',
        uri: config.get('core.url.base') + (domain || 'opening') + config.get('core.url.suffix'),
        qs: {
            'message': userMessage,
            'user_id': userId
        },
        json: true
    };

    return request(options);
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
