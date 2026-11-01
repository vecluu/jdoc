package io.github.vecluu.jdoc.lookup;

import io.github.vecluu.jdoc.model.RenderedDoc;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Merges an overriding member's doc with the doc of the member it overrides, approximating javadoc's
 * {@code {@inheritDoc}} rules from already rendered text.
 *
 * <ul>
 *   <li>A missing doc takes the inherited doc as a whole.</li>
 *   <li>An empty main description takes the inherited one. A description with {@code {@inheritDoc}} gets the
 *       inherited description in front of its own text (the rendered text no longer shows where the tag was; it
 *       almost always comes first), and the inherited first sentence.</li>
 *   <li>Missing {@code @param}, {@code @return} and {@code @throws} tags are taken from the inherited doc.</li>
 * </ul>
 * Stateless.
 */
final class DocInheritance {

    private static final Set<String> INHERITED_TAGS = Set.of("@param", "@return", "@throws");
    private static final List<String> TAG_ORDER =
            List.of("@apiNote", "@implSpec", "@implNote", "@param", "@return", "@throws", "@see", "@since",
                    "@deprecated");

    /** True if the doc is missing, has an unresolved {@code {@inheritDoc}} or has no main description. */
    static boolean needsInheritance(RenderedDoc doc) {
        return doc.inheritsDoc() || doc.description().isBlank();
    }

    /** The own doc completed from the inherited one. */
    static RenderedDoc merge(RenderedDoc own, RenderedDoc inherited) {
        if (own.isEmpty()) {
            return inherited;
        }
        String description;
        String firstSentence;
        if (own.description().isBlank()) {
            description = inherited.description();
            firstSentence = inherited.firstSentence();
        } else if (own.inheritsDoc() && !inherited.description().isBlank()) {
            description = inherited.description() + "\n\n" + own.description();
            firstSentence = inherited.firstSentence();
        } else {
            description = own.description();
            firstSentence = own.firstSentence();
        }
        List<String> tags = new ArrayList<>(own.blockTags());
        Set<String> present = own.blockTags().stream().map(DocInheritance::tagKey).collect(Collectors.toSet());
        boolean added = false;
        for (String tag : inherited.blockTags()) {
            if (INHERITED_TAGS.contains(tagName(tag)) && present.add(tagKey(tag))) {
                tags.add(tag);
                added = true;
            }
        }
        if (added) {
            tags.sort(Comparator.comparingInt(DocInheritance::rank));
        }
        return new RenderedDoc(description, firstSentence, tags, inherited.inheritsDoc());
    }

    private static int rank(String tag) {
        int index = TAG_ORDER.indexOf(tagName(tag));
        return index < 0 ? TAG_ORDER.size() : index;
    }

    private static String tagName(String tag) {
        int space = tag.indexOf(' ');
        return space < 0 ? tag : tag.substring(0, space);
    }

    /** {@code @param x}, {@code @throws X}, or the tag name for others. */
    private static String tagKey(String tag) {
        String name = tagName(tag);
        if (!name.equals("@param") && !name.equals("@throws")) {
            return name;
        }
        String[] parts = tag.split("\\s+", 3);
        return parts.length > 1 ? name + " " + parts[1] : name;
    }

    private DocInheritance() {
    }
}
