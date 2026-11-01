package io.github.vecluu.jdoc.text;

import java.util.regex.Pattern;

/**
 * Footer lines of paginated results and handling of the {@code --page} option in the echoed command line.
 *
 * <p>A page that has a successor ends with {@code page 1/3 · next: <command line> --page 2}; the last page of a
 * multi-page result ends with {@code page 3/3}.
 */
public final class PageFooter {

    private static final Pattern PAGE_OPTION =
            Pattern.compile("(?:^|\\s+)--page(?:=\\S*|\\s+-?\\d+)?(?=\\s|$)");

    private final String commandLine;

    /**
     * @param commandLine the command line as the user typed it; any {@code --page} option is removed
     */
    public PageFooter(String commandLine) {
        this.commandLine = withoutPageOption(commandLine);
    }

    /** Removes {@code --page N}, {@code --page=N} and a dangling {@code --page} from a command line. */
    public static String withoutPageOption(String commandLine) {
        if (commandLine == null) {
            return "";
        }
        return PAGE_OPTION.matcher(commandLine).replaceAll("").strip();
    }

    /** The command line without its {@code --page} option. */
    public String commandLine() {
        return commandLine;
    }

    /** The footer for page {@code number} of {@code total}. */
    public String footer(int number, int total) {
        if (number < total) {
            return "page " + number + "/" + total + " · next: " + nextCommand(number + 1);
        }
        return "page " + number + "/" + total;
    }

    /** The command that shows the given page. */
    public String nextCommand(int page) {
        String option = "--page " + page;
        return commandLine.isEmpty() ? option : commandLine + " " + option;
    }

    /** The longest footer any page of a result with {@code total} pages can have, in printed characters. */
    int maxFooterCharacters(int total) {
        int widest = (int) Math.pow(10, String.valueOf(total).length()) - 1;
        return Lines.characterCount(footer(widest - 1, widest));
    }
}
