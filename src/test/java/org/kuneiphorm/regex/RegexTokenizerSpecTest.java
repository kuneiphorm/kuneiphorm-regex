package org.kuneiphorm.regex;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.kuneiphorm.daedalus.automaton.Automaton;
import org.kuneiphorm.daedalus.range.BinarySearchClassifier;
import org.kuneiphorm.daedalus.range.FragmentedAutomaton;
import org.kuneiphorm.daedalus.range.IntRange;
import org.kuneiphorm.runtime.exception.SyntaxException;

class RegexTokenizerSpecTest {

  @Test
  void automaton_returnsFragmentedAutomaton() {
    Automaton<String, Integer> dfa = Automaton.create();
    dfa.newState();
    dfa.setInitialStateId(0);
    BinarySearchClassifier classifier =
        new BinarySearchClassifier(java.util.List.of(new IntRange(0, 255)));
    FragmentedAutomaton<String> fa = new FragmentedAutomaton<>(dfa, classifier);
    RegexTokenizerSpec<String> spec = new RegexTokenizerSpec<>(fa, Set.of("TOKEN"));
    assertSame(fa, spec.automaton());
  }

  @Test
  void labels_returnsLabelSet() {
    Automaton<String, Integer> dfa = Automaton.create();
    dfa.newState();
    dfa.setInitialStateId(0);
    BinarySearchClassifier classifier =
        new BinarySearchClassifier(java.util.List.of(new IntRange(0, 255)));
    FragmentedAutomaton<String> fa = new FragmentedAutomaton<>(dfa, classifier);
    Set<String> labels = Set.of("A", "B");
    RegexTokenizerSpec<String> spec = new RegexTokenizerSpec<>(fa, labels);
    assertEquals(labels, spec.labels());
  }

  @Test
  void labels_fromBuilder_containsAllRuleLabels() throws SyntaxException {
    RegexTokenizerSpec<String> spec =
        new RegexTokenizerSpecBuilder<String>()
            .add("NUMBER", "[0-9]+")
            .add("IDENT", "[a-z]+")
            .add("WS", "[ ]+")
            .build();
    assertEquals(Set.of("NUMBER", "IDENT", "WS"), spec.labels());
  }

  @Test
  void labels_fromBuilder_preservesInsertionOrder() throws SyntaxException {
    RegexTokenizerSpec<String> spec =
        new RegexTokenizerSpecBuilder<String>().add("C", "c").add("A", "a").add("B", "b").build();
    // LinkedHashSet preserves insertion order
    String[] labels = spec.labels().toArray(new String[0]);
    assertArrayEquals(new String[] {"C", "A", "B"}, labels);
  }
}
