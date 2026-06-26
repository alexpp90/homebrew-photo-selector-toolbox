import os
import json
import urllib.request
from typing import Dict, Any

def get_pr_comments() -> Dict[Any, Any]:
    url = f"{os.environ.get('SANDBOX_API_URL')}/github/pr/comments"
    req = urllib.request.Request(url, headers={'Content-Type': 'application/json'})
    try:
        with urllib.request.urlopen(req) as response:
            return json.loads(response.read().decode())
    except Exception as e:
        print(f"Error fetching comments: {e}")
        return {"comments": []}

comments = get_pr_comments().get("comments", [])
print(f"Found {len(comments)} comments")
for c in comments:
    print(f"ID: {c.get('id')}")
    print(f"Body: {c.get('body')}")
