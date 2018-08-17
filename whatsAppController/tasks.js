// module for handling calls to main platform for various tasks
const config = require('config');
const request = require('request-promise');

const authHeader = {
    'bearer': 'insert_token_here'
};

var exports = module.exports = {};

exports.respondToTask = async (taskType, taskUid, response) => {
    const options = {
        method: 'POST',
        uri: config.get('tasks.url') + config.get('tasks.path.respond') + taskType + '/' + taskUid,
        qs: {
            'response': response
        },
        auth: authHeader
    };
    return request(options);
}

exports.createTask = async (taskType, taskParams) => {
    const options = {
        method: 'POST',
        uri: config.get('tasks.url') + config.get('tasks.path.create') + taskType,
        qs: taskParams,
        auth: authHeader
    };
    return request(options);
}
