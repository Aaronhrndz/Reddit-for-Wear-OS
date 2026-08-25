import json

with open('reddit_thread.json', encoding='utf-8') as f:
    data = json.load(f)

comments_listing = data[1]['data']['children']

def get_comments(listing):
    res = []
    for item in listing:
        if item.get('kind') == 't1':
            res.append(item['data'])
    return res

def parse_tree(comments, depth=0, pass_through=None):
    if pass_through is None:
        pass_through = []
    
    for i, c in enumerate(comments):
        is_last = (i == len(comments) - 1)
        author = c.get('author')
        replies_raw = c.get('replies')
        replies = []
        if isinstance(replies_raw, dict):
            replies = get_comments(replies_raw['data']['children'])
        has_replies = len(replies) > 0
        
        print(f"depth={depth} author=u/{author:20} is_last={str(is_last):5} has_rep={str(has_replies):5} pass_thru={pass_through}")
        
        if has_replies:
            next_pt = [] if depth == 0 else pass_through + [not is_last]
            parse_tree(replies, depth + 1, next_pt)

top_comments = get_comments(comments_listing)
parse_tree(top_comments)
