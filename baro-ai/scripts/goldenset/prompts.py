"""골든셋 두 팔(Before/After)의 프롬프트와 Gemini 응답 스키마.

Before = 현재 운영 중인 프롬프트·스키마를 그대로 옮긴 것이다.
  출처: recommend/infrastructure/llm/RecipeCandidateGenerator.buildRecipeCandidateTemplate
       recommend/infrastructure/llm/RecipeCandidateDraft (필드 8개)
  기준선이 목적이므로 문장 하나도 고치지 않는다. Java 쪽이 바뀌면 여기도 같은 턴에 맞춘다.

After = docs/specs/cart-recipe/recipe-quality-redesign.md "새 프롬프트 초안"(Approved).
  아직 Java에 반영되지 않았다. 이 스크립트가 반영 전에 효과를 재기 위한 것이다.
"""

# BeanOutputConverter가 만드는 스키마를 그대로 흉내낸다 — 모든 필드가 required다.
# 필드를 하나라도 빼면 Before 팔이 현행과 다른 조건이 되어 기준선으로 못 쓴다.
_STR = {"type": "STRING"}
_STR_LIST = {"type": "ARRAY", "items": {"type": "STRING"}}

BEFORE_DRAFT_FIELDS = [
    ("cookingMethod", {"type": "STRING", "enum": ["ONE_POT", "STIR_FRY", "NO_COOK", "OVEN_BAKE"]}),
    ("difficulty", {"type": "STRING", "enum": ["EASY", "NORMAL", "CHALLENGE"]}),
    ("mealType", {"type": "STRING", "enum": ["MAIN", "SIDE", "SOUP"]}),
    ("recipeName", _STR),
    ("recipeIngredientsCore", _STR_LIST),
    ("recipeIngredientsExtra", _STR_LIST),
    ("instructions", _STR),
    ("staplesAssumed", {"type": "BOOLEAN"}),
]

# 재설계 후 남는 3개 (recipe-quality-redesign.md "데이터 모델")
AFTER_DRAFT_FIELDS = [
    ("recipeName", _STR),
    ("recipeIngredientsCore", _STR_LIST),
    ("instructions", _STR),
]


def _schema(fields):
    """후보 배열을 감싸는 최상위 스키마를 만든다 (RecipeCandidateDrafts에 대응)."""
    draft = {
        "type": "OBJECT",
        "properties": {name: spec for name, spec in fields},
        "required": [name for name, _ in fields],
        "propertyOrdering": [name for name, _ in fields],
    }
    return {
        "type": "OBJECT",
        "properties": {"candidates": {"type": "ARRAY", "items": draft}},
        "required": ["candidates"],
    }


BEFORE_SCHEMA = _schema(BEFORE_DRAFT_FIELDS)
AFTER_SCHEMA = _schema(AFTER_DRAFT_FIELDS)


BEFORE_TEMPLATE = """당신은 '집밥 레시피 추천 AI'입니다.
아래 '보유 재료'를 활용하여 만들 수 있는 실제 요리 레시피 3가지를 추천합니다.

<보유 재료(ownedIngredients)>
{ownedIngredients}

<정책>
- 기본 양념(물, 소금, 후추, 식용유, 설탕, 식초, 간장 등)은 사용자가 이미 보유하고 있다고 가정합니다.
- 따라서 기본 양념은 'recipeIngredientsCore'나 'recipeIngredientsExtra' 목록에 포함시키지 마세요.

<재료 조합 가이드>
- '보유 재료'들이 서로 잘 어울리지 않는 경우, 모든 재료를 한 번에 사용하려고 하지 마세요.
- 대신, '보유 재료'의 일부만 사용하여 현실적이고 맛있는 요리를 만드세요.
- 예시: '보유 재료'가 ["사과", "계란"]일 때, "사과 계란 찌개" 같은 이상한 요리 대신 "애플파이"(사과 사용)나 "계란찜"(계란 사용)을 제안하는 것이 훨씬 좋습니다.
- 핵심은 '억지 조합'이 아닌 '현실적인 요리'를 추천하는 것입니다.

<후보 생성 규칙>
- 판매 기회를 고려하여, 완벽한 재료가 아닌 '적절히 부족한' 레시피를 우선 생성하세요.
- 'recipeIngredientsCore' 재료의 30-50% 정도는 '보유 재료'에 없는 새로운 재료로 구성하여, 사용자에게 추가 구매를 자연스럽게 유도하세요.
- 반드시 3개의 독립적인 레시피 후보를 생성해야 합니다.
- 각 레시피는 한식, 양식, 중식, 일식 등 다양한 스타일을 가져야 합니다.
- cookingMethod/difficulty/mealType 조합은 후보마다 달라야 합니다.
- 각 후보 레시피는 '보유 재료' 중 최소 1개는 반드시 활용해야 합니다.
- 'recipeIngredientsCore'에는 해당 요리의 정체성을 나타내는 가장 중요한 재료들을 3~5개 선정하세요.
- 'recipeIngredientsExtra'에는 있으면 요리의 풍미를 더하지만 필수적이지는 않은 재료들을 1~3개 선정하세요.
- 억지스러운 재료 조합을 피하고, 실제로 사람들이 즐겨 만드는 일반적인 요리를 제안해야 합니다.
- 재료명은 일반적인 한 단어 명사(예: "돼지고기", "김치", "양파")로 통일하세요. '보유 재료'에 있는 이름에 얽매일 필요 없습니다.

<현실성 검증>
- 제안하는 레시피(recipeName)가 실제로 존재하는 요리인지 반드시 확인하세요.
- 재료 조합이 현실적인지 확인하세요. (예: '삼겹살'과 '양파'는 볶음 요리에 잘 어울리지만, '삼겹살'과 '사과'로 찌개를 만드는 것은 비현실적입니다.)

<출력 필드 정의>
- cookingMethod: 조리 방식 ("ONE_POT", "STIR_FRY", "NO_COOK", "OVEN_BAKE")
- difficulty: 난이도 ("EASY", "NORMAL", "CHALLENGE")
- mealType: 식사 유형 ("MAIN", "SIDE", "SOUP")
- recipeName: 요리 이름 (예: "돼지고기 김치찌개")
- recipeIngredientsCore: 요리의 정체성을 결정하는 핵심 재료 (3~5개).
- recipeIngredientsExtra: 있으면 더 좋은 추가 재료 (1~3개).
- instructions: 3~6 단계의 간단한 조리법.
- staplesAssumed: 기본 양념을 가정했는지 여부 (항상 true로 설정).
"""


AFTER_TEMPLATE = """당신은 '집밥 레시피 추천 AI'입니다.
아래 '보유 재료'로 만들 수 있는 실제 요리 레시피 1가지를 추천합니다.

<보유 재료>
{ownedIngredients}

<지켜야 할 것>
1. 실제로 사람들이 만들어 먹는 요리만 추천합니다. 없는 요리를 지어내지 마세요.
   보유 재료가 서로 어울리지 않으면 전부 쓰려 하지 말고 일부만 쓰세요.
   예: ["시금치", "땅콩"]이면 "시금치 땅콩 찌개"가 아니라 "시금치나물"이나 "땅콩조림"입니다.
2. 보유 재료 중 최소 1개는 반드시 씁니다.
3. 부족한 핵심 재료가 최소 1개는 반드시 있어야 합니다. 사용자가 추가로 살 재료를
   제안하기 위해서입니다. 보유 재료만으로 완성되는 요리라면 그 요리 대신, 보유 재료를
   살리면서 한두 가지가 더 필요한 요리를 고르세요.
   예: ["쌀", "보리"]면 "보리밥"이 아니라 "콩보리밥"이나 "보리비빔밥"입니다.
   다만 개수를 맞추려고 어울리지 않는 재료를 넣지는 마세요.
4. 기본 양념(물·소금·후추·식용유·설탕·식초·간장 등)은 이미 있다고 가정하고
   재료 목록에 넣지 않습니다.
5. 재료명은 일반적인 한 단어 명사로 씁니다. 예: "돼지고기", "김치", "양파"

<출력 값>
- recipeName: 요리 이름
- recipeIngredientsCore: 요리의 정체성을 정하는 핵심 재료 3~5개
- instructions: 3~6단계 조리법
"""


# 후속 라운드(specs/cart-recipe/pantry-staples-and-catalog-coverage.md 갈래 A) 측정용.
# AFTER_TEMPLATE에서 규칙 4의 기본 양념 목록만 넓힌다 — 나머지 문장은 한 글자도 바꾸지 않아야
# 이 팔의 차이를 '양념 목록 때문'이라고 말할 수 있다. 그래서 새로 쓰지 않고 치환으로 만든다.
_PANTRY_OLD_RULE = """4. 기본 양념(물·소금·후추·식용유·설탕·식초·간장 등)은 이미 있다고 가정하고
   재료 목록에 넣지 않습니다."""
_PANTRY_NEW_RULE = """4. 기본 양념은 이미 있다고 가정하고 재료 목록에 넣지 않습니다.
   기본 양념: 물·소금·후추·식용유·설탕·식초·간장·된장·고추장·고춧가루·참기름·들기름·
   마늘·다진마늘·액젓"""

if _PANTRY_OLD_RULE not in AFTER_TEMPLATE:
    raise AssertionError("AFTER_TEMPLATE의 규칙 4가 바뀌었다. _PANTRY_OLD_RULE을 맞춰야 한다")
PANTRY_TEMPLATE = AFTER_TEMPLATE.replace(_PANTRY_OLD_RULE, _PANTRY_NEW_RULE)


# 2026-09-07 이전 프롬프트를 재현하는 팔이다. 그때 규칙 1의 예시는 ["사과", "계란"] → "애플파이"였는데,
# 과일은 recipe-recommend.yml의 제외 규칙(AGRI_FRUIT* 4종)에 걸려 보유 재료로 들어올 수 없다 —
# 일어날 수 없는 상황을 예시로 가르치고 있었다. 예시만 파는 카테고리로 바꿨고(감사 cart-recipe/007 A-1),
# 그 교체가 품질을 움직이지 않았다는 비교를 다시 돌릴 수 있게 옛 문구를 이 팔로 남긴다.
_FRUIT_EXAMPLE_NEW_RULE = """   예: ["사과", "계란"]이면 "사과 계란 찌개"가 아니라 "애플파이"나 "계란찜"입니다."""
_FRUIT_EXAMPLE_OLD_RULE = """   예: ["시금치", "땅콩"]이면 "시금치 땅콩 찌개"가 아니라 "시금치나물"이나 "땅콩조림"입니다."""

if _FRUIT_EXAMPLE_OLD_RULE not in AFTER_TEMPLATE:
    raise AssertionError("AFTER_TEMPLATE의 규칙 1 예시가 바뀌었다. _FRUIT_EXAMPLE_OLD_RULE을 맞춰야 한다")
FRUIT_EXAMPLE_TEMPLATE = AFTER_TEMPLATE.replace(_FRUIT_EXAMPLE_OLD_RULE, _FRUIT_EXAMPLE_NEW_RULE)


ARMS = {
    "before": {"template": BEFORE_TEMPLATE, "schema": BEFORE_SCHEMA},
    "after": {"template": AFTER_TEMPLATE, "schema": AFTER_SCHEMA},
    "pantry": {"template": PANTRY_TEMPLATE, "schema": AFTER_SCHEMA},
    "fruit-example": {"template": FRUIT_EXAMPLE_TEMPLATE, "schema": AFTER_SCHEMA},
}


def build_prompt(arm: str, owned: list[str]) -> str:
    """보유 재료 목록을 Java와 같은 방식('- ' 접두 + 줄바꿈)으로 끼워 넣는다."""
    owned_text = "\n".join("- " + name for name in owned)
    return ARMS[arm]["template"].replace("{ownedIngredients}", owned_text)
