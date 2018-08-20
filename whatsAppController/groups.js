// module for handling calls to main platform for various tasks
const config = require('config');
const request = require('request-promise');

const authHeader = {
    'bearer': config.get('auth.platform')
};

var exports = module.exports = {};

// todo : if too many groups, only return first few, then 'sorry'
exports.listGroups = async () => {
    const options = {
        method: 'POST',
        uri: config.get('groups.url') + config.get('groups.path.list'),
        auth: authHeader
    };
    return request(options);
}

