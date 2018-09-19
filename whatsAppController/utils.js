const MENU_NUMBER_REGEX = /\d+\W?/;

var exports = module.exports = {};

exports.isMessageNumber = (inboundMsg) => {
    return inboundMsg && inboundMsg['type'] && inboundMsg['type'] == 'text' && MENU_NUMBER_REGEX.test(inboundMsg['message']);
}

exports.reshapeContentFromMenu = (content, prior) => {
    console.log('extracting menu selection ... boolean check: ', /\d+/.test(content['message']));
    const menuSelected = content['message'].match(/\d+/).map(Number);
    const payload = prior['menu'][menuSelected - 1];
    console.log(`extracted menu selection: ${menuSelected} and corresponding payload: ${payload}`);
    content['type'] = 'payload';
    content['payload'] = payload;
    return content;
}