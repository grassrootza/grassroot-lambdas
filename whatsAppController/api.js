const config = require('config');
const logger = require('debug')('grassroot:whatsapp:api');

const request = require('request-promise');
const util = require('util');

var exports = module.exports = {};

const MEDIA_TYPES = ['image', 'voice', 'video', 'audio']

exports.isMediaType = (type) => {
    return MEDIA_TYPES.indexOf(type) > -1;
}

exports.getMessageContent = (req) => {
    logger('message body: ', util.inspect(req.body, false, null, false));
    if (!!req.body['statuses'] || !req.body['messages']) {
        return false;
    }

    const pulledBody = req.body['messages'][0];

    const incoming_type = pulledBody['type'];
    
    let incoming_message; // where we will, depending, do a transformation (esp for locations)
    let incoming_raw; // where we will store exactly what's received

    if (incoming_type === 'text') {
        incoming_message = pulledBody['text']['body'];
        incoming_raw = incoming_message;
    }

    if (incoming_type === 'location') {
        incoming_message = {
            latitude: pulledBody['location']['latitude'],
            longitude: pulledBody['location']['longitude']
        },
        incoming_raw = JSON.stringify(pulledBody['location'])
    };

    logger(`Is incoming message right? : ${exports.isMediaType(incoming_type)}`)
    if (exports.isMediaType(incoming_type)) {
        logger('image body = ', pulledBody['image']);
        const mediaBody = pulledBody[incoming_type];
        incoming_message ={
            mime_type: mediaBody['mime_type'],
            media_id: mediaBody['id'],
            media_caption: mediaBody['caption']
        }
    }

    const incoming_phone = pulledBody['from'];
    return {
        'type': incoming_type,
        'message': incoming_message,
        'raw': incoming_raw,
        'from': incoming_phone
    };
}

// in current case (= W/A API direct, we don't send a response back, hence it's unused, but leaving in sig in case in future)
exports.sendResponse = async (toPhone, ourReply, expressRes) => {
    logger('Complete, sending reply, looks like: ', ourReply);
    logger('Sending to: ', toPhone);

    if (!toPhone) {
        logger('No one to send to, returning false');
        return 'dispatched'; // since we want to make sure nothing happens
    }

    const responseBase = {
        'preview_url': false,
        'recipient_type': 'individual',
        'to': toPhone,
        'type': 'text',
        'text': {
            'body': ourReply.textSingle
        }
    }

    const outboundSend = {
        method: 'POST',
        uri: config.get('api.outbound.url'),
        headers: {
            'Content-Type': 'application/json'
        },
        auth: {
            'bearer': config.get('auth.whatsapp.token')
        },
        json: true
    }

    logger('outbound url: ', outboundSend.uri);
    
    const nonEmptyReplies = ourReply.replyMessages.filter(reply => !!reply);
    logger('Sending non-empty replies: ', nonEmptyReplies);

    for (const reply of nonEmptyReplies) {
        responseBase['text']['body'] = reply;
        outboundSend['body'] = responseBase;
        logger('outbound body: ', outboundSend.body)
        const outboundResult = await request(outboundSend);
        logger('outbound result: ', outboundResult);
    }

    return 'dispatched';
}
