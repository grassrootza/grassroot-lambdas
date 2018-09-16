// module for handling calls to main platform for task creation and response (task = meeting, vote, action, livewire alert, etc)
const config = require('config');
const request = require('request-promise');

const authHeader = {
    'bearer': config.get('auth.platform')
};

var exports = module.exports = {};

exports.checkForJoinPhrase = (incomingPhrase, userId) => {
    const options = {
        method: 'POST',
        uri: config.get('platform.url') + config.get('platform.paths.phrase.search'),
        qs: {
            'phrase': incomingPhrase,
            'userId': userId
        },
        auth: authHeader,
        json: true
    };

    return request(options);
}

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
