export interface LinkItem {
  text: string;
  url: string;
  context?: string;
}

export interface HeadingItem {
  level: number;
  text: string;
}

export interface ParsedContent {
  url: string;
  timestamp: string;
  texts: string[];
  links: LinkItem[];
  headings: HeadingItem[];
}
