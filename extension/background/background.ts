chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
    if (request.type === 'PARSE_CURRENT_PAGE' && sender.tab?.id) {
        chrome.scripting.executeScript({
            target: { tabId: sender.tab.id },
            files: ['content-scripts/content.js']
        });
    }
    return true;
});