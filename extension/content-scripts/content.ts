import { ParsedContent } from '../../extension-ui/src/app/models/parsed-content.model';

function isVisible(element: HTMLElement): boolean {
    const style = window.getComputedStyle(element);
    return style.display !== 'none' &&
        style.visibility !== 'hidden' &&
        element.offsetParent !== null;
}

function extractTextContent(node: Node): string {
    let text = '';
    const walker = document.createTreeWalker(
        node,
        NodeFilter.SHOW_TEXT,
        null
    );

    let currentNode;
    while ((currentNode = walker.nextNode())) {
        if (currentNode.textContent?.trim()) {
            text += currentNode.textContent.trim() + ' ';
        }
    }

    return text.trim();
}

export function parsePage(): ParsedContent {
    const result: ParsedContent = {
        url: window.location.href,
        timestamp: new Date().toISOString(),
        texts: [],
        links: [],
        headings: []
    };

    const allElements = document.querySelectorAll('body *');

    allElements.forEach(element => {
        if (!isVisible(element as HTMLElement)) return;

        if (element instanceof HTMLAnchorElement && element.href) {
            result.links.push({
                text: element.innerText.trim(),
                url: element.href,
                context: element.parentElement?.textContent?.slice(0, 100)
            });
        } else if (/^H[1-6]$/.test(element.tagName)) {
            result.headings.push({
                level: parseInt(element.tagName.substring(1)),
                text: element.textContent?.trim() || ''
            });
        } else if (element.childNodes.length === 1 &&
            element.childNodes[0].nodeType === Node.TEXT_NODE) {
            const text = element.textContent?.trim();
            if (text) {
                result.texts.push(text);
            }
        }
    });

    return result;
}

chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
    if (request.type === 'PARSE_CURRENT_PAGE') {
        const parsedData = parsePage();
        sendResponse({ type: 'PAGE_PARSED', data: parsedData });
    }
});