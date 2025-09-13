import ollama
import json
import logging
from typing import List, Dict, Any
from pathlib import Path
import base64
import hashlib

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)


class ContentAgent:
    def __init__(self, json_file_path: str = "C:\\Users\\lucja\\IdeaProjects\\Multilevel-Web-Content-Analysis-Using-a-Browser-Extension-\\page_content.json"):
        self.json_file_path = json_file_path
        self.content_data = self.load_json_data()
        self.context_hash = self.generate_context_hash()

    def load_json_data(self) -> List[Dict[str, Any]]:
        """Loads data from JSON file created by Java scraper"""
        try:
            if not Path(self.json_file_path).exists():
                logger.warning(f"File {self.json_file_path} does not exist!")
                return []

            with open(self.json_file_path, 'r', encoding='utf-8') as file:
                data = json.load(file)
                logger.info(f"Loaded {len(data)} elements from {self.json_file_path}")
                return data
        except Exception as e:
            logger.error(f"Error loading JSON file: {e}")
            return []

    def generate_context_hash(self) -> str:
        """Generates hash of the entire context for caching"""
        if not self.content_data:
            return ""

        context_str = json.dumps(self.content_data, sort_keys=True)
        return hashlib.md5(context_str.encode()).hexdigest()

    def prepare_full_context(self) -> str:
        """Prepares the complete context for AI analysis"""
        if not self.content_data:
            return "No data available for analysis."

        # Convert entire JSON to string for full context
        full_context = json.dumps(self.content_data, ensure_ascii=False, indent=2)

        # Basic statistics for the AI
        text_elements = [item for item in self.content_data if item.get('type') == 'TEXT']
        link_elements = [item for item in self.content_data if item.get('type') == 'LINK']

        stats = f"""PAGE CONTEXT ANALYSIS:
- Total elements: {len(self.content_data)}
- Text elements: {len(text_elements)}
- Links: {len(link_elements)}
- Unique selectors: {len(set(item.get('selector', '') for item in self.content_data))}

FULL CONTENT DATA (JSON):
"""
        return stats + full_context

    def ask_agent(self, question: str) -> str:
        """Main method to ask questions to the agent with FULL context"""
        if not self.content_data:
            return "❌ No data available for analysis. Make sure the Java scraper saved data to JSON file."

        # Prepare FULL context
        full_context = self.prepare_full_context()

        # System prompt for the agent (in English)
        system_prompt = f"""You are a specialized web content analysis agent. You have access to the complete scraped content of a web page.

MISSION:
1. Analyze user questions about the page content
2. If information is directly available - provide the CSS selector and element text
3. If information is not found - check available links and suggest the most relevant ones
4. Always provide CSS selectors when possible
5. Analyze the COMPLETE JSON data provided below

RESPONSE FORMAT:
- ✅ Direct information: [selector] - "text content"
- 🔗 Suggested link: [selector] - "link text" (expected topic)
- ❌ Information not found: Explain why and suggest next steps

COMPLETE PAGE CONTEXT:
{full_context}
"""

        try:
            response = ollama.chat(
                model="deepseek-r1:latest",
                messages=[
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": question},
                ],
                options={
                    "max_tokens": 1000,
                    "temperature": 0.1,  # Lower temperature for more precise answers
                    "top_p": 0.9,
                    "num_ctx": 16000  # Larger context window for full JSON
                },
            )

            return response["message"]["content"]

        except Exception as e:
            logger.error(f"Error communicating with model: {e}")
            return f"❌ Error: {str(e)}"

    def analyze_specific_topic(self, topic: str) -> str:
        """Specialized analysis for specific topics"""
        question = f"""Analyze the page content and provide information about: {topic}

Please:
1. Find all relevant information about this topic
2. Provide CSS selectors for each finding
3. Suggest relevant links if direct information is not available
4. Structure your response clearly"""

        return self.ask_agent(question)

    def find_related_links(self, topic: str) -> str:
        """Find links related to specific topic"""
        question = f"""Find all links that might be related to: {topic}

For each relevant link, provide:
- CSS selector
- Link text
- Why it might be relevant"""

        return self.ask_agent(question)

    def interactive_session(self):
        """Interactive session with the agent"""
        print("🤖 Web Content Analysis Agent - Ready!")
        print(f"📊 Loaded {len(self.content_data)} elements")
        print("💡 Type 'exit' to quit")
        print("-" * 50)

        while True:
            try:
                question = input("\n🧠 Your question: ").strip()

                if question.lower() in ['exit', 'quit', 'q']:
                    print("Goodbye! 👋")
                    break

                if not question:
                    continue

                print("\n🔍 Analyzing complete content...")
                response = self.ask_agent(question)
                print(f"\n🤖 Agent response:\n{response}")

            except KeyboardInterrupt:
                print("\n\nGoodbye! 👋")
                break
            except Exception as e:
                print(f"❌ Error: {e}")


def main():
    # Initialize agent (default looks for page_content.json)
    agent = ContentAgent()

    # Interactive mode
    agent.interactive_session()


if __name__ == "__main__":
    main()
