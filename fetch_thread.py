import urllib.request
import json

req = urllib.request.Request(
    'https://www.reddit.com/r/worldnews/comments/1vxra8b.json',
    headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'}
)
try:
    with urllib.request.urlopen(req) as resp:
        data = json.loads(resp.read().decode('utf-8'))
        comments_listing = data[1]['data']['children']
        for c in comments_listing:
            if c.get('kind') == 't1':
                d = c['data']
                print(f"Top comment by: u/{d.get('author')}")
                reps = d.get('replies')
                if isinstance(reps, dict):
                    rep_children = reps['data']['children']
                    print(f"  Reply count: {len(rep_children)}")
                    for rc in rep_children:
                        if rc.get('kind') == 't1':
                            print(f"   - reply by: u/{rc['data'].get('author')}")
                            sub_reps = rc['data'].get('replies')
                            if isinstance(sub_reps, dict):
                                sub_children = sub_reps['data']['children']
                                for sc in sub_children:
                                    if sc.get('kind') == 't1':
                                        print(f"     -- sub-reply by: u/{sc['data'].get('author')}")
                        else:
                            print(f"   - non-t1: {rc.get('kind')}")
except Exception as e:
    print('Error:', e)
