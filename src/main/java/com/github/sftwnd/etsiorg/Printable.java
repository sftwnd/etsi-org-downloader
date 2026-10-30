package com.github.sftwnd.etsiorg;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Printable representation of the text that comes from the network.
 * Control, format and line separator characters are escaped, so the text can neither forge log records
 * nor send escape sequences to the terminal
 */
public final class Printable {

    private Printable() {
    }

    /**
     * Printable representation of the value
     * @param value value to represent
     * @return string representation of the value with unsafe characters escaped as {@code \}{@code uXXXX}
     */
    public static @NonNull String of(@Nullable Object value) {
        String text = String.valueOf(value);
        if (text.codePoints().noneMatch(Printable::isUnsafe)) {
            return text;
        }
        StringBuilder result = new StringBuilder(text.length() + 16);
        text.codePoints().forEach(codePoint -> {
            if (isUnsafe(codePoint)) {
                result.append(String.format("\\u%04X", codePoint));
            } else {
                result.appendCodePoint(codePoint);
            }
        });
        return result.toString();
    }

    /**
     * Check that the character has to be escaped
     * @param codePoint character code point
     * @return true for control, format and line or paragraph separator characters
     */
    private static boolean isUnsafe(int codePoint) {
        switch (Character.getType(codePoint)) {
            case Character.CONTROL:
            case Character.FORMAT:
            case Character.LINE_SEPARATOR:
            case Character.PARAGRAPH_SEPARATOR:
                return true;
            default:
                return false;
        }
    }

}
