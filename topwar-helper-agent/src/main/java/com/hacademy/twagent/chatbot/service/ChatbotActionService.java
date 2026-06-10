package com.hacademy.twagent.chatbot.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hacademy.twagent.chatbot.vo.ActionSpec;
import com.hacademy.twagent.chatbot.vo.ChatbotRequest;
import com.hacademy.twagent.chatbot.vo.ChatbotResponse;

@Service
public class ChatbotActionService {

    @Autowired
    private ChatClient chatClient;

    @Autowired
    private ObjectMapper objectMapper;

    public ChatbotResponse parseAction(ChatbotRequest request) {
        if (request == null || !StringUtils.hasText(request.message())) {
            return messageResponse("요청 내용이 없습니다.");
        }

        if (request.actions() == null || request.actions().isEmpty()) {
            return messageResponseByLang(request.lang(), "지원 가능한 기능이 없습니다.");
        }

        String lang = normalizeLang(request.lang());
        String systemPrompt = buildSystemPrompt(lang, request.actions());
        String userPrompt = buildUserPrompt(request.message());

        String aiText;

        try {
            aiText = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();
        } catch (Exception e) {
            return messageResponseByLang(lang, "AI 요청 처리 중 오류가 발생했습니다.");
        }

        ChatbotResponse aiResponse;

        try {
            System.out.println("AI raw = " + aiText);

            aiResponse = parseAiResponse(aiText);

            System.out.println("AI parsed = " + aiResponse);
            System.out.println("AI params = " + aiResponse.params());
        } catch (Exception e) {
            return messageResponseByLang(lang, "AI 응답을 해석하지 못했습니다.");
        }

        return validateResponse(aiResponse, request.actions(), lang);
    }

    private String buildSystemPrompt(String lang, List<ActionSpec> actions) {
        String actionListText = buildActionListText(actions);

        return """
                너는 웹사이트 기능 실행을 위한 action 선택기다.

                사용자의 요청을 분석해서 제공된 action 목록 중 가장 적절한 action 하나를 선택한다.
                너는 action, path, query key를 새로 만들 수 없다.
                너는 제공된 목록에서 선택만 한다.

                사용자 언어:
                %s

                [action 선택 규칙]
                1. 제공된 action 목록에 없는 action은 절대 만들지 않는다.
                2. 사용자의 요청이 명확하지 않거나 실행 가능한 action이 없으면 type을 "message"로 응답한다.
                3. action을 실행하려면 requiredParams를 모두 채워야 한다.
                4. requiredParams에 필요한 값을 사용자 요청에서 찾을 수 없으면 type을 "message"로 응답한다.
                5. allowedValues가 있는 파라미터는 반드시 허용된 값 중 하나만 사용한다.

                [navigate path 선택 규칙]
                6. navigate action을 선택한 경우 allowedValues.path 목록을 확인한다.
                7. allowedValues.path 안의 각 항목은 하나의 선택지다.
                8. 각 항목의 label과 examples는 해당 path를 선택하기 위한 이름, 별칭, 검색 키워드다.
                9. 사용자 요청이 label 또는 examples와 일치하거나 의미상 유사하면 해당 항목의 path를 선택한다.
                10. params.path에는 선택한 항목의 path 값을 글자 하나 바꾸지 말고 그대로 복사한다.
                11. params.path에 allowedValues.path 목록에 없는 값을 쓰면 안 된다.
                12. 사용자 요청의 단어를 조합해서 새로운 path를 만들지 않는다.
                13. label, examples, queryParams를 조합해서 새로운 path를 만들지 않는다.
                14. 사용자 요청에 "서버", "랭킹", "분석", "조회", "전장", "검색", "비교" 같은 단어가 있어도 path 뒤에 새 구간을 추가하지 않는다.
                15. label이 "홈"이어도 path는 반드시 제공된 실제 path 값을 사용한다. "/"가 제공되면 "/"를 사용한다.
                16. path는 언어 prefix 없이 제공된 논리 경로만 사용한다. 예: "/notice". "/ko/notice" 또는 "/en/notice"처럼 만들지 않는다.

                [queryParams 처리 규칙]
                17. 선택한 path 항목에 queryParams가 있으면 queryParams 전체를 확인한다.
                18. 사용자 요청 안에 queryParams의 이름, description, type과 의미상 일치하는 값이 있으면 반드시 params.query에 넣는다.
                19. required:false는 값이 없어도 실행 가능하다는 뜻이다.
                20. required:false여도 사용자 요청에 값이 있으면 반드시 params.query에 넣는다.
                21. queryParams에 해당하는 값은 params 바로 아래에 넣지 말고 반드시 params.query 안에 넣는다.
                22. queryParams에 정의되지 않은 query key는 만들지 않는다.
                23. queryParams의 type이 "number"이면 JSON 숫자 타입으로 넣는다.
                24. queryParams의 type이 "string"이면 JSON 문자열 타입으로 넣는다.
                25. queryParams의 type이 "boolean"이면 JSON boolean 타입으로 넣는다.
                26. queryParams.required가 true인 값을 사용자 요청에서 찾을 수 없으면 type을 "message"로 응답한다.

                [응답 규칙]
                27. reply는 반드시 사용자 언어(%s)로 짧게 작성한다.
                28. 설명 문장, 마크다운, 코드블록 없이 JSON 객체 하나만 응답한다.
                29. 응답은 반드시 { 로 시작하고 } 로 끝난다.

                응답 형식:
                {
                  "type": "action" 또는 "message",
                  "action": "action 이름 또는 null",
                  "params": {
                    "path": "allowedValues.path 항목 중 하나의 path 값",
                    "query": {}
                  },
                  "reply": "사용자에게 보여줄 짧은 응답"
                }

                query가 없는 경우에는 "query"를 생략할 수 있다.

                올바른 처리 예시:
                allowedValues.path에 다음 항목이 있다고 가정한다.

                - path: /information/ssc
                  label: 봉인석
                  examples: 봉인석, SSC, Seal Stone Chaos, 봉인된 성소, 봉인석 랭킹
                  queryParams:
                    server:
                      type: number
                      required: false
                      description: 분석할 서버 번호

                사용자 요청:
                3223 봉인석 전장 랭킹

                올바른 응답:
                {
                  "type": "action",
                  "action": "navigate",
                  "params": {
                    "path": "/information/ssc",
                    "query": {
                      "server": 3223
                    }
                  },
                  "reply": "3223 서버 봉인석 페이지로 이동합니다."
                }

                제공된 action 목록:
                %s
                """.formatted(lang, lang, actionListText);
    }

    private String buildUserPrompt(String message) {
        return """
                사용자 요청:
                %s
                """.formatted(message);
    }

    private String buildActionListText(List<ActionSpec> actions) {
        StringBuilder sb = new StringBuilder();

        for (ActionSpec action : actions) {
            if (action == null || !StringUtils.hasText(action.name())) {
                continue;
            }

            sb.append("- action: ").append(action.name()).append("\n");

            if (StringUtils.hasText(action.description())) {
                sb.append("  description: ").append(action.description()).append("\n");
            }

            if (action.requiredParams() == null || action.requiredParams().isEmpty()) {
                sb.append("  requiredParams: 없음\n");
            } else {
                sb.append("  requiredParams: ")
                        .append(String.join(", ", action.requiredParams()))
                        .append("\n");
            }

            if (action.allowedValues() != null && !action.allowedValues().isEmpty()) {
                sb.append("  allowedValues:\n");

                action.allowedValues().forEach((paramName, values) -> {
                    sb.append("    ").append(paramName).append(":\n");
                    sb.append(formatAllowedValues(values, 6));
                });
            }

            if (action.examples() != null && !action.examples().isEmpty()) {
                sb.append("  examples:\n");

                for (String example : action.examples()) {
                    sb.append("    - ").append(example).append("\n");
                }
            }

            sb.append("\n");
        }

        return sb.toString();
    }

    private String formatAllowedValues(Object values, int indentSize) {
        String indent = " ".repeat(indentSize);
        StringBuilder sb = new StringBuilder();

        if (values instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Object value = firstNonNull(
                            map.get("value"),
                            map.get("path"),
                            map.get("name")
                    );

                    Object label = firstNonNull(
                            map.get("label"),
                            map.get("title")
                    );

                    Object examples = firstNonNull(
                            map.get("examples"),
                            map.get("keywords"),
                            map.get("aliases")
                    );

                    Object queryParams = map.get("queryParams");

                    sb.append(indent).append("- path: ").append(value).append("\n");

                    if (label != null) {
                        sb.append(indent).append("  label: ").append(label).append("\n");
                    }

                    if (examples instanceof List<?> exampleList && !exampleList.isEmpty()) {
                        sb.append(indent).append("  examples: ");

                        String exampleText = exampleList.stream()
                                .map(String::valueOf)
                                .reduce((a, b) -> a + ", " + b)
                                .orElse("");

                        sb.append(exampleText).append("\n");
                    }

                    if (queryParams instanceof Map<?, ?> queryParamMap && !queryParamMap.isEmpty()) {
                        sb.append(indent).append("  queryParams:\n");

                        for (Map.Entry<?, ?> entry : queryParamMap.entrySet()) {
                            String paramName = String.valueOf(entry.getKey());
                            Object spec = entry.getValue();

                            sb.append(indent)
                                    .append("    ")
                                    .append(paramName)
                                    .append(":\n");

                            if (spec instanceof Map<?, ?> specMap) {
                                Object type = specMap.get("type");
                                Object required = specMap.get("required");
                                Object description = specMap.get("description");
                                Object aliases = specMap.get("aliases");
                                Object pattern = specMap.get("pattern");

                                if (type != null) {
                                    sb.append(indent)
                                            .append("      type: ")
                                            .append(type)
                                            .append("\n");
                                }

                                if (required != null) {
                                    sb.append(indent)
                                            .append("      required: ")
                                            .append(required)
                                            .append("\n");
                                }

                                if (description != null) {
                                    sb.append(indent)
                                            .append("      description: ")
                                            .append(description)
                                            .append("\n");
                                }

                                if (aliases instanceof List<?> aliasList && !aliasList.isEmpty()) {
                                    String aliasText = aliasList.stream()
                                            .map(String::valueOf)
                                            .reduce((a, b) -> a + ", " + b)
                                            .orElse("");

                                    sb.append(indent)
                                            .append("      aliases: ")
                                            .append(aliasText)
                                            .append("\n");
                                }

                                if (pattern != null) {
                                    sb.append(indent)
                                            .append("      pattern: ")
                                            .append(pattern)
                                            .append("\n");
                                }
                            } else {
                                sb.append(indent)
                                        .append("      spec: ")
                                        .append(spec)
                                        .append("\n");
                            }
                        }
                    }
                } else {
                    sb.append(indent).append("- ").append(item).append("\n");
                }
            }
        } else {
            sb.append(indent).append("- ").append(values).append("\n");
        }

        return sb.toString();
    }

    private ChatbotResponse parseAiResponse(String aiText) throws Exception {
        String json = extractJson(aiText);
        return objectMapper.readValue(json, ChatbotResponse.class);
    }

    private String extractJson(String text) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("empty ai response");
        }

        String trimmed = text.trim();

        if (trimmed.startsWith("```")) {
            trimmed = trimmed
                    .replaceFirst("^```json", "")
                    .replaceFirst("^```", "")
                    .replaceFirst("```$", "")
                    .trim();
        }

        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');

        if (start < 0 || end < 0 || end <= start) {
            throw new IllegalArgumentException("json object not found");
        }

        return trimmed.substring(start, end + 1);
    }

    private ChatbotResponse validateResponse(
            ChatbotResponse response,
            List<ActionSpec> actions,
            String lang
    ) {
        if (response == null) {
            return messageResponseByLang(lang, "처리할 수 없는 응답입니다.");
        }

        String type = response.type();

        if (!"action".equals(type)) {
            return ChatbotResponse.builder()
                    .type("message")
                    .action(null)
                    .params(Map.of())
                    .reply(defaultReply(response.reply(), lang, "요청을 처리할 수 없습니다."))
                    .build();
        }

        String actionName = response.action();

        ActionSpec actionSpec = findActionSpec(actionName, actions);

        if (actionSpec == null) {
            return messageResponseByLang(lang, "지원하지 않는 기능입니다.");
        }

        Map<String, Object> params = response.params() == null
                ? Map.of()
                : response.params();

        params = normalizeParams(actionSpec, params);

        System.out.println("normalized params = " + params);

        if (!hasRequiredParams(actionSpec, params)) {
            return messageResponseByLang(lang, "필요한 값이 부족합니다.");
        }

        if (!hasOnlyAllowedValues(actionSpec, params)) {
            return messageResponseByLang(lang, "허용되지 않은 값이 포함되어 있습니다.");
        }

        return ChatbotResponse.builder()
                .type("action")
                .action(actionName)
                .params(params)
                .reply(defaultReply(response.reply(), lang, "처리합니다."))
                .build();
    }

    private Map<String, Object> normalizeParams(
            ActionSpec actionSpec,
            Map<String, Object> params
    ) {
        if (actionSpec == null || params == null) {
            return params;
        }

        if (!"navigate".equals(actionSpec.name())) {
            return params;
        }

        return normalizeNavigateParams(actionSpec, params);
    }

    private Map<String, Object> normalizeNavigateParams(
            ActionSpec actionSpec,
            Map<String, Object> params
    ) {
        Object pathObj = params.get("path");

        if (pathObj == null) {
            return params;
        }

        String path = String.valueOf(pathObj);
        Map<String, Object> normalized = new LinkedHashMap<>();

        normalized.put("path", path);

        Map<?, ?> matchedPathSpec = findMatchedPathSpec(actionSpec, path);

        if (matchedPathSpec == null) {
            return normalized;
        }

        Object queryParamsObj = matchedPathSpec.get("queryParams");

        Map<?, ?> queryParamsMap = queryParamsObj instanceof Map<?, ?> map
                ? map
                : Map.of();

        Map<String, Object> query = new LinkedHashMap<>();

        Object queryObj = params.get("query");

        if (queryObj instanceof Map<?, ?> queryMap) {
            for (Map.Entry<?, ?> entry : queryMap.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object value = entry.getValue();

                if (isEmptyQueryValue(value)) {
                    continue;
                }

                query.put(key, value);
            }
        }

        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();

            if ("path".equals(key) || "query".equals(key)) {
                continue;
            }

            if (queryParamsMap.containsKey(key) && !isEmptyQueryValue(entry.getValue())) {
                query.put(key, entry.getValue());
            }
        }

        if (!query.isEmpty()) {
            normalized.put("query", query);
        }

        return normalized;
    }
    
    private boolean isEmptyQueryValue(Object value) {
        if (value == null) {
            return true;
        }

        if (value instanceof String str) {
            return !StringUtils.hasText(str) || "null".equalsIgnoreCase(str);
        }

        return false;
    }

    private boolean hasRequiredParams(ActionSpec actionSpec, Map<String, Object> params) {
        List<String> requiredParams = actionSpec.requiredParams();

        if (requiredParams == null || requiredParams.isEmpty()) {
            return true;
        }

        for (String key : requiredParams) {
            Object value = params.get(key);

            if (value == null) {
                return false;
            }

            if (value instanceof String str && !StringUtils.hasText(str)) {
                return false;
            }
        }

        return true;
    }

    private boolean hasOnlyAllowedValues(ActionSpec actionSpec, Map<String, Object> params) {
        if (actionSpec == null) {
            return false;
        }

        if (params == null) {
            return false;
        }

        Map<String, Object> allowedValues = actionSpec.allowedValues();

        if (allowedValues == null || allowedValues.isEmpty()) {
            return true;
        }

        if ("navigate".equals(actionSpec.name())) {
            return validateNavigateParams(actionSpec, params);
        }

        for (Map.Entry<String, Object> entry : allowedValues.entrySet()) {
            String paramName = entry.getKey();
            Object allowed = entry.getValue();

            if (!params.containsKey(paramName)) {
                continue;
            }

            Object actualValue = params.get(paramName);

            if (!isAllowedValue(actualValue, allowed)) {
                return false;
            }
        }

        return true;
    }

    private boolean validateNavigateParams(ActionSpec actionSpec, Map<String, Object> params) {
        Object pathObj = params.get("path");

        if (pathObj == null) {
            return false;
        }

        String path = String.valueOf(pathObj);

        Map<?, ?> matchedPathSpec = findMatchedPathSpec(actionSpec, path);

        if (matchedPathSpec == null) {
            return false;
        }

        for (String key : params.keySet()) {
            if (!"path".equals(key) && !"query".equals(key)) {
                return false;
            }
        }

        Object queryObj = params.get("query");

        if (queryObj == null) {
            return validateRequiredQueryParams(matchedPathSpec, Map.of());
        }

        if (!(queryObj instanceof Map<?, ?> queryMap)) {
            return false;
        }

        Object queryParamsObj = matchedPathSpec.get("queryParams");

        if (!(queryParamsObj instanceof Map<?, ?> queryParamsMap)) {
            return queryMap.isEmpty();
        }

        for (Map.Entry<?, ?> entry : queryMap.entrySet()) {
            String queryKey = String.valueOf(entry.getKey());
            Object queryValue = entry.getValue();

            Object querySpecObj = queryParamsMap.get(queryKey);

            if (querySpecObj == null) {
                return false;
            }

            if (!validateQueryValue(queryValue, querySpecObj)) {
                return false;
            }
        }

        return validateRequiredQueryParams(matchedPathSpec, queryMap);
    }

    private boolean validateRequiredQueryParams(
            Map<?, ?> matchedPathSpec,
            Map<?, ?> queryMap
    ) {
        Object queryParamsObj = matchedPathSpec.get("queryParams");

        if (!(queryParamsObj instanceof Map<?, ?> queryParamsMap)) {
            return true;
        }

        for (Map.Entry<?, ?> entry : queryParamsMap.entrySet()) {
            String queryKey = String.valueOf(entry.getKey());
            Object querySpecObj = entry.getValue();

            if (!(querySpecObj instanceof Map<?, ?> querySpecMap)) {
                continue;
            }

            Object requiredObj = querySpecMap.get("required");

            boolean required = Boolean.TRUE.equals(requiredObj)
                    || "true".equalsIgnoreCase(String.valueOf(requiredObj));

            if (!required) {
                continue;
            }

            Object value = queryMap.get(queryKey);

            if (value == null) {
                return false;
            }

            if (value instanceof String str && !StringUtils.hasText(str)) {
                return false;
            }
        }

        return true;
    }

    private boolean validateQueryValue(Object value, Object querySpecObj) {
        if (value == null) {
            return false;
        }

        if (!(querySpecObj instanceof Map<?, ?> querySpecMap)) {
            return true;
        }

        Object typeObj = querySpecMap.get("type");

        if (typeObj == null) {
            return true;
        }

        String type = String.valueOf(typeObj);

        return switch (type) {
            case "number" -> isNumberValue(value);
            case "string" -> value instanceof String;
            case "boolean" -> value instanceof Boolean;
            default -> true;
        };
    }

    private boolean isNumberValue(Object value) {
        if (value instanceof Number) {
            return true;
        }

        if (value instanceof String str) {
            return str.matches("-?\\d+(\\.\\d+)?");
        }

        return false;
    }

    private Map<?, ?> findMatchedPathSpec(ActionSpec actionSpec, String path) {
        if (actionSpec == null || actionSpec.allowedValues() == null) {
            return null;
        }

        Object allowedPathObj = actionSpec.allowedValues().get("path");

        if (!(allowedPathObj instanceof List<?> allowedPathList)) {
            return null;
        }

        for (Object item : allowedPathList) {
            if (item instanceof Map<?, ?> map) {
                Object allowedPath = firstNonNull(
                        map.get("path"),
                        map.get("value"),
                        map.get("name")
                );

                if (allowedPath == null) {
                    continue;
                }

                if (path.equals(String.valueOf(allowedPath))) {
                    return map;
                }
            } else {
                if (path.equals(String.valueOf(item))) {
                    return Map.of("path", item);
                }
            }
        }

        return null;
    }

    private ActionSpec findActionSpec(String actionName, List<ActionSpec> actions) {
        if (!StringUtils.hasText(actionName) || actions == null) {
            return null;
        }

        return actions.stream()
                .filter(action -> action != null && actionName.equals(action.name()))
                .findFirst()
                .orElse(null);
    }

    private boolean isAllowedValue(Object actualValue, Object allowed) {
        if (actualValue == null) {
            return false;
        }

        if (allowed instanceof List<?> list) {
            return list.stream().anyMatch(item -> matchesAllowedItem(actualValue, item));
        }

        return Objects.equals(
                String.valueOf(actualValue),
                String.valueOf(allowed)
        );
    }

    private boolean matchesAllowedItem(Object actualValue, Object allowedItem) {
        if (allowedItem instanceof Map<?, ?> map) {
            Object value = firstNonNull(
                    map.get("value"),
                    map.get("path"),
                    map.get("name")
            );

            return Objects.equals(
                    String.valueOf(actualValue),
                    String.valueOf(value)
            );
        }

        return Objects.equals(
                String.valueOf(actualValue),
                String.valueOf(allowedItem)
        );
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }

        return null;
    }

    private String normalizeLang(String lang) {
        if (!StringUtils.hasText(lang)) {
            return "ko";
        }

        String lower = lang.toLowerCase();

        if (lower.startsWith("ko")) {
            return "ko";
        }

        if (lower.startsWith("en")) {
            return "en";
        }

        if (lower.startsWith("ja")) {
            return "ja";
        }

        return "ko";
    }

    private ChatbotResponse messageResponse(String reply) {
        return ChatbotResponse.builder()
                .type("message")
                .action(null)
                .params(Map.of())
                .reply(reply)
                .build();
    }

    private ChatbotResponse messageResponseByLang(String lang, String koMessage) {
        String normalized = normalizeLang(lang);

        String reply = switch (normalized) {
            case "en" -> toEnglishFallback(koMessage);
            case "ja" -> toJapaneseFallback(koMessage);
            default -> koMessage;
        };

        return ChatbotResponse.builder()
                .type("message")
                .action(null)
                .params(Map.of())
                .reply(reply)
                .build();
    }

    private String defaultReply(String reply, String lang, String defaultKoReply) {
        if (StringUtils.hasText(reply)) {
            return reply;
        }

        return switch (normalizeLang(lang)) {
            case "en" -> "Processing your request.";
            case "ja" -> "リクエストを処理します。";
            default -> defaultKoReply;
        };
    }

    private String toEnglishFallback(String koMessage) {
        return switch (koMessage) {
            case "지원 가능한 기능이 없습니다." -> "There are no available actions.";
            case "AI 요청 처리 중 오류가 발생했습니다." -> "An error occurred while processing the AI request.";
            case "AI 응답을 해석하지 못했습니다." -> "Could not parse the AI response.";
            case "처리할 수 없는 응답입니다." -> "The response cannot be processed.";
            case "지원하지 않는 기능입니다." -> "This feature is not supported.";
            case "필요한 값이 부족합니다." -> "Required values are missing.";
            case "허용되지 않은 값이 포함되어 있습니다." -> "The request contains a value that is not allowed.";
            default -> "The request could not be processed.";
        };
    }

    private String toJapaneseFallback(String koMessage) {
        return switch (koMessage) {
            case "지원 가능한 기능이 없습니다." -> "利用可能な機能がありません。";
            case "AI 요청 처리 중 오류가 발생했습니다." -> "AIリクエストの処理中にエラーが発生しました。";
            case "AI 응답을 해석하지 못했습니다." -> "AIの応答を解析できませんでした。";
            case "처리할 수 없는 응답입니다." -> "応答を処理できません。";
            case "지원하지 않는 기능입니다." -> "この機能はサポートされていません。";
            case "필요한 값이 부족합니다." -> "必要な値が不足しています。";
            case "허용되지 않은 값이 포함되어 있습니다." -> "許可されていない値が含まれています。";
            default -> "リクエストを処理できませんでした。";
        };
    }
}