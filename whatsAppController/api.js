const config = require('config');
const request = require('request-promise');

// const MessagingResponse = require('twilio').twiml.MessagingResponse;

var exports = module.exports = {};

exports.getMessageContent = (req) => {
    console.log('message body: ', req.body);
    if (!req.body['messages']) {
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
    console.log('Complete, sending reply, looks like: ', ourReply);
    console.log('Sending to: ', toPhone);
    
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
