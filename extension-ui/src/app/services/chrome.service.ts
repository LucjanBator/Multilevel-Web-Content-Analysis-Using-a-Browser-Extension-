import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';
import { ParsedContent } from '../models/parsed-content.model';

declare const chrome: any; // Deklaracja dla TypeScript

@Injectable({ providedIn: 'root' })
export class ChromeService {
  private messageSubject = new Subject<any>();

  constructor() {
    chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
      this.messageSubject.next(request);
      sendResponse({ status: 'received' });
    });
  }

  onMessage() {
    return this.messageSubject.asObservable();
  }

  sendMessage(message: any) {
    return new Promise((resolve) => {
      chrome.runtime.sendMessage(message, (response) => {
        resolve(response);
      });
    });
  }

  getCurrentTab() {
    return new Promise<chrome.tabs.Tab>((resolve) => {
      chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
        resolve(tabs[0]);
      });
    });
  }
}
