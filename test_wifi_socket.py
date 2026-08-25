import socket

s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
s.bind(("192.168.1.210", 0)) # Bindeo forzoso a la tarjeta Wi-Fi del PC
s.settimeout(3)
res = s.connect_ex(("192.168.1.176", 39721))
print("WiFi to Watch Connection result (0 is success):", res)
s.close()
