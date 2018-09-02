const config = require('config');
const request = require('request-promise');

const authHeader = {
    'bearer': config.get('auth.platform')
};

var exports = module.exports = {};

// todo : if too many groups, only return first few, then 'sorry'

exports.fetchUserId = async (phoneNumber) => {
    userId = await fetchIdFromServer(phoneNumber);
    console.log('retrieved userId: ', userId);
    return userId;
}

fetchIdFromServer = (phoneNumber) => {
    const options = {
        method: 'POST',
        uri: config.get('users.url') + config.get('users.path.id'),
        auth: authHeader,
        qs: {
            'msisdn': phoneNumber
        }
    };
    console.log('calling: ', options.uri);
    return request(options);
}