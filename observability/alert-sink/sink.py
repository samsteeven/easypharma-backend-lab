"""Récepteur de notifications Alertmanager pour le laboratoire (bibliothèque standard uniquement).

POST /alerts     : reçoit le webhook d'Alertmanager et l'enregistre avec l'heure de réception
GET  /received   : liste des notifications reçues (JSON)
DELETE /received : vide la liste
"""
import json
import time
from http.server import BaseHTTPRequestHandler, HTTPServer

received = []


class Handler(BaseHTTPRequestHandler):
    def _send(self, code, body=b"", content_type="application/json"):
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        payload = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")
        entry = {
            "received_at_ms": int(time.time() * 1000),
            "status": payload.get("status"),
            "alerts": [
                {"alertname": a["labels"].get("alertname"), "status": a.get("status"), "startsAt": a.get("startsAt")}
                for a in payload.get("alerts", [])
            ],
        }
        received.append(entry)
        print(json.dumps(entry), flush=True)
        self._send(200, b"{}")

    def do_GET(self):
        if self.path == "/received":
            self._send(200, json.dumps(received).encode())
        else:
            self._send(404)

    def do_DELETE(self):
        received.clear()
        self._send(200, b"{}")

    def log_message(self, *args):  # silence le journal HTTP par défaut
        pass


if __name__ == "__main__":
    HTTPServer(("0.0.0.0", 8080), Handler).serve_forever()
