'use strict';

const config = require('config');
const request = require('request-promise');

module.exports.send = async (event, context) => {
  
  console.log('event: ', event);
  const payload = JSON.parse(event['Records'][0]['body']);
  console.log('payload: ', payload);

  const recipient = payload['recipient'];
  const messages = payload['messages'];

  const contactCheckResult = await checkPhoneNumberInContacts(recipient);
  console.log(`rate limit left: ${contactCheckResult['headers']['x-ratelimit-remaining']}, rate limit reset: ${contactCheckResult['headers']['x-ratelimit-reset']}`);
  
  const thisContact = contactCheckResult['data']['contacts'][0];
  console.log('this contact: ', thisContact);

  if (thisContact['status'] !== 'valid') {
    console.log('Invalid contact, not sending');
    return { message: 'Contact invalid!' }; // todo : stick in DLQ
  }

  // await sendMessagesToRecipient(thisContact['wa_id'], messages);

  // then: dispatch to recording queue [NB]

  return { 
    message: 'Completed the send!'
  };
 
}

const _include_headers = function(body, response, resolveWithFullResponse) {
  return {'headers': response.headers, 'data': body};
};

const checkPhoneNumberInContacts = async (recipient) => {

  const checkObject = {
    method: 'POST',
    uri: config.get('api.contacts.url'),
    headers: {
      'Content-Type': 'application/json'
    },
    auth: {
      'bearer': config.get('auth.whatsapp.token')
    },
    body: {
      'blocking': 'wait',
      'contacts': [ '+' + recipient ]
    },
    transform: _include_headers,
    json: true
  }

  console.log('sending object: ', checkObject);

  const contactCheckResult = await request(checkObject);
  // console.log('contact check result: ', contactCheckResult);
  return contactCheckResult;
}

const sendMessagesToRecipient = async (whatsAppId, messages) => {

  const baseBody = {
    'preview_url': false,
    'recipient_type': 'individual',
    'to': whatsAppId,
    'type': 'text',
    'text': {}
  }
  
  const baseOutboundOptions = {
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

  console.log('outbound url: ', baseOutboundOptions.uri);

  for (const message of messages) {
    baseBody['text']['body'] = message;
    baseOutboundOptions['body'] = baseBody;
    console.log('outbound body: ', baseOutboundOptions.body)
    const outboundResult = await request(baseOutboundOptions);
    console.log('outbound result: ', outboundResult);
  }
  
  // todo : handle rate limited response
  return 'dispatched';
}