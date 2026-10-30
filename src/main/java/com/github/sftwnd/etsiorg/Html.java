package com.github.sftwnd.etsiorg;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helpers of the HTML text
 */
final class Html {

    /**
     * Numeric or predefined named character reference
     */
    private static final Pattern REFERENCE = Pattern.compile("&(?:#(\\d{1,7})|#[xX]([0-9a-fA-F]{1,6})|(amp|lt|gt|quot|apos));");

    private static final Map<String, String> NAMED = Map.of("amp", "&", "lt", "<", "gt", ">", "quot", "\"", "apos", "'");

    private Html() {
    }

    /**
     * Replace the character references with the characters, e.g. in an attribute value
     * @param text HTML text
     * @return text with numeric and predefined named references replaced; invalid references are kept as is
     */
    static @NonNull String unescape(@NonNull String text) {
        if (Objects.requireNonNull(text, "Html::unescape - text is null").indexOf('&') < 0) {
            return text;
        }
        Matcher matcher = REFERENCE.matcher(text);
        StringBuilder result = new StringBuilder(text.length());
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(character(matcher)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Character of the reference
     * @param matcher matcher of the reference
     * @return referenced character or the reference itself if it is not a valid code point
     */
    private static @NonNull String character(@NonNull Matcher matcher) {
        if (matcher.group(3) != null) {
            return NAMED.get(matcher.group(3));
        }
        int codePoint = matcher.group(1) != null ? Integer.parseInt(matcher.group(1)) : Integer.parseInt(matcher.group(2), 16);
        return codePoint > 0 && Character.isValidCodePoint(codePoint) ? new String(Character.toChars(codePoint)) : matcher.group();
    }

}
