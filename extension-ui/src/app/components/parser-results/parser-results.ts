import { Component, OnInit } from '@angular/core';
import { ChromeService } from '../../services/chrome.service';
import { ParsedContent } from '../../models/parsed-content.model';

@Component({
  selector: 'app-parser-results',
  templateUrl: './parser-results.component.html',
  styleUrls: ['./parser-results.component.scss']
})
export class ParserResultsComponent implements OnInit {
  parsedData: ParsedContent | null = null;
  isLoading = true;

  constructor(private chromeService: ChromeService) {}

  ngOnInit() {
    this.chromeService.onMessage().subscribe((message) => {
      if (message.type === 'PAGE_PARSED') {
        this.parsedData = message.data;
        this.isLoading = false;
      }
    });

    this.requestPageParse();
  }

  private async requestPageParse() {
    const tab = await this.chromeService.getCurrentTab();
    await this.chromeService.sendMessage({
      type: 'PARSE_CURRENT_PAGE',
      tabId: tab.id
    });
  }

  exportToJson() {
    if (!this.parsedData) return;

    const dataStr = JSON.stringify(this.parsedData, null, 2);
    const blob = new Blob([dataStr], { type: 'application/json' });
    const url = URL.createObjectURL(blob);

    const a = document.createElement('a');
    a.href = url;
    a.download = `page-content-${new Date().toISOString()}.json`;
    a.click();

    URL.revokeObjectURL(url);
  }
}
