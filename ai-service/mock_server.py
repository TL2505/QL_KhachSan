"""Day-1 development stub: loopback only, synthetic fixtures, never production."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from datetime import date, timedelta
import json


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass  # Do not log payloads or document images.

    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        if length > 14_000_000:
            self.send_error(413)
            return
        try:
            body = json.loads(self.rfile.read(length))
            if self.path == "/ocr":
                result = {"id_card": "0" * 12, "full_name": "SYNTHETIC MOCK GUEST", "dob": "2000-01-01", "address": "SYNTHETIC MOCK"}
            elif self.path == "/face-match":
                result = {"match": False, "confidence": 0, "recommendation": "MANUAL_REVIEW"}
            elif self.path == "/rag/query":
                result = {"answer": "SYNTHETIC DEVELOPMENT FIXTURE", "citations": [{"source": "SYNTHETIC-NOT-PMS", "text": "SYNTHETIC DEVELOPMENT FIXTURE"}], "confidence": .9, "escalate": False}
            elif self.path == "/forecast":
                start, days = date.fromisoformat(body["start_date"]), int(body["days"])
                if not 1 <= days <= 90:
                    raise ValueError()
                result = [{"date": str(start + timedelta(days=i)), "predicted_occupancy": 50,
                           "confidence_interval": [40, 60], "recommended_multiplier": 1.0} for i in range(days)]
            else:
                self.send_error(404)
                return
            payload = json.dumps(result).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("X-Synthetic-Fixture", "true")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)
        except (ValueError, KeyError, TypeError):
            self.send_error(400, "Invalid synthetic request")


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", 8089), Handler).serve_forever()
