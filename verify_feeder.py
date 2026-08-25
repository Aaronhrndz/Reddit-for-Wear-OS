import urllib.request, json

token = 'eyJhbGciOiJSUzI1NiIsImtpZCI6IlNIQTI1NjpzS3dsMnlsV0VtMjVmcXhwTU40cWY4MXE2OWFFdWFyMnpLMUdhVGxjdWNZIiwidHlwIjoiSldUIn0.eyJzdWIiOiJ1c2VyIiwiZXhwIjoxNzg3NjkwNjU0Ljk1OTg2MiwiaWF0IjoxNzg3NjA0MjU0Ljk1OTg2MiwianRpIjoiZ0stclVtN3hkcGhlcXJZVXBDVmdXeVhrWTJuZ0J3IiwiY2lkIjoiMFItV0FNaHVvby1NeVEiLCJsaWQiOiJ0Ml84cDZzZXVodyIsImFpZCI6InQyXzhwNnNldWh3IiwiYXQiOjEsImxjYSI6MTYwNDE1NTc2MjAwMCwic2NwIjoiZUp4a2tkR090REFJaGQtRmE1X2dmNVVfbTAxdGNZYXNMUWFvazNuN0RWb2NrNzA3Y0Q0cEhQOURLb3FGRENaWGdxbkFCRmdUclREQlJ1VDluTG0zZzJpTmU4dFlzWm5DQkZtd0ZEcmttTEdzaVFRbWVKSWF5eHNtb0lMTnlGeXV0R05OTFQwUUpxaGNNcmVGSHBjMm9ia2JpNTZkR0ZXNXJEeW9zVmZsMHRqR0ZMWW54amNicXcycHVDNm5Na25MUXZrc1h2VGpOOVczOXZtel9TYTBKOE9LcXVtQjNobEpDRzRzZnBpbTNkOVRrNTZ0Q3hhMTkzcVEydWQ2M0s1OTFpdzBPN2VmNl9sckl4bVhZMmgtSnZ0MzF5LWhBNDg4THpQcUFFYXM0VWNaZG1RZF9sVUhVTG1nSkdNSjR0TUk1TXJsMjM4SnRtdlR2OGJ0RXo5OE0tS21OX3pXRE5SekNlTFFwX0gxR3dBQV9fOFExZVRSIiwicmNpZCI6IkxPSzR3N1RHYm5OMzRFSjRjS1hvLTVZXzFVSXQ3bXVzQzA0dzg1Vk9reDAiLCJmbG8iOjJ9.pTuJGhob9-4Q_GZ53uvzu4D0VwgkDVboxgBRmIDZU1HiKkqhJeUeU4dcLSwY_kvx8ApK_dztJpjU1Lz-Fz_hpgun0rUNopo99-FxtcYRpfn_8VslPD8XsQSD6JWctBOXXAb6daCTCGIklouKrEZcWj-UL8uKo-9H9qXMRD1G2YD5-8XFvUXjBzKZCc2s4WZaUAnKteoInlvt0KkL51GjfCTppnVEA5VBUEBa1bxSIRze2p_hyoZWXc7uRUAO0GCtJ18inpm375Q6bi3caLAyLn9e0h_V_Uisb55Jli6paKwaoqojdgmDw-6xVEd2Z4WcB7oWzR5XRByms_SBOMK5LA'
req = urllib.request.Request('https://oauth.reddit.com/r/worldnews/comments/1vxra8b.json?raw_json=1', headers={'Authorization': f'Bearer {token}', 'User-Agent': 'android:com.reddit.wearos:v1.0.0'})
data = json.loads(urllib.request.urlopen(req).read().decode('utf-8'))

class Seg:
    def __init__(self, top=False, bottom=False, horiz=False):
        self.top = top; self.bottom = bottom; self.horiz = horiz
    def __repr__(self):
        return f"({int(self.top)}{int(self.bottom)}{int(self.horiz)})"

def flatten(comments, depth=0, active_ancestors=[]):
    result = []
    for i, c in enumerate(comments):
        if c.get('kind') != 't1': continue
        d = c['data']
        is_last = (i == len(comments) - 1)
        replies_raw = d.get('replies')
        replies = []
        if isinstance(replies_raw, dict):
            replies = [x for x in replies_raw['data']['children'] if x.get('kind') == 't1']
        has_replies = len(replies) > 0
        
        segs = []
        # 1. Ancestor pass-throughs
        for col in range(depth - 1):
            active = col < len(active_ancestors) and active_ancestors[col]
            segs.append(Seg(top=active, bottom=active))
        
        # 2. Own column (depth-1): always top=True (from parent or sibling)
        if depth > 0:
            segs.append(Seg(top=True, bottom=not is_last, horiz=True))
        
        # 3. Feeder for children: bottom=True in column depth
        if has_replies:
            segs.append(Seg(top=False, bottom=True))
        
        result.append({'author': d.get('author'), 'depth': depth, 'segs': segs})
        
        if has_replies:
            next_anc = [] if depth == 0 else active_ancestors + [not is_last]
            result.extend(flatten(replies, depth + 1, next_anc))
    return result

top_comments = data[1]['data']['children']
flat = flatten(top_comments)

errors = 0
for i in range(len(flat) - 1):
    curr = flat[i]; nxt = flat[i + 1]
    max_cols = max(len(curr['segs']), len(nxt['segs']))
    for col in range(max_cols):
        cb = curr['segs'][col].bottom if col < len(curr['segs']) else False
        nt = nxt['segs'][col].top if col < len(nxt['segs']) else False
        if cb != nt:
            print(f"[ERROR] #{i} u/{curr['author']} (d={curr['depth']}) -> #{i+1} u/{nxt['author']} (d={nxt['depth']}) col {col}: bottom={cb} top={nt}")
            errors += 1

print(f"\nTotal: {len(flat)} comments, {errors} errors")

# ASCII visual first 30
print("\nASCII tree (first 30):")
for idx, item in enumerate(flat[:30]):
    line = ""
    for col, seg in enumerate(item['segs']):
        if seg.horiz:
            if seg.bottom:
                line += "+--"
            else:
                line += "\\--"
        elif seg.top and seg.bottom:
            line += "|  "
        elif seg.bottom and not seg.top:
            line += ":"  # feeder start
            line += "  "
        elif seg.top and not seg.bottom:
            line += "'  "
        else:
            line += "   "
    print(f"{idx:2d} {line} u/{item['author']}")
