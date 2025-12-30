package io.github.http.log.snap.server.spring.rule;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JSON 表达式求值器
 * <p>
 * 支持简单的比较表达式，例如：
 * <ul>
 *   <li>{@code stream == true}</li>
 *   <li>{@code status.code == 200}</li>
 *   <li>{@code name != "test"}</li>
 *   <li>{@code count > 10}</li>
 *   <li>{@code age >= 18}</li>
 * </ul>
 *
 * @author http-logging
 */
@Slf4j
public class JsonExpressionEvaluator {

    // 匹配表达式：字段名 操作符 值
    // 例如：stream == true, status.code == 200, name != "test"
    private static final Pattern EXPRESSION_PATTERN = Pattern.compile(
        "^\\s*([\\w.]+)\\s*(==|!=|>|<|>=|<=)\\s*(.+?)\\s*$"
    );

    /**
     * 评估 JSON 表达式
     *
     * @param jsonBody JSON 字符串
     * @param expression 表达式，例如：stream == true
     * @return 如果表达式为真返回 true，否则返回 false
     */
    public static boolean evaluate(@Nullable String jsonBody, String expression) {
        if (jsonBody == null || jsonBody.trim().isEmpty()) {
            return false;
        }

        if (expression == null || expression.trim().isEmpty()) {
            return false;
        }

        try {
            // 解析 JSON
            Object jsonObj = JSON.parse(jsonBody);
            if (!(jsonObj instanceof JSONObject)) {
                // 如果不是对象，尝试解析为数组或其他类型
                return false;
            }

            JSONObject json = (JSONObject) jsonObj;

            // 解析表达式
            Matcher matcher = EXPRESSION_PATTERN.matcher(expression.trim());
            if (!matcher.matches()) {
                log.warn("Invalid JSON expression format: {}", expression);
                return false;
            }

            String fieldPath = matcher.group(1); // 字段路径，如 "stream" 或 "status.code"
            String operator = matcher.group(2); // 操作符
            String valueStr = matcher.group(3).trim(); // 值字符串

            // 获取字段值
            Object fieldValue = getFieldValue(json, fieldPath);
            if (fieldValue == null) {
                return false;
            }

            // 解析比较值
            Object compareValue = parseValue(valueStr);

            // 执行比较
            return compare(fieldValue, operator, compareValue);

        } catch (Exception e) {
            log.debug("Failed to evaluate JSON expression: {} on body: {}", expression, jsonBody, e);
            return false;
        }
    }

    /**
     * 从 JSON 对象中获取字段值（支持点号分隔的路径）
     */
    private static Object getFieldValue(JSONObject json, String fieldPath) {
        String[] parts = fieldPath.split("\\.");
        Object current = json;

        for (String part : parts) {
            if (current instanceof JSONObject) {
                current = ((JSONObject) current).get(part);
                if (current == null) {
                    return null;
                }
            } else {
                return null;
            }
        }

        return current;
    }

    /**
     * 解析值字符串为对应的类型
     */
    private static Object parseValue(String valueStr) {
        // 移除可能的引号
        valueStr = valueStr.trim();
        if ((valueStr.startsWith("\"") && valueStr.endsWith("\"")) ||
            (valueStr.startsWith("'") && valueStr.endsWith("'"))) {
            return valueStr.substring(1, valueStr.length() - 1);
        }

        // 布尔值
        if ("true".equalsIgnoreCase(valueStr)) {
            return true;
        }
        if ("false".equalsIgnoreCase(valueStr)) {
            return false;
        }

        // null
        if ("null".equalsIgnoreCase(valueStr)) {
            return null;
        }

        // 数字
        try {
            if (valueStr.contains(".")) {
                return Double.parseDouble(valueStr);
            } else {
                return Long.parseLong(valueStr);
            }
        } catch (NumberFormatException e) {
            // 不是数字，返回字符串
            return valueStr;
        }
    }

    /**
     * 执行比较操作
     */
    private static boolean compare(Object fieldValue, String operator, Object compareValue) {
        // null 值比较
        if (fieldValue == null || compareValue == null) {
            switch (operator) {
                case "==":
                    return fieldValue == null && compareValue == null;
                case "!=":
                    return fieldValue != null || compareValue != null;
                default:
                    return false;
            }
        }

        // 类型匹配的比较
        if (fieldValue.getClass() == compareValue.getClass()) {
            return compareSameType(fieldValue, operator, compareValue);
        }

        // 数字类型比较（支持不同数字类型之间的比较）
        if (isNumber(fieldValue) && isNumber(compareValue)) {
            double fieldNum = toDouble(fieldValue);
            double compareNum = toDouble(compareValue);
            return compareNumbers(fieldNum, operator, compareNum);
        }

        // 字符串比较
        if (fieldValue instanceof String && compareValue instanceof String) {
            return compareStrings((String) fieldValue, operator, (String) compareValue);
        }

        // 布尔值比较
        if (fieldValue instanceof Boolean && compareValue instanceof Boolean) {
            boolean fieldBool = (Boolean) fieldValue;
            boolean compareBool = (Boolean) compareValue;
            switch (operator) {
                case "==":
                    return fieldBool == compareBool;
                case "!=":
                    return fieldBool != compareBool;
                default:
                    return false;
            }
        }

        // 其他情况：转换为字符串比较
        return compareStrings(String.valueOf(fieldValue), operator, String.valueOf(compareValue));
    }

    private static boolean compareSameType(Object fieldValue, String operator, Object compareValue) {
        if (fieldValue instanceof Number) {
            return compareNumbers(toDouble(fieldValue), operator, toDouble(compareValue));
        }
        if (fieldValue instanceof String) {
            return compareStrings((String) fieldValue, operator, (String) compareValue);
        }
        if (fieldValue instanceof Boolean) {
            boolean fieldBool = (Boolean) fieldValue;
            boolean compareBool = (Boolean) compareValue;
            switch (operator) {
                case "==":
                    return fieldBool == compareBool;
                case "!=":
                    return fieldBool != compareBool;
                default:
                    return false;
            }
        }
        // 默认使用 equals
        switch (operator) {
            case "==":
                return fieldValue.equals(compareValue);
            case "!=":
                return !fieldValue.equals(compareValue);
            default:
                return false;
        }
    }

    private static boolean compareNumbers(double fieldNum, String operator, double compareNum) {
        switch (operator) {
            case "==":
                return Math.abs(fieldNum - compareNum) < 1e-10; // 浮点数比较
            case "!=":
                return Math.abs(fieldNum - compareNum) >= 1e-10;
            case ">":
                return fieldNum > compareNum;
            case "<":
                return fieldNum < compareNum;
            case ">=":
                return fieldNum >= compareNum;
            case "<=":
                return fieldNum <= compareNum;
            default:
                return false;
        }
    }

    private static boolean compareStrings(String fieldStr, String operator, String compareStr) {
        switch (operator) {
            case "==":
                return fieldStr.equals(compareStr);
            case "!=":
                return !fieldStr.equals(compareStr);
            case ">":
                return fieldStr.compareTo(compareStr) > 0;
            case "<":
                return fieldStr.compareTo(compareStr) < 0;
            case ">=":
                return fieldStr.compareTo(compareStr) >= 0;
            case "<=":
                return fieldStr.compareTo(compareStr) <= 0;
            default:
                return false;
        }
    }

    private static boolean isNumber(Object value) {
        return value instanceof Number;
    }

    private static double toDouble(Object value) {
        if (value instanceof Double) {
            return (Double) value;
        } else if (value instanceof Float) {
            return (Float) value;
        } else if (value instanceof Long) {
            return (Long) value;
        } else if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof Short) {
            return (Short) value;
        } else if (value instanceof Byte) {
            return (Byte) value;
        } else {
            return ((Number) value).doubleValue();
        }
    }
}

