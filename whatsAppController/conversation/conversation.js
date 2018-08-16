const config = require('config');

const conversation = require('./conversation-en.json'); // in time maybe switch to reading this from S3 ...?
const START_WORD = new RegExp(config.get('conversation.startWordRegex')); // in case conversation 'finished', but want to re-initiate

var exports = module.exports = {}

exports.assembleErrorMsg = (msgId) => {
    const block = getRelevantConversationBlock('error');
    const body = getResponseChunk(block, msgId, 0);

    const messages = extractMessages(block, body);

    return {
        context: 'error::' + msgId,
        replies: messages.replyMsgs
    }
}

exports.getRelevantConversationBlock = (section) => {
    // will need to look this up from prior, message, etc, for now, just sending generic
    return conversation[section];
}

exports.findBlockForIntent = (intent) => {
    console.log('hunting for intent: ', intent);
    var returnBlock;
    const conversationSections = Object.keys(conversation).filter(key => key !== 'meta');
    // double iteration (well, tree search), but these are small, and always will be, so in memory will be microseconds
    // also, might be able to do this more elegantly with a find or map, but would still involve that double iteration underneath
    conversationSections.some(section => {
        let blocks = conversation[section];
        blocks.some(block => {
            console.log('checking block: ', block['_id']);
            if (block['_intent'] && block['_intent'] == intent) {
                returnBlock = block;
                return true;
            }
            return false;
        });
        return !!returnBlock;
    });
    console.log('return block: ', returnBlock);
    return returnBlock;
}

exports.getEntity = (block, id) => {
    // console.log('looking for ' + id + ' in block: ', block);
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

exports.extractOptionDescriptions = (optionsEntity, sortedKeys) => {
    return sortedKeys.map((key, index) => (index + 1) + '. ' + optionsEntity._source[key].trim());
}

// may want to randomize this in future, and if so, will want to be sure this is same as above
exports.extractOptionsKeys = (optionsEntity) => {
    return Object.keys(optionsEntity._source);
}
