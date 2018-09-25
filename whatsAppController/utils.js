const stringSimilarity = require('string-similarity');

const MENU_NUMBER_REGEX = /^\s?\d{1,2}\W?$/;
const STRING_CONF_THRESHOLD = 0.6;

var exports = module.exports = {};


exports.isMessageMenuNumber = (inboundMsg) => {
    return inboundMsg && inboundMsg['type'] && inboundMsg['type'] == 'text' && MENU_NUMBER_REGEX.test(inboundMsg['message']);
}

// returns the index, or -1 if nothing found
exports.isMessageLikeMenuTextOrPayload = (inboundText, priorMenuTexts) => {
    // since similarity doesn't work great if user responds with one key word 
    const normalizedText = inboundText['message'].toLowerCase().trim();
    const normalizedOptions = priorMenuTexts.map(option => option.toLowerCase().trim());
    console.log(`checking for text: ${normalizedText}, in: ${normalizedOptions.toString()}`);

    const containsTest = normalizedOptions.findIndex(option => option.includes(normalizedText));
    if (containsTest != -1) {
        return containsTest;
    }

    const match = stringSimilarity.findBestMatch(normalizedText, normalizedOptions);
    if (match.bestMatch.rating > STRING_CONF_THRESHOLD) {
        return normalizedOptions.findIndex(option => option == match.bestMatch.target);
    }

    return -1;
}

exports.reshapeContentFromMenu = (content, lastMenu) => {
    console.log('extracting menu selection ... boolean check: ', /\d+/.test(content['message']));
    const menuSelected = content['message'].match(/\d+/).map(Number);
    console.log('and menu select = ', menuSelected);
    console.log('prior menu: ', lastMenu);
    const payload = lastMenu[menuSelected - 1];
    console.log(`extracted menu selection: ${menuSelected} and corresponding payload: ${payload}`);
    content['type'] = 'payload';
    content['payload'] = payload;
    return content;
}