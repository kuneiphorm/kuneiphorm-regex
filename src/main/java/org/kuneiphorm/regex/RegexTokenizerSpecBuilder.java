package org.kuneiphorm.regex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.kuneiphorm.daedalus.automaton.Automaton;
import org.kuneiphorm.daedalus.automaton.State;
import org.kuneiphorm.daedalus.automaton.Transition;
import org.kuneiphorm.daedalus.core.Expression;
import org.kuneiphorm.daedalus.craft.AlphabetFragmenter;
import org.kuneiphorm.daedalus.craft.ExpressionConverter;
import org.kuneiphorm.daedalus.craft.Minimizer;
import org.kuneiphorm.daedalus.craft.RangeDeterminizer;
import org.kuneiphorm.daedalus.craft.Trimmer;
import org.kuneiphorm.daedalus.range.FragmentedAutomaton;
import org.kuneiphorm.daedalus.range.IntRange;
import org.kuneiphorm.runtime.exception.SyntaxException;

/**
 * Builds a {@link RegexTokenizerSpec} from named regex rules.
 *
 * <p>Rules are collected via {@link #add(Object, String)} or {@link #add(Object, Expression)},
 * POSIX classes can be customized via {@link #defineClass(String, List)}, then the full compilation
 * pipeline runs when {@link #build()} is called. The first rule added has the highest priority
 * (lower index = higher priority), matching {@link
 * org.kuneiphorm.daedalus.craft.RangeDeterminizer}'s priority-based output resolution.
 *
 * <p>The build pipeline is: Thompson NFA -> {@link RangeDeterminizer} -> {@link Trimmer} -> {@link
 * Minimizer} -> {@link AlphabetFragmenter}.
 *
 * @param <L> the token label type
 * @author Florent Guille
 * @since 0.1.0
 */
public class RegexTokenizerSpecBuilder<L> {

  private record RegexPattern<L>(L label, Expression<IntRange> expression) {}

  private final List<RegexPattern<L>> rules;
  private final RegexParser parser;

  /** Creates a new builder. */
  public RegexTokenizerSpecBuilder() {
    rules = new ArrayList<>();
    parser = new RegexParser();
  }

  /**
   * Registers or overrides a POSIX character class used when parsing regex strings.
   *
   * @param name the POSIX class name (e.g. {@code "alpha"})
   * @param ranges the ranges that define the class
   * @return this builder, for chaining
   */
  public RegexTokenizerSpecBuilder<L> defineClass(String name, List<IntRange> ranges) {
    parser.defineClass(name, ranges);
    return this;
  }

  /**
   * Adds a rule from a regex string.
   *
   * @param label the token label for this rule
   * @param regex the regex pattern
   * @return this builder, for chaining
   * @throws SyntaxException if the regex is malformed
   */
  public RegexTokenizerSpecBuilder<L> add(L label, String regex) throws SyntaxException {
    Objects.requireNonNull(label, "label");
    Objects.requireNonNull(regex, "regex");
    return add(label, parser.parse(regex));
  }

  /**
   * Adds a rule from a pre-parsed expression.
   *
   * @param label the token label for this rule
   * @param expression the expression tree (must use {@link IntRange} unit labels)
   * @return this builder, for chaining
   */
  public RegexTokenizerSpecBuilder<L> add(L label, Expression<IntRange> expression) {
    Objects.requireNonNull(label, "label");
    Objects.requireNonNull(expression, "expression");
    rules.add(new RegexPattern<>(label, expression));
    return this;
  }

  /**
   * Builds the tokenizer specification from the collected rules.
   *
   * <p>Pipeline: combined NFA -> determinize (range-aware, priority-based) -> trim -> minimize ->
   * fragment.
   *
   * @return the compiled tokenizer specification
   */
  public RegexTokenizerSpec<L> build() {
    Automaton<L, IntRange> nfa = Automaton.create();
    State<L, IntRange> root = nfa.newState();
    nfa.setInitialStateId(root.getId());

    Map<L, Integer> priority = new HashMap<>();
    for (int i = 0; i < rules.size(); i++) {
      RegexPattern<L> rule = rules.get(i);
      Automaton<L, IntRange> ruleNfa = ExpressionConverter.build(rule.expression(), rule.label());
      State<L, IntRange> ruleInitial = ruleNfa.getInitial();

      // Map rule NFA states into the combined NFA.
      Map<Integer, State<L, IntRange>> stateMap = new HashMap<>();
      for (State<L, IntRange> state : ruleNfa.getStates()) {
        stateMap.put(state.getId(), nfa.newState(state.getOutput()));
      }

      // Copy transitions.
      for (State<L, IntRange> state : ruleNfa.getStates()) {
        State<L, IntRange> mapped = stateMap.get(state.getId());
        for (Transition<L, IntRange> t : state.getTransitions()) {
          State<L, IntRange> target = stateMap.get(t.target().getId());
          if (t.isEpsilon()) {
            mapped.addEpsilonTransition(target);
          } else {
            mapped.addTransition(t.label(), target);
          }
        }
      }

      // Connect root to the rule's initial state via epsilon.
      root.addEpsilonTransition(stateMap.get(ruleInitial.getId()));
      priority.put(rule.label(), i);
    }

    Automaton<L, IntRange> dfa = RangeDeterminizer.determinize(nfa, priority);
    Automaton<L, IntRange> trimmed = Trimmer.trim(dfa);
    Automaton<L, IntRange> minimized = Minimizer.minimize(trimmed);
    FragmentedAutomaton<L> fragmented = AlphabetFragmenter.fragment(minimized);

    Set<L> labels = new LinkedHashSet<>();
    for (RegexPattern<L> rule : rules) {
      labels.add(rule.label());
    }
    return new RegexTokenizerSpec<>(fragmented, Collections.unmodifiableSet(labels));
  }
}
