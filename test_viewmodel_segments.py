import urllib.request, json

token = 'eyJhbGciOiJSUzI1NiIsImtpZCI6IlNIQTI1NjpzS3dsMnlsV0VtMjVmcXhwTU40cWY4MXE2OWFFdWFyMnpLMUdhVGxjdWNZIiwidHlwIjoiSldUIn0.eyJzdWIiOiJ1c2VyIiwiZXhwIjoxNzg3NjkwNjU0Ljk1OTg2MiwiaWF0IjoxNzg3NjA0MjU0Ljk1OTg2MiwianRpIjoiZ0stclVtN3hkcGhlcXJZVXBDVmdXeVhrWTJuZ0J3IiwiY2lkIjoiMFItV0FNaHVvby1NeVEiLCJsaWQiOiJ0Ml84cDZzZXVodyIsImFpZCI6InQyXzhwNnNldWh3IiwiYXQiOjEsImxjYSI6MTYwNDE1NTc2MjAwMCwic2NwIjoiZUp4a2tkR090REFJaGQtRmE1X2dmNVVfbTAxdGNZYXNMUWFvazNuN0RWb2NrNzA3Y0Q0cEhQOURLb3FGRENaWGdxbkFCRmdUclREQlJ1VDluTG0zZzJpTmU4dFlzWm5DQkZtd0ZEcmttTEdzaVFRbWVKSWF5eHNtb0lMTnlGeXV0R05OTFQwUUpxaGNNcmVGSHBjMm9ia2JpNTZkR0ZXNXJEeW9zVmZsMHRqR0ZMWW54amNicXcycHVDNm5Na25MUXZrc1h2VGpOOVczOXZtel9TYTBKOE9LcXVtQjNobEpDRzRzZnBpbTNkOVRrNTZ0Q3hhMTkzcVEydWQ2M0s1OTFpdzBPN2VmNl9sckl4bVhZMmgtSnZ0MzF5LWhBNDg4THpQcUFFYXM0VWNaZG1RZF9sVUhVTG1nSkdNSjR0TUk1TXJsMjM4SnRtdlR2OGJ0RXo5OE0tS21OX3pXRE5SekNlTFFwX0gxR3dBQV9fOFExZVRSIiwicmNpZCI6IkxPSzR3N1RHYm5OMzRFSjRjS1hvLTVZXzFVSXQ3bXVzQzA0dzg1Vk9reDAiLCJmbG8iOjJ9.pTuJGhob9-4Q_GZ53uvzu4D0VwgkDVboxgBRmIDZU1HiKkqhJeUeU4dcLSwY_kvx8ApK_dztJpjU1Lz-Fz_hpgun0rUNopo99-FxtcYRpfn_8VslPD8XsQSD6JWctBOXXAb6daCTCGIklouKrEZcWj-UL8uKo-9H9qXMRD1G2YD5-8XFvUXjBzKZCc2s4WZaUAnKteoInlvt0KkL51GjfCTppnVEA5VBUEBa1bxSIRze2p_hyoZWXc7uRUAO0GCtJ18inpm375Q6bi3caLAyLn9e0h_V_Uisb55Jli6paKwaoqojdgmDw-6xVEd2Z4WcB7oWzR5XRByms_SBOMK5LA'
req = urllib.request.Request('https://oauth.reddit.com/r/worldnews/comments/1vxra8b.json?raw_json=1', headers={'Authorization': f'Bearer {token}', 'User-Agent': 'android:com.reddit.wearos:v1.0.0'})
data = json.loads(urllib.request.urlopen(req).read().decode('utf-8'))

# Definición de modelo Kotlin:
# data class TreeLineSegment(val top: Boolean, val bottom: Boolean, val horiz: Boolean)
# data class FlatComment(val comment: RedditComment, val depth: Int, val segments: List<TreeLineSegment>)

class TreeLineSegment:
    def __init__(self, top=False, bottom=False, horiz=False):
        self.top = top
        self.bottom = bottom
        self.horiz = horiz
    def __repr__(self):
        return f"Seg(T={self.top}, B={self.bottom}, H={self.horiz})"

def flatten_comments(comments, depth=0, active_ancestors=[]):
    result = []
    for i, c in enumerate(comments):
        if c.get('kind') != 't1': continue
        d = c['data']
        is_first = (i == 0)
        is_last = (i == len(comments) - 1)
        replies_raw = d.get('replies')
        replies = []
        if isinstance(replies_raw, dict):
            replies = [x for x in replies_raw['data']['children'] if x.get('kind') == 't1']
        has_replies = len(replies) > 0
        
        segments = []
        # Para cada columna 0..depth-2 (ancestros)
        for col_idx in range(depth - 1):
            is_active = (col_idx < len(active_ancestors) and active_ancestors[col_idx])
            segments.append(TreeLineSegment(top=is_active, bottom=is_active, horiz=False))
            
        # Para la columna depth - 1 (propia)
        if depth > 0:
            top_line = not is_first
            bottom_line = not is_last
            segments.append(TreeLineSegment(top=top_line, bottom=bottom_line, horiz=True))
            
        result.append({
            'author': d.get('author'),
            'depth': depth,
            'segments': segments
        })
        
        if has_replies:
            next_ancestors = active_ancestors + [not is_last] if depth > 0 else []
            result.extend(flatten_comments(replies, depth + 1, next_ancestors))
    return result

top_comments = data[1]['data']['children']
flat = flatten_comments(top_comments, depth=0)

# Verificación matemática 100% de la lista plana
errors = 0
for i in range(len(flat) - 1):
    curr = flat[i]
    nxt = flat[i + 1]
    
    max_cols = max(len(curr['segments']), len(nxt['segments']))
    for col in range(max_cols):
        curr_b = curr['segments'][col].bottom if col < len(curr['segments']) else False
        nxt_t = nxt['segments'][col].top if col < len(nxt['segments']) else False
        if curr_b != nxt_t:
            print(f"[ERROR] #{i} ({curr['author']}) -> #{i+1} ({nxt['author']}) col {col}: curr.bottom={curr_b} != nxt.top={nxt_t}")
            errors += 1

print(f"Total comentarios: {len(flat)}")
print(f"Total errores de enlace: {errors}")
