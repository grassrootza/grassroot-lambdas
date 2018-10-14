const stringSimilarity = require('string-similarity');

const MENU_NUMBER_REGEX = /^\s?\d{1,2}\W?$/;
const STRING_CONF_THRESHOLD = 0.6;

var exports = module.exports = {};


exports.isMessageMenuNumber = (inboundMsg) => {
    return inboundMsg && inboundMsg['type'] && inboundMsg['type'] == 'text' && MENU_NUMBER_REGEX.test(inboundMsg['message']);
}

// returns the index, or -1 if nothing found
exports.isMessageLikeMenuTextOrPayload = (inboundText, priorMenuTexts) => {
    // sometimes may have empty menu texts, in which case, just remove them
    if (!priorMenuTexts || priorMenuTexts.length == 0)
        return -1;
    
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

exports.extractEntityFromPrior = (priorOutbound) => {
    return {
        'entityType': priorOutbound['entity'].substring(0, priorOutbound['entity'].indexOf('::')),
        'entityUid': priorOutbound['entity'].substring(priorOutbound['entity'].indexOf('::') + 2)
    }
}
