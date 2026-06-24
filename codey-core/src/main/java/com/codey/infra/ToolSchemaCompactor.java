package com.codey.infra;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 压缩 function tool 的 JSON Schema，去掉对模型收益很低但非常占 token 的描述性字段。
 */
public class ToolSchemaCompactor {
    private ToolSchemaCompactor() {
    }

    public static Map<String, Object> compactParameters(Map<String, Object> parameters) {
        Object compacted = compactValue(parameters);
        if (compacted instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) compacted;
            return map;
        }
        return parameters;
    }

    private static Object compactValue(Object value) {
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> source = (Map<String, Object>) value;
            Map<String, Object> target = new LinkedHashMap<String, Object>();
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                String key = entry.getKey();
                if (shouldDropKey(key)) {
                    continue;
                }
                target.put(key, compactValue(entry.getValue()));
            }
            return target;
        }
        if (value instanceof List) {
            List<?> source = (List<?>) value;
            List<Object> target = new ArrayList<Object>(source.size());
            for (Object item : source) {
                target.add(compactValue(item));
            }
            return target;
        }
        return value;
    }

    private static boolean shouldDropKey(String key) {
        if (key == null) {
            return false;
        }
        // 这些字段主要服务人类/文档，对模型构造参数帮助很小，但会显著增加上下文体积。
        return "description".equals(key)
                || "title".equals(key)
                || "examples".equals(key)
                || "$comment".equals(key);
    }
}

