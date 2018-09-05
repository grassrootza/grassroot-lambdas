const config = require('config');
const request = require('request-promise');

// const MessagingResponse = require('twilio').twiml.MessagingResponse;

var exports = module.exports = {};

// we will be swapping these out in future, as possibly / probably not using Twilio, so stashing them
exports.getMessageContent = (req) => {
    console.log('message body: ', req.body);
    const incoming_type = req.body['type'];
    
    let incoming_message; // where we will, depending, do a transformation (esp for locations)
    let incoming_raw; // where we will store exactly what's received

    if (incoming_type === 'text') {
        incoming_message = req.body['text']['body'];
        incoming_raw = incoming_message;
    }

    if (incoming_type === 'location') {
        incoming_message = {
            latitude: req.body['location']['latitude'],
            longitude: req.body['location']['longitude']
        },
        incoming_raw = JSON.stringify(req.body['location'])
    };

    const incoming_phone = req.body['from'];
    return {
        'type': incoming_type,
        'message': incoming_message,
        'raw': incoming_raw,
        'from': incoming_phone
    };
}

// in current case (= W/A API direct, we don't send a response back, hence it's unused, but leaving in sig in case in future)
exports.sendResponse = async (ourReply, expressRes) => {
    console.log('Complete, sending reply, looks like: ', ourReply);
    
    const loginOptions = {
        method: 'POST',
        uri: config.get('auth.whatsapp.url'),
        auth: {
            'user': config.get('auth.whatsapp.username'),
            'pass': config.get('auth.whatsapp.password')
        },
        json: true
    }

    const authResult = await request(loginOptions);
    console.log('auth result on whatsapp: ', authResult);
    const authToken = authResult['users'][0]['token'];
    console.log('and auth token: ', authToken);

    const responseBase = {
        'preview_url': false,
        'recipient_type': 'individual',
        'to': '27813074085',
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
            'bearer': authToken
        },
        json: true
    }

    for (const reply of ourReply.replyMessages) {
        responseBase['text']['body'] = reply;
        outboundSend['body'] = responseBase;
        const outboundResult = await request(outboundSend);
        console.log('outbound result: ', outboundResult);
    }

    // if (ourReply) {
        
    // } else {
        
    // }
    return 'dispatched';
}

const turnMsgsIntoBody = (replyMsgs) => {
    if (replyMsgs) {
        const twiml = new MessagingResponse();
        replyMsgs.forEach(msg => twiml.message(msg));
        return twiml.toString();
    } else {
        return exports.emptyMsgBody();
    }
}

exports.emptyMsgBody = () => {
    const twiml = new MessagingResponse();
    return twiml.toString();
}
