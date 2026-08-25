import urllib.request
import json

token = 'eyJhbGciOiJSUzI1NiIsImtpZCI6IlNIQTI1NjpzS3dsMnlsV0VtMjVmcXhwTU40cWY4MXE2OWFFdWFyMnpLMUdhVGxjdWNZIiwidHlwIjoiSldUIn0.eyJzdWIiOiJ1c2VyIiwiZXhwIjoxNzg3NjkwNjU0Ljk1OTg2MiwiaWF0IjoxNzg3NjA0MjU0Ljk1OTg2MiwianRpIjoiZ0stclVtN3hkcGhlcXJZVXBDVmdXeVhrWTJuZ0J3IiwiY2lkIjoiMFItV0FNaHVvby1NeVEiLCJsaWQiOiJ0Ml84cDZzZXVodyIsImFpZCI6InQyXzhwNnNldWh3IiwiYXQiOjEsImxjYSI6MTYwNDE1NTc2MjAwMCwic2NwIjoiZUp4a2tkR090REFJaGQtRmE1X2dmNVVfbTAxdGNZYXNMUWFvazNuN0RWb2NrNzA3Y0Q0cEhQOURLb3FGRENaWGdxbkFCRmdUclREQlJ1VDluTG0zZzJpTmU4dFlzWm5DQkZtd0ZEcmttTEdzaVFRbWVKSWF5eHNtb0lMTnlGeXV0R05OTFQwUUpxaGNNcmVGSHBjMm9ia2JpNTZkR0ZXNXJEeW9zVmZsMHRqR0ZMWW54amNicXcycHVDNm5Na25MUXZrc1h2VGpOOVczOXZtel9TYTBKOE9LcXVtQjNobEpDRzRzZnBpbTNkOVRrNTZ0Q3hhMTkzcVEydWQ2M0s1OTFpdzBPN2VmNl9sckl4bVhZMmgtSnZ0MzF5LWhBNDg4THpQcUFFYXM0VWNaZG1RZF9sVUhVTG1nSkdNSjR0TUk1TXJsMjM4SnRtdlR2OGJ0RXo5OE0tS21OX3pXRE5SekNlTFFwX0gxR3dBQV9fOFExZVRSIiwicmNpZCI6IkxPSzR3N1RHYm5OMzRFSjRjS1hvLTVZXzFVSXQ3bXVzQzA0dzg1Vk9reDAiLCJmbG8iOjJ9.pTuJGhob9-4Q_GZ53uvzu4D0VwgkDVboxgBRmIDZU1HiKkqhJeUeU4dcLSwY_kvx8ApK_dztJpjU1Lz-Fz_hpgun0rUNopo99-FxtcYRpfn_8VslPD8XsQSD6JWctBOXXAb6daCTCGIklouKrEZcWj-UL8uKo-9H9qXMRD1G2YD5-8XFvUXjBzKZCc2s4WZaUAnKteoInlvt0KkL51GjfCTppnVEA5VBUEBa1bxSIRze2p_hyoZWXc7uRUAO0GCtJ18inpm375Q6bi3caLAyLn9e0h_V_Uisb55Jli6paKwaoqojdgmDw-6xVEd2Z4WcB7oWzR5XRByms_SBOMK5LA'

# 1. Descargar árbol real del post
req = urllib.request.Request(
    'https://oauth.reddit.com/r/worldnews/comments/1vxra8b.json?raw_json=1',
    headers={'Authorization': f'Bearer {token}', 'User-Agent': 'android:com.reddit.wearos:v1.0.0'}
)
data = json.loads(urllib.request.urlopen(req).read().decode('utf-8'))

# 2. Algoritmo canónico de árbol para listas planas:
# En un árbol de n niveles (como `tree` en Linux o exploradores de archivos):
# Cada nivel `d` mantiene un booleano: ¿hay más elementos en este nivel más abajo?
# ancestors_active = [bool, bool, ...] donde ancestors_active[k] indica si el nivel k tiene líneas verticales continuas.

class Node:
    def __init__(self, author, depth, is_last, has_replies, active_ancestors):
        self.author = author
        self.depth = depth
        self.is_last = is_last
        self.has_replies = has_replies
        self.active_ancestors = list(active_ancestors) # columnas 0..depth-2 que pasan recto
        
    def get_lines_top(self):
        # Columnas activas en la parte superior (y=0 a cornerY)
        # Columnas 0..depth-2 que pasan recto + la columna de entrada de este nodo (depth-1)
        enters = set()
        for col, active in enumerate(self.active_ancestors):
            if active: enters.add(col)
        if self.depth > 0:
            enters.add(self.depth - 1)
        return enters

    def get_lines_bottom(self):
        # Columnas activas en la parte inferior (cornerY a height)
        # 1. Ancestros que siguen activos
        exits = set()
        for col, active in enumerate(self.active_ancestors):
            if active: exits.add(col)
        # 2. Si este nodo NO es el último de su nivel, su columna (depth-1) continúa hacia abajo
        if self.depth > 0 and not self.is_last:
            exits.add(self.depth - 1)
        return exits

def flatten(children, depth=0, active_ancestors=[]):
    items = []
    for i, c in enumerate(children):
        if c.get('kind') != 't1': continue
        d = c['data']
        is_last = (i == len(children) - 1)
        replies_raw = d.get('replies')
        replies = []
        if isinstance(replies_raw, dict):
            replies = [x for x in replies_raw['data']['children'] if x.get('kind') == 't1']
        has_replies = len(replies) > 0
        
        node = Node(
            author=d.get('author'),
            depth=depth,
            is_last=is_last,
            has_replies=has_replies,
            active_ancestors=active_ancestors
        )
        items.append(node)
        
        if has_replies:
            # Para los hijos, este nivel (depth) añade si tiene más hermanos después de él
            next_active = active_ancestors + [not is_last]
            items.extend(flatten(replies, depth + 1, next_active))
    return items

top_comments = data[1]['data']['children']
flat_tree = flatten(top_comments, depth=0)

print(f"Total comentarios procesados: {len(flat_tree)}")
print("="*80)
print("AUDITORÍA DE CONTINUIDAD VERTICAL MATEMÁTICA (Columna por Columna):")
print("="*80)

errors = 0
for i in range(len(flat_tree) - 1):
    curr = flat_tree[i]
    nxt = flat_tree[i + 1]
    
    curr_exits = curr.get_lines_bottom()
    nxt_enters = nxt.get_lines_top()
    
    # Comprobar si todas las columnas coinciden entre la salida del actual y la entrada del siguiente
    # Nota: solo comprobamos columnas hasta min(max_col_curr, max_col_nxt)
    all_cols = curr_exits.union(nxt_enters)
    for col in all_cols:
        in_curr = col in curr_exits
        in_nxt = col in nxt_enters
        if in_curr != in_nxt:
            print(f"[FAIL] DISCONTINUIDAD en transicion #{i} -> #{i+1}:")
            print(f"   [{i}] u/{curr.author} (depth={curr.depth}, is_last={curr.is_last}) EXITS_BOTTOM = {sorted(list(curr_exits))}")
            print(f"   [{i+1}] u/{nxt.author} (depth={nxt.depth}, is_last={nxt.is_last}) ENTERS_TOP = {sorted(list(nxt_enters))}")
            print(f"   -> En columna {col}: curr_exits={in_curr} pero nxt_enters={in_nxt}")
            errors += 1

if errors == 0:
    print("[SUCCESS] CONTINUIDAD 100% PERFECTA: Cero desconexiones en todo el hilo de Reddit.")
else:
    print(f"Total errores detectados: {errors}")

# Imprimir los primeros 25 comentarios en ASCII puro
print("\nMAPA VISUAL ASCII EXACTO (Primeros 25 comentarios):")
for i, node in enumerate(flat_tree[:25]):
    prefix = ""
    for col in range(node.depth):
        if col < node.depth - 1:
            if col < len(node.active_ancestors) and node.active_ancestors[col]:
                prefix += "|  "
            else:
                prefix += "   "
        else:
            if node.is_last:
                prefix += "\\--"
            else:
                prefix += "+--"
    print(f"{i:2d} {prefix} u/{node.author}")
