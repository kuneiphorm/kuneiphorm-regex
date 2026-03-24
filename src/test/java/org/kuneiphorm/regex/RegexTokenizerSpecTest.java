package org.kuneiphorm.regex;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.kuneiphorm.daedalus.automaton.Automaton;
import org.kuneiphorm.daedalus.range.BinarySearchClassifier;
import org.kuneiphorm.daedalus.range.FragmentedAutomaton;
import org.kuneiphorm.daedalus.range.IntRange;

class RegexTokenizerSpecTest {

  @Test
  void automaton_returnsFragmentedAutomaton() {
    Automaton<String, Integer> dfa = Automaton.create();
    dfa.newState();
    dfa.setInitialStateId(0);
    BinarySearchClassifier classifier =
        new BinarySearchClassifier(java.util.List.of(new IntRange(0, 255)));
    FragmentedAutomaton<String> fa = new FragmentedAutomaton<>(dfa, classifier);
    RegexTokenizerSpec<String> spec = new RegexTokenizerSpec<>(fa);
    assertSame(fa, spec.automaton());
  }
}
