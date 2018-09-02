const config = require('config');
const MessagingResponse = require('twilio').twiml.MessagingResponse;

var exports = module.exports = {};

// we will be swapping these out in future, as possibly / probably not using Twilio, so stashing them
exports.getMessageContent = (req) => {
    console.log('message body: ', req.body);
    const incoming_text = req.body['Body'];
    const incoming_phone = req.body['From'] ? req.body['From'].substring('whatsapp:+'.length) : '<<Error>>';
    return {
        'message': incoming_text,
        'from': incoming_phone
    };
}

exports.sendResponse = async (ourReply, expressRes) => {
    console.log('Complete, sending reply, looks like: ', ourReply);
    if (ourReply) {
        expressRes.writeHead(200, {'Content-Type': config.get('response.contentType')});
        expressRes.end(turnMsgsIntoBody(ourReply.replyMsgs));
        return 'finished';
    } else {
        expressRes.writeHead(200, {'Content-Type': config.get('response.contentType')});
        expressRes.end(emptyMsgBody());
        return 'error';
    }
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
