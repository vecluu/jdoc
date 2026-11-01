package io.github.vecluu.jdoc.index;

import io.github.vecluu.jdoc.error.IndexException;
import io.github.vecluu.jdoc.model.ParameterInfo;
import java.util.ArrayList;
import java.util.List;

/**
 * Compact text encoding of a parameter list for the {@code members.parameters} column:
 * {@code name US type US erased} per parameter, parameters separated by RS (ASCII unit/record separators,
 * which cannot occur in Java names or rendered types). The empty list is the empty string.
 */
final class ParameterCodec {

    private static final char FIELD = '\u001F';
    private static final char RECORD = '\u001E';

    private ParameterCodec() {
    }

    static String encode(List<ParameterInfo> parameters) {
        StringBuilder text = new StringBuilder();
        for (ParameterInfo parameter : parameters) {
            if (!text.isEmpty()) {
                text.append(RECORD);
            }
            text.append(checked(parameter.name())).append(FIELD)
                    .append(checked(parameter.type())).append(FIELD)
                    .append(checked(parameter.erasedType()));
        }
        return text.toString();
    }

    static List<ParameterInfo> decode(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        List<ParameterInfo> parameters = new ArrayList<>();
        for (String record : text.split(String.valueOf(RECORD), -1)) {
            String[] fields = record.split(String.valueOf(FIELD), -1);
            if (fields.length != 3) {
                throw new IndexException("corrupt parameter list in index: " + text.replace(RECORD, '|'));
            }
            parameters.add(new ParameterInfo(fields[0], fields[1], fields[2]));
        }
        return List.copyOf(parameters);
    }

    private static String checked(String value) {
        if (value.indexOf(FIELD) >= 0 || value.indexOf(RECORD) >= 0) {
            throw new IllegalArgumentException("parameter text contains a control separator: " + value);
        }
        return value;
    }
}
