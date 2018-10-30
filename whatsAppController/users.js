const config = require('config');
const logger = require('debug')('grassroot:whatsapp:users');

const request = require('request-promise');
const authHeader = {
    'bearer': config.get('auth.platform')
};

var exports = module.exports = {};

// todo : if too many groups, only return first few, then 'sorry'

exports.fetchUserId = async (phoneNumber) => {
    userId = await fetchIdFromServer(phoneNumber);
    logger('retrieved userId: ', userId);
    return userId;
}

fetchIdFromServer = (phoneNumber) => {
    logger('fetching user ID for phone number: ', phoneNumber);
    const options = {
        method: 'POST',
        uri: config.get('users.url') + config.get('users.path.id'),
        auth: authHeader,
        qs: {
            'msisdn': phoneNumber
        }
    };
    logger('calling: ', options.uri);
    logger('query params: ', options.qs);
    return request.post(options);
}