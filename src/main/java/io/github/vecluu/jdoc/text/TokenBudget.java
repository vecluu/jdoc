package io.github.vecluu.jdoc.text;

/**
 * The output cap: tokens are estimated as characters divided by four.
 *
 * @param maxTokens tokens allowed per page
 */
public record TokenBudget(int maxTokens) {

    /** Default page budget in tokens. */
    public static final int DEFAULT_MAX_TOKENS = 2_000;

    /** Characters per estimated token. */
    public static final int CHARACTERS_PER_TOKEN = 4;

    public TokenBudget {
        if (maxTokens < 1) {
            throw new IllegalArgumentException("token budget must be positive: " + maxTokens);
        }
    }

    /** The default budget of {@value #DEFAULT_MAX_TOKENS} tokens. */
    public static TokenBudget defaultBudget() {
        return new TokenBudget(DEFAULT_MAX_TOKENS);
    }

    /** Estimated tokens of a text: characters / 4, rounded up. */
    public static int estimateTokens(CharSequence text) {
        return (text.length() + CHARACTERS_PER_TOKEN - 1) / CHARACTERS_PER_TOKEN;
    }

    /** Characters allowed per page (line breaks count as characters). */
    public int maxCharacters() {
        return maxTokens * CHARACTERS_PER_TOKEN;
    }

    /** True if the text fits into the budget. */
    public boolean fits(CharSequence text) {
        return text.length() <= maxCharacters();
    }
}
