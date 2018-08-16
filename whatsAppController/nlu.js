const config = require('config');
const request = require('request-promise');

var exports = module.exports = {};

const NLU_URL = config.get('nlu.url');

exports.interpretMessage = (message_text, conversation_id) => {
    const queryParams = {
        text: message_text
    };

    if (conversation_id)
        queryParams.conversationUid = conversation_id;
    
    const options = {
        url: NLU_URL,
        method: 'GET',
        qs: queryParams
    };

    return request(options);
}

exports.transformNluResponse = (raw) => {
    console.time('nlu_call');
    
    let response = JSON.parse(raw);
    console.log('parsed body: ', response['parsed']);

    let transformed = {
        nlu_id: response['uid'],
        intent: response['parsed']['intent']['name'],
        confidence: response['parsed']['intent']['confidence']
    };

    if (response['entities'])
        transformed['entities'] = response['entities'].map(transformNluEntity);

    console.timeEnd('nlu_call');
    return transformed;
}

exports.transformNluEntity = (entity) => {
    return {
        type: entity['entity'],
        value: entity['value']
    }
}