package org.kuneiphorm.regex;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.kuneiphorm.daedalus.automaton.Automaton;
import org.kuneiphorm.daedalus.automaton.State;
import org.kuneiphorm.daedalus.automaton.Transition;
import org.kuneiphorm.daedalus.range.Classifier;
import org.kuneiphorm.daedalus.range.FragmentedAutomaton;
import org.kuneiphorm.daedalus.range.IntRange;
import org.kuneiphorm.runtime.exception.SyntaxException;

class RegexTokenizerSpecBuilderTest {

  // Simulate the fragmented DFA on a string input.
  private static <L> L simulate(RegexTokenizerSpec<L> spec, String input) {
    FragmentedAutomaton<L> fa = spec.automaton();
    Automaton<L, Integer> dfa = fa.dfa();
    Classifier classifier = fa.classifier();

    State<L, Integer> current = dfa.getInitial();
    for (int i = 0; i < input.length(); i++) {
      int classId = classifier.classify(input.charAt(i));
      if (classId < 0) return null;

      State<L, Integer> next = null;
      for (Transition<L, Integer> t : current.getTransitions()) {
        if (t.label() == classId) {
          next = t.target();
          break;
        }
      }
      if (next == null) return null;
      current = next;
    }

    return current.getOutput();
  }

  @Test
  void build_singleRule_acceptsMatch() throws SyntaxException {
    RegexTokenizerSpec<String> spec =
        new RegexTokenizerSpecBuilder<String>().add("DIGIT", "[0-9]+").build();
    assertEquals("DIGIT", simulate(spec, "42"));
    assertEquals("DIGIT", simulate(spec, "0"));
    assertNull(simulate(spec, "abc"));
  }

  @Test
  void build_multipleRules_firstRuleWins() throws SyntaxException {
    RegexTokenizerSpec<String> spec =
        new RegexTokenizerSpecBuilder<String>().add("KEYWORD", "if").add("IDENT", "[a-z]+").build();
    assertEquals("KEYWORD", simulate(spec, "if"));
    assertEquals("IDENT", simulate(spec, "abc"));
    assertEquals("IDENT", simulate(spec, "iff"));
  }

  @Test
  void build_alternation_acceptsBothBranches() throws SyntaxException {
    RegexTokenizerSpec<String> spec =
        new RegexTokenizerSpecBuilder<String>().add("AB", "a|b").build();
    assertEquals("AB", simulate(spec, "a"));
    assertEquals("AB", simulate(spec, "b"));
    assertNull(simulate(spec, "c"));
  }

  @Test
  void build_quantifiers_workCorrectly() throws SyntaxException {
    RegexTokenizerSpec<String> spec =
        new RegexTokenizerSpecBuilder<String>().add("NUM", "[0-9]+").build();
    assertNull(simulate(spec, ""));
    assertEquals("NUM", simulate(spec, "1"));
    assertEquals("NUM", simulate(spec, "123"));
  }

  @Test
  void build_star_acceptsEmpty() throws SyntaxException {
    RegexTokenizerSpec<String> spec =
        new RegexTokenizerSpecBuilder<String>().add("OPT", "a*").build();
    assertEquals("OPT", simulate(spec, ""));
    assertEquals("OPT", simulate(spec, "a"));
    assertEquals("OPT", simulate(spec, "aaa"));
  }

  @Test
  void build_repetitionBounds_acceptsCorrectLength() throws SyntaxException {
    RegexTokenizerSpec<String> spec =
        new RegexTokenizerSpecBuilder<String>().add("HEX4", "[0-9a-f]{4}").build();
    assertEquals("HEX4", simulate(spec, "dead"));
    assertEquals("HEX4", simulate(spec, "0000"));
    assertNull(simulate(spec, "abc")); // too short
    assertNull(simulate(spec, "abcde")); // too long (only 4 matched)
  }

  @Test
  void build_repetitionRange_acceptsVariableLength() throws SyntaxException {
    RegexTokenizerSpec<String> spec =
        new RegexTokenizerSpecBuilder<String>().add("DIGITS", "[0-9]{2,4}").build();
    assertNull(simulate(spec, "1")); // too short
    assertEquals("DIGITS", simulate(spec, "12"));
    assertEquals("DIGITS", simulate(spec, "123"));
    assertEquals("DIGITS", simulate(spec, "1234"));
  }

  @Test
  void build_labels_containsAllRules() throws SyntaxException {
    RegexTokenizerSpec<String> spec =
        new RegexTokenizerSpecBuilder<String>()
            .add("DIGIT", "[0-9]+")
            .add("LETTER", "[a-z]+")
            .build();
    assertEquals(java.util.Set.of("DIGIT", "LETTER"), spec.labels());
  }

  @Test
  void build_defineClass_affectsRegex() throws SyntaxException {
    RegexTokenizerSpecBuilder<String> builder = new RegexTokenizerSpecBuilder<>();
    builder.defineClass("alpha", java.util.List.of(new IntRange('x', 'z')));
    builder.add("ALPHA", "[[:alpha:]]+");
    RegexTokenizerSpec<String> spec = builder.build();
    assertEquals("ALPHA", simulate(spec, "xyz"));
    assertNull(simulate(spec, "abc")); // not in custom alpha
  }

  @Test
  void build_characterClass_rangesWork() throws SyntaxException {
    RegexTokenizerSpec<String> spec =
        new RegexTokenizerSpecBuilder<String>().add("HEX", "[0-9a-fA-F]+").build();
    assertEquals("HEX", simulate(spec, "deadBEEF"));
    assertEquals("HEX", simulate(spec, "42"));
    assertNull(simulate(spec, "xyz"));
  }
}
