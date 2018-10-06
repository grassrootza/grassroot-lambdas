const config = require('config');
const request = require('request-promise');

// const MessagingResponse = require('twilio').twiml.MessagingResponse;

var exports = module.exports = {};

exports.getMessageContent = (req) => {
    console.log('message body: ', req.body);
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

    if (incoming_type === 'image') {
        incoming_message ={
            mime_type: pulledBody['image']['mime_type'],
            media_id: pulledBody['image']['id'],
            media_caption: pulledBody['image']['caption']
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
    console.log('Complete, sending reply, looks like: ', ourReply);
    console.log('Sending to: ', toPhone);

    if (!toPhone) {
        console.log('No one to send to, returning false');
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

    console.log('outbound url: ', outboundSend.uri);

    for (const reply of ourReply.replyMessages) {
        responseBase['text']['body'] = reply;
        outboundSend['body'] = responseBase;
        console.log('outbound body: ', outboundSend.body)
        const outboundResult = await request(outboundSend);
        console.log('outbound result: ', outboundResult);
    }

    return 'dispatched';
}

const extractAndStoreMedia = (media_id) => {
    // do the needful.
}