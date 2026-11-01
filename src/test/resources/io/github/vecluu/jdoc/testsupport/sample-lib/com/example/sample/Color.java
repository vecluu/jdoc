package com.example.sample;

/**
 * Fill colors.
 *
 * @since 1.0
 */
public enum Color {
    /** Pure red. */
    RED("#ff0000"),
    /** Pure green. */
    GREEN("#00ff00"),
    /** Pure blue, a cool color. */
    BLUE("#0000ff") {
        @Override
        public boolean isCool() {
            return true;
        }
    };

    private final String hex;

    Color(String hex) {
        this.hex = hex;
    }

    /**
     * Returns the hex code, e.g. {@code #ff0000}.
     *
     * @return the hex code
     */
    public String hex() {
        return hex;
    }

    /**
     * Whether this is a cool color.
     *
     * @return {@code true} for cool colors
     */
    public boolean isCool() {
        return false;
    }
}
