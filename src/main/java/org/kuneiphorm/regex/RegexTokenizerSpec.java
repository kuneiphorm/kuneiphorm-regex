package org.kuneiphorm.regex;

import java.util.Set;
import org.kuneiphorm.daedalus.range.FragmentedAutomaton;

/**
 * A compiled regex-based tokenizer specification: a fragmented DFA ready for runtime
 * interpretation.
 *
 * <p>Produced by {@link RegexTokenizerSpecBuilder}. The resulting fragmented DFA can be interpreted
 * at runtime or used as code-generation input.
 *
 * @param <L> the token label type
 * @param automaton the fragmented DFA
 * @param labels the set of token labels recognized by this specification
 * @author Florent Guille
 * @since 0.1.0
 */
public record RegexTokenizerSpec<L>(FragmentedAutomaton<L> automaton, Set<L> labels) {}
