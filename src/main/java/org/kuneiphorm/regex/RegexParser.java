package org.kuneiphorm.regex;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.kuneiphorm.daedalus.core.Expression;
import org.kuneiphorm.daedalus.range.IntRange;
import org.kuneiphorm.runtime.charflow.CharFlow;
import org.kuneiphorm.runtime.exception.SyntaxException;
import org.kuneiphorm.runtime.exception.UnexpectedCharException;
import org.kuneiphorm.runtime.exception.UnexpectedEndOfInputException;

/**
 * Recursive-descent parser for regular expressions, producing {@link Expression Expression&lt;
 * IntRange&gt;} trees.
 *
 * <p>{@link #parse(CharFlow)} is the primary API; {@link #parse(String)} is a convenience wrapper.
 *
 * <p>Supported syntax:
 *
 * <ul>
 *   <li>Alternation: {@code a|b}
 *   <li>Concatenation: {@code ab} (juxtaposition)
 *   <li>Quantifiers: {@code ?}, {@code +}, {@code *}
 *   <li>Repetition bounds: {@code {n}}, {@code {n,}}, {@code {n,m}}
 *   <li>Groups: {@code (...)}
 *   <li>Character classes: {@code [a-z]}, {@code [^...]} (negation), trailing dash
 *   <li>Dot: {@code .} (any character except {@code \n})
 *   <li>Shorthand escapes: {@code \d}, {@code \D}, {@code \w}, {@code \W}, {@code \s}, {@code \S}
 *   <li>POSIX classes: {@code [[:alpha:]]}, customizable via {@link #defineClass(String, List)}
 *   <li>Escape sequences: {@code \n}, {@code \t}, {@code \r}, {@code \xHH}, backslash-u HHHH
 * </ul>
 *
 * @author Florent Guille
 * @since 0.1.0
 */
public class RegexParser {

  private static final int MAX_CHAR = 0x10FFFF;

  private final Map<String, List<IntRange>> posixClasses = new HashMap<>();

  /** Creates a new regex parser with the default POSIX class definitions. */
  public RegexParser() {
    defineClass("alpha", List.of(new IntRange('A', 'Z'), new IntRange('a', 'z')));
    defineClass("digit", List.of(new IntRange('0', '9')));
    defineClass(
        "alnum", List.of(new IntRange('A', 'Z'), new IntRange('a', 'z'), new IntRange('0', '9')));
    defineClass("space", List.of(new IntRange(' ', ' '), new IntRange('\t', '\r')));
    defineClass("upper", List.of(new IntRange('A', 'Z')));
    defineClass("lower", List.of(new IntRange('a', 'z')));
    defineClass("print", List.of(new IntRange(0x20, 0x7E)));
    defineClass("graph", List.of(new IntRange(0x21, 0x7E)));
    defineClass(
        "punct",
        List.of(
            new IntRange('!', '/'),
            new IntRange(':', '@'),
            new IntRange('[', '`'),
            new IntRange('{', '~')));
    defineClass("blank", List.of(new IntRange(' ', ' '), new IntRange('\t', '\t')));
    defineClass("cntrl", List.of(new IntRange(0x00, 0x1F), new IntRange(0x7F, 0x7F)));
    defineClass(
        "xdigit", List.of(new IntRange('0', '9'), new IntRange('A', 'F'), new IntRange('a', 'f')));
    defineClass("ascii", List.of(new IntRange(0x00, 0x7F)));
  }

  /**
   * Registers or overrides a POSIX character class.
   *
   * @param name the class name (e.g. {@code "alpha"})
   * @param ranges the character ranges for this class
   */
  public void defineClass(String name, List<IntRange> ranges) {
    posixClasses.put(name, IntRange.normalize(ranges));
  }

  /**
   * Parses a regex from the given string.
   *
   * @param regex the regex string
   * @return the parsed expression tree
   * @throws SyntaxException if the regex is malformed
   */
  public Expression<IntRange> parse(String regex) throws SyntaxException {
    try {
      CharFlow flow = new CharFlow(new StringReader(regex));
      Expression<IntRange> result = parse(flow);
      if (flow.hasMore()) {
        throw new UnexpectedCharException(
            null, flow.getLine(), flow.getColumn(), '\0', (char) flow.peek());
      }
      return result;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Parses a regex from the given {@link CharFlow}.
   *
   * @param flow the character flow to read from
   * @return the parsed expression tree
   * @throws IOException if an I/O error occurs
   * @throws SyntaxException if the regex is malformed
   */
  public Expression<IntRange> parse(CharFlow flow) throws IOException, SyntaxException {
    return readExpression(flow);
  }

  // ---------------------------------------------------------------------------
  // Grammar: E => T ('|' T)*
  // ---------------------------------------------------------------------------

  private Expression<IntRange> readExpression(CharFlow flow) throws IOException, SyntaxException {
    List<Expression<IntRange>> alternatives = new ArrayList<>();
    alternatives.add(readTerm(flow));

    while (flow.accept('|')) {
      alternatives.add(readTerm(flow));
    }

    if (alternatives.size() == 1) {
      return alternatives.get(0);
    }
    return Expression.choice(alternatives);
  }

  // ---------------------------------------------------------------------------
  // Grammar: T => F+
  // ---------------------------------------------------------------------------

  private Expression<IntRange> readTerm(CharFlow flow) throws IOException, SyntaxException {
    List<Expression<IntRange>> elements = new ArrayList<>();

    while (flow.hasMore() && flow.peek() != '|' && flow.peek() != ')') {
      elements.add(readFactor(flow));
    }

    if (elements.isEmpty()) {
      return Expression.sequence();
    }
    if (elements.size() == 1) {
      return elements.get(0);
    }
    return Expression.sequence(elements);
  }

  // ---------------------------------------------------------------------------
  // Grammar: F => B ('+'|'?'|'*'|'{' ... '}')?
  // ---------------------------------------------------------------------------

  private Expression<IntRange> readFactor(CharFlow flow) throws IOException, SyntaxException {
    Expression<IntRange> base = readBase(flow);

    if (flow.accept('+')) {
      return Expression.plus(base);
    }
    if (flow.accept('?')) {
      return Expression.optional(base);
    }
    if (flow.accept('*')) {
      return Expression.star(base);
    }
    if (flow.accept('{')) {
      return readRepetition(flow, base);
    }

    return base;
  }

  /**
   * Reads a repetition quantifier: {@code {n}}, {@code {n,}}, or {@code {n,m}}.
   *
   * <p>Desugars into sequences and optionals:
   *
   * <ul>
   *   <li>{@code {n}} → {@code base} repeated n times
   *   <li>{@code {n,}} → {@code base} repeated n times + {@code base*}
   *   <li>{@code {n,m}} → {@code base} repeated n times + {@code base?} repeated (m-n) times
   * </ul>
   */
  private Expression<IntRange> readRepetition(CharFlow flow, Expression<IntRange> base)
      throws IOException, SyntaxException {
    int min = readInt(flow);

    if (flow.accept('}')) {
      // {n} -- exactly n
      return repeat(base, min);
    }

    flow.expect(',');

    if (flow.accept('}')) {
      // {n,} -- n or more
      List<Expression<IntRange>> parts = new ArrayList<>();
      for (int i = 0; i < min; i++) {
        parts.add(base);
      }
      parts.add(Expression.star(base));
      return Expression.sequence(parts);
    }

    // {n,m} -- between n and m
    int max = readInt(flow);
    flow.expect('}');

    if (max < min) {
      throw new IllegalArgumentException(
          "Invalid repetition: max (" + max + ") < min (" + min + ")");
    }

    List<Expression<IntRange>> parts = new ArrayList<>();
    for (int i = 0; i < min; i++) {
      parts.add(base);
    }
    for (int i = 0; i < max - min; i++) {
      parts.add(Expression.optional(base));
    }
    return Expression.sequence(parts);
  }

  private int readInt(CharFlow flow) throws IOException, SyntaxException {
    if (!flow.hasMore() || !isDigit(flow.peek())) {
      throw new UnexpectedCharException(
          flow.getName(),
          flow.getLine(),
          flow.getColumn(),
          '0',
          flow.hasMore() ? (char) flow.peek() : '?');
    }
    int result = 0;
    while (flow.hasMore() && isDigit(flow.peek())) {
      result = result * 10 + (flow.next() - '0');
    }
    return result;
  }

  private static boolean isDigit(int ch) {
    return ch >= '0' && ch <= '9';
  }

  private Expression<IntRange> repeat(Expression<IntRange> base, int count) {
    if (count == 0) {
      return Expression.sequence();
    }
    if (count == 1) {
      return base;
    }
    List<Expression<IntRange>> parts = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      parts.add(base);
    }
    return Expression.sequence(parts);
  }

  // ---------------------------------------------------------------------------
  // Grammar: B => '(' E ')' | '[' ... ']' | '.' | C
  // ---------------------------------------------------------------------------

  private Expression<IntRange> readBase(CharFlow flow) throws IOException, SyntaxException {
    if (flow.accept('(')) {
      Expression<IntRange> expr = readExpression(flow);
      flow.expect(')');
      return expr;
    }

    if (flow.peek() == '[') {
      return readCharacterClass(flow);
    }

    if (flow.accept('.')) {
      List<IntRange> ranges = IntRange.negate(List.of(new IntRange('\n', '\n')), 0, MAX_CHAR);
      return buildChoice(ranges);
    }

    if (flow.peek() == '\\') {
      return readEscape(flow);
    }

    // Literal character
    int ch = flow.next();
    return Expression.unit(new IntRange(ch, ch));
  }

  // ---------------------------------------------------------------------------
  // Character class: [...]
  // ---------------------------------------------------------------------------

  private Expression<IntRange> readCharacterClass(CharFlow flow)
      throws IOException, SyntaxException {
    flow.expect('[');
    boolean negated = flow.accept('^');

    List<IntRange> ranges = new ArrayList<>();

    while (flow.hasMore() && flow.peek() != ']') {
      // POSIX class: [[:name:]]
      if (flow.peek() == '[') {
        ranges.addAll(readPosixClass(flow));
        continue;
      }

      // Trailing dash before ']'
      if (flow.accept('-')) {
        if (flow.peek() == ']') {
          ranges.add(new IntRange('-', '-'));
          break;
        }
        // dash not at end -- treat as literal
        ranges.add(new IntRange('-', '-'));
        continue;
      }

      int lo = readClassChar(flow);

      if (flow.accept('-')) {
        if (flow.peek() == ']') {
          // trailing dash: a-]
          ranges.add(new IntRange(lo, lo));
          ranges.add(new IntRange('-', '-'));
          break;
        }
        int hi = readClassChar(flow);
        ranges.add(new IntRange(lo, hi));
      } else {
        ranges.add(new IntRange(lo, lo));
      }
    }

    flow.expect(']');

    List<IntRange> normalized = IntRange.normalize(ranges);
    if (negated) {
      normalized = IntRange.negate(normalized, 0, MAX_CHAR);
    }

    return buildChoice(normalized);
  }

  private List<IntRange> readPosixClass(CharFlow flow) throws IOException, SyntaxException {
    int line = flow.getLine();
    int column = flow.getColumn();
    flow.expect('[');

    if (flow.peek() != ':') {
      // Not a POSIX class, treat '[' as literal
      return List.of(new IntRange('[', '['));
    }
    flow.expect(':');

    StringBuilder name = new StringBuilder();
    while (flow.hasMore() && flow.peek() != ':') {
      name.append((char) flow.next());
    }

    flow.expect(':');
    flow.expect(']');

    String className = name.toString();
    List<IntRange> result = posixClasses.get(className);
    if (result == null) {
      throw new UnknownPosixClassException(flow.getName(), line, column, className);
    }
    return result;
  }

  private int readClassChar(CharFlow flow) throws IOException, SyntaxException {
    if (flow.peek() == '\\') {
      return readEscapeCodepoint(flow);
    }
    return flow.next();
  }

  // ---------------------------------------------------------------------------
  // Escape sequences
  // ---------------------------------------------------------------------------

  private Expression<IntRange> readEscape(CharFlow flow) throws IOException, SyntaxException {
    flow.expect('\\');

    if (!flow.hasMore()) {
      throw new UnexpectedEndOfInputException(flow.getName(), flow.getLine(), flow.getColumn());
    }

    int ch = flow.peek();

    // Shorthand character classes
    switch (ch) {
      case 'd':
        flow.next();
        return buildChoice(List.of(new IntRange('0', '9')));
      case 'D':
        flow.next();
        return buildChoice(IntRange.negate(List.of(new IntRange('0', '9')), 0, MAX_CHAR));
      case 'w':
        flow.next();
        return buildChoice(
            List.of(
                new IntRange('0', '9'),
                new IntRange('A', 'Z'),
                new IntRange('_', '_'),
                new IntRange('a', 'z')));
      case 'W':
        flow.next();
        return buildChoice(
            IntRange.negate(
                IntRange.normalize(
                    List.of(
                        new IntRange('0', '9'),
                        new IntRange('A', 'Z'),
                        new IntRange('_', '_'),
                        new IntRange('a', 'z'))),
                0,
                MAX_CHAR));
      case 's':
        flow.next();
        return buildChoice(List.of(new IntRange(' ', ' '), new IntRange('\t', '\r')));
      case 'S':
        flow.next();
        return buildChoice(
            IntRange.negate(
                IntRange.normalize(List.of(new IntRange(' ', ' '), new IntRange('\t', '\r'))),
                0,
                MAX_CHAR));
      default:
        break;
    }

    // Simple escape sequences
    int codepoint = readEscapeCodepointRaw(flow);
    return Expression.unit(new IntRange(codepoint, codepoint));
  }

  private int readEscapeCodepoint(CharFlow flow) throws IOException, SyntaxException {
    flow.expect('\\');

    if (!flow.hasMore()) {
      throw new UnexpectedEndOfInputException(flow.getName(), flow.getLine(), flow.getColumn());
    }

    return readEscapeCodepointRaw(flow);
  }

  private int readEscapeCodepointRaw(CharFlow flow) throws IOException, SyntaxException {
    int ch = flow.next();

    switch (ch) {
      case 'n':
        return '\n';
      case 't':
        return '\t';
      case 'r':
        return '\r';
      case 'x':
        return readHex(flow, 2);
      case 'u':
        return readHex(flow, 4);
      default:
        return ch;
    }
  }

  private int readHex(CharFlow flow, int digits) throws IOException, SyntaxException {
    int result = 0;
    for (int i = 0; i < digits; i++) {
      if (!flow.hasMore()) {
        throw new UnexpectedEndOfInputException(flow.getName(), flow.getLine(), flow.getColumn());
      }
      int ch = flow.next();
      int digit = Character.digit(ch, 16);
      if (digit < 0) {
        throw new UnexpectedCharException(
            flow.getName(), flow.getLine(), flow.getColumn(), '0', (char) ch);
      }
      result = result * 16 + digit;
    }
    return result;
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private Expression<IntRange> buildChoice(List<IntRange> ranges) {
    if (ranges.size() == 1) {
      return Expression.unit(ranges.get(0));
    }
    List<Expression<IntRange>> units = new ArrayList<>();
    for (IntRange range : ranges) {
      units.add(Expression.unit(range));
    }
    return Expression.choice(units);
  }
}
