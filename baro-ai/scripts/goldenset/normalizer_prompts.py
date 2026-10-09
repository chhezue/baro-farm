"""색인 시점 재료명 추출 프롬프트의 두 팔.

- current      : 운영 `ProductIngredientNormalizer.PROMPT`. Java 파일에서 직접 읽는다
- pre-catalog  : 2026-09-06 개선 전 프롬프트(농산물만 전제). 비교 기준선으로 얼려 둔다

`current`를 옮겨 적지 않고 Java에서 읽는 이유는, 옮겨 적으면 언젠가 어긋나기 때문이다.
어긋난 프롬프트로 잰 수치는 운영에 대해 아무것도 말해 주지 않는다.
"""
import re
from pathlib import Path

JAVA_FILE = (Path(__file__).resolve().parents[2]
             / "src/main/java/com/barofarm/ai/search/infrastructure/llm/ProductIngredientNormalizer.java")


def _load_current() -> str:
    """운영 프롬프트를 Java 텍스트 블록에서 읽어 온다."""
    src = JAVA_FILE.read_text(encoding="utf-8")
    m = re.search(r'PROMPT = """\n(.*?)\n\s*""";', src, re.DOTALL)
    if not m:
        raise RuntimeError(f"{JAVA_FILE}에서 PROMPT 텍스트 블록을 찾지 못했다")
    body = m.group(1)
    # Java 텍스트 블록은 닫는 따옴표의 들여쓰기만큼 각 줄 앞을 잘라낸다. 여기서도 같게 맞춘다.
    indents = [len(l) - len(l.lstrip()) for l in body.splitlines() if l.strip()]
    cut = min(indents) if indents else 0
    return "\n".join(l[cut:] if len(l) >= cut else l for l in body.splitlines()) + "\n"


# 2026-09-06 이전 프롬프트. 김치·두부·장류를 팔기로 하면서 바꿨고, 무엇이 왜 바뀌었는지는
# docs/audits/cart-recipe/004-index-time-ingredient-name.md에 있다. 되돌아가 다시 재려면 이 팔을 쓴다.
PRE_CATALOG_TEMPLATE = """당신은 신선 농산물 이커머스 상품명에서 '요리에 쓰이는 재료명'만 뽑아내는 역할입니다.
이 쇼핑몰은 농산물만 취급하므로 입력으로 들어오는 상품은 모두 식재료입니다.

<입력>
상품명: {productName}
카테고리: {categoryName}

<규칙>
- 브랜드명, 산지, 수량, 무게, 단위, 포장 형태, 등급·마케팅 수식어는 모두 제거하세요.
- 결과는 공백 없는 한 단어의 일반 명사로 쓰세요. 예를 들어 "계란", "돼지고기", "애호박"처럼 씁니다.
- 카테고리는 힌트로만 쓰고 그대로 답으로 쓰지는 마세요.
  카테고리가 "뿌리채소"여도 답은 "감자"처럼 구체적인 재료명이어야 합니다.
- 상품명에 근거가 없는 재료를 지어내지 마세요.

<예시>
상품명 "곰곰 무항생제 신선한 대란, 30구, 1개" / 카테고리 "계란" -> "계란"
상품명 "제주 서귀포 고당도 노지 감귤 3kg (박스)" / 카테고리 "제철 과일" -> "감귤"
상품명 "횡성 한우 등심 300g 1등급" / 카테고리 "육류" -> "소고기"
"""

ARMS = {
    "current": _load_current(),
    "pre-catalog": PRE_CATALOG_TEMPLATE,
}


def build_prompt(arm: str, product_name: str, category_name: str) -> str:
    """Spring AI의 `.param()`과 같은 방식으로 자리표시자를 채운다."""
    return (ARMS[arm]
            .replace("{productName}", product_name)
            .replace("{categoryName}", category_name))
