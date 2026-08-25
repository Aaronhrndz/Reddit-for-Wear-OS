import socket
import threading
import time
import subprocess

WATCH_IP = "192.168.1.176"
WATCH_PORT = 37093
PAIR_CODE = "229342"
LOCAL_PORT = 55555
WIFI_IP = "192.168.1.210"

def bridge(src, dst):
    try:
        while True:
            data = src.recv(4096)
            if not data:
                break
            dst.sendall(data)
    except:
        pass
    finally:
        src.close()
        dst.close()

def run_proxy():
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(("127.0.0.1", LOCAL_PORT))
    server.listen(1)
    server.settimeout(15)
    
    try:
        client_sock, _ = server.accept()
        # Salida obligada por Wi-Fi 2
        watch_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        watch_sock.bind((WIFI_IP, 0))
        watch_sock.connect((WATCH_IP, WATCH_PORT))
        
        t1 = threading.Thread(target=bridge, args=(client_sock, watch_sock))
        t2 = threading.Thread(target=bridge, args=(watch_sock, client_sock))
        t1.start()
        t2.start()
        t1.join()
        t2.join()
    except Exception as e:
        print("Proxy error:", e)
    finally:
        server.close()

proxy_thread = threading.Thread(target=run_proxy)
proxy_thread.daemon = True
proxy_thread.start()
time.sleep(0.5)

adb = r"C:\Users\aaron\tools\platform-tools\adb.exe"
print("Connecting adb pair to 127.0.0.1:" + str(LOCAL_PORT))
p = subprocess.run([adb, "pair", f"127.0.0.1:{LOCAL_PORT}", PAIR_CODE], capture_output=True, text=True, timeout=10)
print("STDOUT:", p.stdout)
print("STDERR:", p.stderr)
print("EXIT:", p.returncode)
