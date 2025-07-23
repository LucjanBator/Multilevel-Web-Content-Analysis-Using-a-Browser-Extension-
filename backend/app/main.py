from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List, Optional
from datetime import datetime

app = FastAPI()

# Configure CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["chrome-extension://*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

class LinkItem(BaseModel):
    text: str
    url: str
    context: Optional[str] = None

class HeadingItem(BaseModel):
    level: int
    text: str

class ParsedContent(BaseModel):
    url: str
    timestamp: str
    texts: List[str]
    links: List[LinkItem]
    headings: List[HeadingItem]

@app.post("/api/analyze")
async def analyze_content(content: ParsedContent):
    try:
        # Basic analysis
        text_char_count = sum(len(text) for text in content.texts)
        word_count = sum(len(text.split()) for text in content.texts)

        return {
            "analysis": {
                "text_sections": len(content.texts),
                "links": len(content.links),
                "headings": len(content.headings),
                "total_characters": text_char_count,
                "total_words": word_count,
                "avg_word_length": text_char_count / word_count if word_count > 0 else 0
            },
            "metadata": {
                "url": content.url,
                "timestamp": content.timestamp,
                "processed_at": datetime.now().isoformat()
            }
        }
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))

@app.get("/")
async def root():
    return {"status": "running", "service": "Page Parser API"}