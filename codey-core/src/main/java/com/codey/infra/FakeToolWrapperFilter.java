package com.codey.infra;

/**
 * 过滤模型流式文本中伪装成工具包装标签的内容。
 */
public class FakeToolWrapperFilter {
    private static final String[] TOOL_CALL_START_MARKERS =
            new String[]{"[TOOL_CALL]", "<deepseek:tool_call", "<tool_call", "<invoke ", "<function_calls>"};
    private static final String[] TOOL_CALL_END_MARKERS =
            new String[]{"[/TOOL_CALL]", "</deepseek:tool_call>", "</tool_call>", "</invoke>", "</function_calls>"};

    private boolean inWrapper;

    public String filter(String delta) {
        if (delta == null || delta.isEmpty()) {
            return "";
        }
        StringBuilder visible = new StringBuilder();
        String rest = delta;
        while (!rest.isEmpty()) {
            Marker marker = inWrapper
                    ? findFirstMarker(rest, TOOL_CALL_END_MARKERS)
                    : findFirstMarker(rest, TOOL_CALL_START_MARKERS);
            if (marker == null) {
                if (!inWrapper) {
                    visible.append(rest);
                }
                break;
            }
            if (!inWrapper) {
                visible.append(rest.substring(0, marker.index));
            }
            rest = rest.substring(marker.index + marker.length);
            inWrapper = !inWrapper;
        }
        return visible.toString();
    }

    private Marker findFirstMarker(String text, String[] markers) {
        Marker match = null;
        for (String marker : markers) {
            int index = text.indexOf(marker);
            if (index < 0) {
                continue;
            }
            if (match == null || index < match.index) {
                match = new Marker(index, marker.length());
            }
        }
        return match;
    }

    private static final class Marker {
        private final int index;
        private final int length;

        private Marker(int index, int length) {
            this.index = index;
            this.length = length;
        }
    }
}
