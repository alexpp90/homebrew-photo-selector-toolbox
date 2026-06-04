import json

with open("/app/pr_comments.json", "r") as f:
    comments = json.load(f)
    print(json.dumps(comments, indent=2))
