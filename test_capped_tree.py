import urllib.request, json

token = 'eyJhbGciOiJSUzI1NiIsImtpZCI6IlNIQTI1NjpzS3dsMnlsV0VtMjVmcXhwTU40cWY4MXE2OWFFdWFyMnpLMUdhVGxjdWNZIiwidHlwIjoiSldUIn0.eyJzdWIiOiJ1c2VyIiwiZXhwIjoxNzg3NjkwNjU0Ljk1OTg2MiwiaWF0IjoxNzg3NjA0MjU0Ljk1OTg2MiwianRpIjoiZ0stclVtN3hkcGhlcXJZVXBDVmdXeVhrWTJuZ0J3IiwiY2lkIjoiMFItV0FNaHVvby1NeVEiLCJsaWQiOiJ0Ml84cDZzZXVodyIsImFpZCI6InQyXzhwNnNldWh3IiwiYXQiOjEsImxjYSI6MTYwNDE1NTc2MjAwMCwic2NwIjoiZUp4a2tkR090REFJaGQtRmE1X2dmNVVfbTAxdGNZYXNMUWFvazNuN0RWb2NrNzA3Y0Q0cEhQOURLb3FGRENaWGdxbkFCRmdUclREQlJ1VDluTG0zZzJpTmU4dFlzWm5DQkZtd0ZEcmttTEdzaVFRbWVKSWF5eHNtb0lMTnlGeXV0R05OTFQwUUpxaGNNcmVGSHBjMm9ia2JpNTZkR0ZXNXJEeW9zVmZsMHRqR0ZMWW54amNicXcycHVDNm5Na25MUXZrc1h2VGpOOVczOXZtel9TYTBKOE9LcXVtQjNobEpDRzRzZnBpbTNkOVRrNTZ0Q3hhMTkzcVEydWQ2M0s1OTFpdzBPN2VmNl9sckl4bVhZMmgtSnZ0MzF5LWhBNDg4THpQcUFFYXM0VWNaZG1RZF9sVUhVTG1nSkdNSjR0TUk1TXJsMjM4SnRtdlR2OGJ0RXo5OE0tS21OX3pXRE5SekNlTFFwX0gxR3dBQV9fOFExZVRSIiwicmNpZCI6IkxPSzR3N1RHYm5OMzRFSjRjS1hvLTVZXzFVSXQ3bXVzQzA0dzg1Vk9reDAiLCJmbG8iOjJ9.pTuJGhob9-4Q_GZ53uvzu4D0VwgkDVboxgBRmIDZU1HiKkqhJeUeU4dcLSwY_kvx8ApK_dztJpjU1Lz-Fz_hpgun0rUNopo99-FxtcYRpfn_8VslPD8XsQSD6JWctBOXXAb6daCTCGIklouKrEZcWj-UL8uKo-9H9qXMRD1G2YD5-8XFvUXjBzKZCc2s4WZaUAnKteoInlvt0KkL51GjfCTppnVEA5VBUEBa1bxSIRze2p_hyoZWXc7uRUAO0GCtJ18inpm375Q6bi3caLAyLn9e0h_V_Uisb55Jli6paKwaoqojdgmDw-6xVEd2Z4WcB7oWzR5XRByms_SBOMK5LA'
req = urllib.request.Request('https://oauth.reddit.com/r/worldnews/comments/1vxra8b.json?raw_json=1', headers={'Authorization': f'Bearer {token}', 'User-Agent': 'android:com.reddit.wearos:v1.0.0'})
data = json.loads(urllib.request.urlopen(req).read().decode('utf-8'))

class Segment:
    def __init__(self, top=False, bottom=False, horiz=False):
        self.top = top
        self.bottom = bottom
        self.horiz = horiz

class RowDrawing:
    def __init__(self, author, depth, cols):
        self.author = author
        self.depth = depth
        self.cols = cols

def build_tree_capped(children, raw_depth=0, active_ancestors=[]):
    rows = []
    # En Wear OS, depth visible se capea a max 3 columnas
    for i, c in enumerate(children):
        if c.get('kind') != 't1': continue
        d = c['data']
        is_first = (i == 0)
        is_last = (i == len(children) - 1)
        replies_raw = d.get('replies')
        replies = []
        if isinstance(replies_raw, dict):
            replies = [x for x in replies_raw['data']['children'] if x.get('kind') == 't1']
        has_replies = len(replies) > 0
        
        vis_depth = min(raw_depth, 3)
        cols = {}
        
        # 1. Ancestros visibles (columnas 0 hasta vis_depth - 2)
        for col_idx in range(vis_depth - 1):
            if col_idx < len(active_ancestors) and active_ancestors[col_idx]:
                cols[col_idx] = Segment(top=True, bottom=True, horiz=False)
                
        # 2. Columna propia si vis_depth > 0
        if vis_depth > 0:
            my_col = vis_depth - 1
            top_line = not is_first
            bottom_line = not is_last
            cols[my_col] = Segment(top=top_line, bottom=bottom_line, horiz=True)
            
        rows.append(RowDrawing(d.get('author'), vis_depth, cols))
        
        if has_replies:
            if raw_depth == 0:
                next_ancestors = []
            else:
                next_ancestors = active_ancestors + [not is_last]
            rows.extend(build_tree_capped(replies, raw_depth + 1, next_ancestors))
    return rows

top_comments = data[1]['data']['children']
rows = build_tree_capped(top_comments, raw_depth=0)

errors = 0
for i in range(len(rows) - 1):
    curr = rows[i]
    nxt = rows[i + 1]
    for col in range(3):
        curr_seg = curr.cols.get(col, Segment())
        nxt_seg = nxt.cols.get(col, Segment())
        if curr_seg.bottom != nxt_seg.top:
            print(f"[CAPPED ERROR] #{i} (u/{curr.author}, d={curr.depth}) -> #{i+1} (u/{nxt.author}, d={nxt.depth}) en col {col}: curr.bottom={curr_seg.bottom} != nxt.top={nxt_seg.top}")
            errors += 1

print(f"Errores con depth capeado a 3: {errors}")
