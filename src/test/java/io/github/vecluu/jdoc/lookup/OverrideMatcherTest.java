package io.github.vecluu.jdoc.lookup;

import static io.github.vecluu.jdoc.lookup.LookupIndex.constructor;
import static io.github.vecluu.jdoc.lookup.LookupIndex.field;
import static io.github.vecluu.jdoc.lookup.LookupIndex.method;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OverrideMatcherTest {

    private final OverrideMatcher matcher = new OverrideMatcher();

    @Test
    void methodsOverrideBySameNameAndErasedParameters() {
        assertThat(matcher.overrides(method("a.Sub", "run", "int x"), method("a.Sup", "run", "int y"))).isTrue();
        assertThat(matcher.overrides(method("a.Sub", "run"), method("a.Sup", "run"))).isTrue();
        assertThat(matcher.overrides(method("a.Sub", "run", "long x"), method("a.Sup", "run", "int y"))).isFalse();
        assertThat(matcher.overrides(method("a.Sub", "run", "int x"), method("a.Sup", "run"))).isFalse();
        assertThat(matcher.overrides(method("a.Sub", "walk"), method("a.Sup", "run"))).isFalse();
        assertThat(matcher.overrides(method("a.Sub", "put", "List<String> l"), method("a.Sup", "put", "List<T> l")))
                .isTrue();
        assertThat(matcher.overrides(method("a.Sub", "get", "Map.Entry<K,V> e:Map.Entry"),
                method("a.Sup", "get", "Entry e:Map.Entry"))).isTrue();
    }

    @Test
    void typeVariableParametersMatchAnyTypeInThatPosition() {
        assertThat(matcher.overrides(method("a.Shape", "compareTo", "Shape other"),
                method("java.lang.Comparable", "compareTo", "T o:Object"))).isTrue();
        assertThat(matcher.overrides(method("a.Box", "put", "Circle c"),
                method("a.Base", "put", "T value:Shape"))).isTrue();
        assertThat(matcher.overrides(method("a.Box", "all", "String[] values"),
                method("a.Base", "all", "T... values:Object[]"))).isTrue();
        assertThat(matcher.overrides(method("a.Box", "all", "String value"),
                method("a.Base", "all", "T[] values:Object[]"))).isFalse();
        assertThat(matcher.overrides(method("a.Shape", "compareTo", "Shape other"),
                method("a.Other", "compareTo", "Object o"))).isFalse();
    }

    @Test
    void fieldsHideByNameAndConstructorsNeverOverride() {
        assertThat(matcher.overrides(field("a.Sub", "SIZE"), field("a.Sup", "SIZE"))).isTrue();
        assertThat(matcher.overrides(field("a.Sub", "size"), method("a.Sup", "size"))).isFalse();
        assertThat(matcher.overrides(method("a.Sub", "size"), field("a.Sup", "size"))).isFalse();
        assertThat(matcher.overrides(constructor("a.Same"), constructor("a.Same"))).isFalse();
    }
}
