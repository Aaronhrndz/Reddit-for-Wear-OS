import urllib.request
import json

token = 'eyJhbGciOiJSUzI1NiIsImtpZCI6IlNIQTI1NjpzS3dsMnlsV0VtMjVmcXhwTU40cWY4MXE2OWFFdWFyMnpLMUdhVGxjdWNZIiwidHlwIjoiSldUIn0.eyJzdWIiOiJ1c2VyIiwiZXhwIjoxNzg3NjkwNjU0Ljk1OTg2MiwiaWF0IjoxNzg3NjA0MjU0Ljk1OTg2MiwianRpIjoiZ0stclVtN3hkcGhlcXJZVXBDVmdXeVhrWTJuZ0J3IiwiY2lkIjoiMFItV0FNaHVvby1NeVEiLCJsaWQiOiJ0Ml84cDZzZXVodyIsImFpZCI6InQyXzhwNnNldWh3IiwiYXQiOjEsImxjYSI6MTYwNDE1NTc2MjAwMCwic2NwIjoiZUp4a2tkR090REFJaGQtRmE1X2dmNVVfbTAxdGNZYXNMUWFvazNuN0RWb2NrNzA3Y0Q0cEhQOURLb3FGRENaWGdxbkFCRmdUclREQlJ1VDluTG0zZzJpTmU4dFlzWm5DQkZtd0ZEcmttTEdzaVFRbWVKSWF5eHNtb0lMTnlGeXV0R05OTFQwUUpxaGNNcmVGSHBjMm9ia2JpNTZkR0ZXNXJEeW9zVmZsMHRqR0ZMWW54amNicXcycHVDNm5Na25MUXZrc1h2VGpOOVczOXZtel9TYTBKOE9LcXVtQjNobEpDRzRzZnBpbTNkOVRrNTZ0Q3hhMTkzcVEydWQ2M0s1OTFpdzBPN2VmNl9sckl4bVhZMmgtSnZ0MzF5LWhBNDg4THpQcUFFYXM0VWNaZG1RZF9sVUhVTG1nSkdNSjR0TUk1TXJsMjM4SnRtdlR2OGJ0RXo5OE0tS21OX3pXRE5SekNlTFFwX0gxR3dBQV9fOFExZVRSIiwicmNpZCI6IkxPSzR3N1RHYm5OMzRFSjRjS1hvLTVZXzFVSXQ3bXVzQzA0dzg1Vk9reDAiLCJmbG8iOjJ9.pTuJGhob9-4Q_GZ53uvzu4D0VwgkDVboxgBRmIDZU1HiKkqhJeUeU4dcLSwY_kvx8ApK_dztJpjU1Lz-Fz_hpgun0rUNopo99-FxtcYRpfn_8VslPD8XsQSD6JWctBOXXAb6daCTCGIklouKrEZcWj-UL8uKo-9H9qXMRD1G2YD5-8XFvUXjBzKZCc2s4WZaUAnKteoInlvt0KkL51GjfCTppnVEA5VBUEBa1bxSIRze2p_hyoZWXc7uRUAO0GCtJ18inpm375Q6bi3caLAyLn9e0h_V_Uisb55Jli6paKwaoqojdgmDw-6xVEd2Z4WcB7oWzR5XRByms_SBOMK5LA'

req = urllib.request.Request(
    'https://oauth.reddit.com/r/worldnews/comments/1vxra8b.json?raw_json=1',
    headers={'Authorization': f'Bearer {token}', 'User-Agent': 'android:com.reddit.wearos:v1.0.0'}
)
data = json.loads(urllib.request.urlopen(req).read().decode('utf-8'))

class ColumnState:
    NONE = 0
    PASS_THROUGH = 1   # top=True, bottom=True, horiz=False
    TURN_LAST = 2      # top=True, bottom=False, horiz=True  (\--)
    TURN_CONTINUE = 3  # top=True, bottom=True, horiz=True   (+--)

class ItemDrawing:
    def __init__(self, author, depth, col_states):
        self.author = author
        self.depth = depth
        self.col_states = col_states # dict: col -> ColumnState

    def exits_bottom(self, col):
        st = self.col_states.get(col, ColumnState.NONE)
        return st in (ColumnState.PASS_THROUGH, ColumnState.TURN_CONTINUE)

    def enters_top(self, col):
        st = self.col_states.get(col, ColumnState.NONE)
        return st in (ColumnState.PASS_THROUGH, ColumnState.TURN_LAST, ColumnState.TURN_CONTINUE)

def compute_drawings(children, depth=0, active_ancestors=[]):
    drawings = []
    for i, c in enumerate(children):
        if c.get('kind') != 't1': continue
        d = c['data']
        is_last = (i == len(children) - 1)
        replies_raw = d.get('replies')
        replies = []
        if isinstance(replies_raw, dict):
            replies = [x for x in replies_raw['data']['children'] if x.get('kind') == 't1']
        has_replies = len(replies) > 0
        
        # Calcular estados de columnas para este item
        col_states = {}
        # 1. Ancestros
        for col_idx, is_active in enumerate(active_ancestors):
            if is_active:
                col_states[col_idx] = ColumnState.PASS_THROUGH
            else:
                col_states[col_idx] = ColumnState.NONE
        # 2. Columna propia si depth > 0
        if depth > 0:
            my_col = depth - 1
            if is_last:
                col_states[my_col] = ColumnState.TURN_LAST
            else:
                col_states[my_col] = ColumnState.TURN_CONTINUE
                
        drawings.append(ItemDrawing(d.get('author'), depth, col_states))
        
        if has_replies:
            # Para los descendientes: la columna depth-1 permanece activa si este elemento NO es el último de su nivel
            # Para depth=0 (top-level), depth-1 no existe, la columna 0 empezará con los hijos directos
            if depth == 0:
                next_ancestors = []
            else:
                next_ancestors = active_ancestors + [not is_last]
            drawings.extend(compute_drawings(replies, depth + 1, next_ancestors))
    return drawings

top_comments = data[1]['data']['children']
drawings = compute_drawings(top_comments, depth=0)

print(f"Total comentarios: {len(drawings)}")
print("="*60)
print("AUDITORIA DE CONTINUIDAD MATEMATICA:")
print("="*60)

errors = 0
max_depth = max(d.depth for d in drawings)
for i in range(len(drawings) - 1):
    curr = drawings[i]
    nxt = drawings[i + 1]
    
    for col in range(max_depth):
        out_b = curr.exits_bottom(col)
        in_t = nxt.enters_top(col)
        if out_b != in_t:
            print(f"[ERROR] Discontinuidad entre #{i} (u/{curr.author}, d={curr.depth}) y #{i+1} (u/{nxt.author}, d={nxt.depth}) en columna {col}:")
            print(f"        #{i} exits_bottom={out_b}, #{i+1} enters_top={in_t}")
            errors += 1

if errors == 0:
    print("[PERFECTO] 100% Continuidad Matematica: 0 Errores en todo el arbol!")
else:
    print(f"Total errores: {errors}")
