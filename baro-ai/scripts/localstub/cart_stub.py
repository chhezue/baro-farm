"""baro-shopping 장바구니 API 흉내 — 레시피 추천 경로를 로컬에서 끝까지 돌리기 위한 최소 스텁.

CartClient가 부르는 경로 하나만 응답한다. 담긴 상품은 로컬 ES에 심어 둔 임시 상품에서 고른다.
"""
import json, os, sys
from http.server import BaseHTTPRequestHandler, HTTPServer

# 색인해 둔 상품 목록. 다른 곳에 두었으면 STUB_PRODUCTS로 경로를 넘긴다.
CATALOG = os.environ.get("STUB_PRODUCTS",
                         os.path.join(os.path.dirname(os.path.abspath(__file__)), "seeded-products.json"))
if not os.path.exists(CATALOG):
    sys.exit(f"상품 목록이 없습니다: {CATALOG}\n  STUB_PRODUCTS=/경로/seeded-products.json 로 지정하세요.")
PRODUCTS = json.load(open(CATALOG, encoding="utf-8"))
WANT = [n.strip() for n in (sys.argv[2].split(",") if len(sys.argv) > 2 else ["사과", "계란"])]

def pick(keyword):
    for p in PRODUCTS:
        if keyword in p["productName"]:
            return p
    return None

ITEMS = [p for p in (pick(w) for w in WANT) if p]

class H(BaseHTTPRequestHandler):
    def do_GET(self):
        body = json.dumps({"status": 200, "message": None, "data": {
            "cartId": "cart-local", "buyerId": "u", "totalPrice": 0,
            "items": [{"itemId": f"i{i}", "productId": p["productId"], "productName": p["productName"],
                       "productCategoryName": p["categoryCode"], "productCategoryCode": p["categoryCode"],
                       "quantity": 1, "unitPrice": 1000, "lineTotalPrice": 1000,
                       "inventoryId": "inv", "unit": "개"}
                      for i, p in enumerate(ITEMS)]}}, ensure_ascii=False).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json;charset=UTF-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)
    def log_message(self, *a): pass

if __name__ == "__main__":
    print("담은 상품:", [p["productName"] + f"({p['categoryCode']})" for p in ITEMS], flush=True)
    HTTPServer(("127.0.0.1", int(sys.argv[1])), H).serve_forever()
