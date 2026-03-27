package org.kuneiphorm.regex;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.kuneiphorm.daedalus.core.Expression;
import org.kuneiphorm.daedalus.core.ExpressionChoice;
import org.kuneiphorm.daedalus.core.ExpressionQuantifier;
import org.kuneiphorm.daedalus.core.ExpressionSequence;
import org.kuneiphorm.daedalus.core.ExpressionUnit;
import org.kuneiphorm.daedalus.range.IntRange;
import org.kuneiphorm.runtime.exception.SyntaxException;
import org.kuneiphorm.runtime.exception.UnexpectedCharException;
import org.kuneiphorm.runtime.exception.UnexpectedEndOfInputException;

class RegexParserTest {

  private final RegexParser parser = new RegexParser();

  // --- Literal characters ---

  @Test
  void parse_singleChar_returnsUnit() throws SyntaxException {
    Expression<IntRange> expr = parser.parse("a");
    assertInstanceOf(ExpressionUnit.class, expr);
    assertEquals(new IntRange('a', 'a'), ((ExpressionUnit<IntRange>) expr).label());
  }

  @Test
  void parse_concatenation_returnsSequence() throws SyntaxException {
    Expression<IntRange> expr = parser.parse("ab");
    assertInstanceOf(ExpressionSequence.class, expr);
    assertEquals(2, expr.getChildren().size());
  }

  @Test
  void parse_alternation_returnsChoice() throws SyntaxException {
    Expression<IntRange> expr = parser.parse("a|b");
    assertInstanceOf(ExpressionChoice.class, expr);
    assertEquals(2, ((ExpressionChoice<IntRange>) expr).alternatives().size());
  }

  // --- Quantifiers ---

  @Test
  void parse_star_returnsStarQuantifier() throws SyntaxException {
    Expression<IntRange> expr = parser.parse("a*");
    assertInstanceOf(ExpressionQuantifier.class, expr);
    assertEquals(ExpressionQuantifier.Kind.STAR, ((ExpressionQuantifier<IntRange>) expr).kind());
  }

  @Test
  void parse_plus_returnsPlusQuantifier() throws SyntaxException {
    Expression<IntRange> expr = parser.parse("a+");
    assertInstanceOf(ExpressionQuantifier.class, expr);
    assertEquals(ExpressionQuantifier.Kind.PLUS, ((ExpressionQuantifier<IntRange>) expr).kind());
  }

  @Test
  void parse_optional_returnsOptionalQuantifier() throws SyntaxException {
    Expression<IntRange> expr = parser.parse("a?");
    assertInstanceOf(ExpressionQuantifier.class, expr);
    assertEquals(
        ExpressionQuantifier.Kind.OPTIONAL, ((ExpressionQuantifier<IntRange>) expr).kind());
  }

  // --- Groups ---

  @Test
  void parse_group_returnsInnerExpression() throws SyntaxException {
    Expression<IntRange> expr = parser.parse("(a|b)c");
    assertInstanceOf(ExpressionSequence.class, expr);
    Expression<IntRange> first = expr.getChildren().get(0);
    assertInstanceOf(ExpressionChoice.class, first);
  }

  @Test
  void parse_unclosedGroup_throws() {
    assertThrows(UnexpectedEndOfInputException.class, () -> parser.parse("(a"));
  }

  // --- Character classes ---

  @Test
  void parse_simpleClass_returnsUnit() throws SyntaxException {
    Expression<IntRange> expr = parser.parse("[a]");
    assertInstanceOf(ExpressionUnit.class, expr);
    assertEquals(new IntRange('a', 'a'), ((ExpressionUnit<IntRange>) expr).label());
  }

  @Test
  void parse_rangeClass_returnsUnit() throws SyntaxException {
    Expression<IntRange> expr = parser.parse("[a-z]");
    assertInstanceOf(ExpressionUnit.class, expr);
    assertEquals(new IntRange('a', 'z'), ((ExpressionUnit<IntRange>) expr).label());
  }

  @Test
  void parse_multiRangeClass_returnsChoice() throws SyntaxException {
    Expression<IntRange> expr = parser.parse("[a-zA-Z]");
    assertInstanceOf(ExpressionChoice.class, expr);
    assertEquals(2, ((ExpressionChoice<IntRange>) expr).alternatives().size());
  }

  @Test
  void parse_negatedClass_negatesRange() throws SyntaxException {
    Expression<IntRange> expr = parser.parse("[^a]");
    // Should produce a choice of ranges covering everything except 'a'
    assertInstanceOf(ExpressionChoice.class, expr);
    // Verify 'a' is NOT in any of the ranges (negation worked)
    ExpressionChoice<IntRange> choice = (ExpressionChoice<IntRange>) expr;
    for (Expression<IntRange> alt : choice.alternatives()) {
      IntRange range = ((ExpressionUnit<IntRange>) alt).label();
      assertFalse(range.contains('a'), "Negated class should not contain 'a'");
    }
  }

  @Test
  void parse_trailingDash_includesDash() throws SyntaxException {
    Expression<IntRange> expr = parser.parse("[a-]");
    assertInstanceOf(ExpressionChoice.class, expr);
  }

  @Test
  void parse_unclosedClass_throws() {
    assertThrows(UnexpectedEndOfInputException.class, () -> parser.parse("[a"));
  }

  // --- Dot ---

  @Test
  void parse_dot_matchesAnyExceptNewline() throws SyntaxException {
    Expression<IntRange> expr = parser.parse(".");
    // Should produce a choice of ranges covering all except \n
    assertInstanceOf(ExpressionChoice.class, expr);
  }

  // --- Escape sequences ---

  @Test
  void parse_escapedN_returnsNewline() throws SyntaxException {
    Expression<IntRange> expr = parser.parse("\\n");
    assertInstanceOf(ExpressionUnit.class, expr);
    assertEquals(new IntRange('\n', '\n'), ((ExpressionUnit<IntRange>) expr).label());
  }

  @Test
  void parse_escapedT_returnsTab() throws SyntaxException {
    Expression<IntRange> expr = parser.parse("\\t");
    assertInstanceOf(ExpressionUnit.class, expr);
    assertEquals(new IntRange('\t', '\t'), ((ExpressionUnit<IntRange>) expr).label());
  }

  @Test
  void parse_escapedR_returnsCarriageReturn() throws SyntaxException {
    Expression<IntRange> expr = parser.parse("\\r");
    assertInstanceOf(ExpressionUnit.class, expr);
    assertEquals(new IntRange('\r', '\r'), ((ExpressionUnit<IntRange>) expr).label());
  }

  @Test
  void parse_escapedLiteral_returnsLiteral() throws SyntaxException {
    Expression<IntRange> expr = parser.parse("\\*");
    assertInstanceOf(ExpressionUnit.class, expr);
    assertEquals(new IntRange('*', '*'), ((ExpressionUnit<IntRange>) expr).label());
  }

  @Test
  void parse_hexEscape_returnsCodepoint() throws SyntaxException {
    Expression<IntRange> expr = parser.parse("\\x41");
    assertInstanceOf(ExpressionUnit.class, expr);
    assertEquals(new IntRange('A', 'A'), ((ExpressionUnit<IntRange>) expr).label());
  }

  @Test
  void parse_invalidHexEscape_throws() {
    assertThrows(UnexpectedCharException.class, () -> parser.parse("\\xGG"));
  }

  @Test
  void parse_truncatedEscape_throws() {
    assertThrows(UnexpectedEndOfInputException.class, () -> parser.parse("\\"));
  }

  // --- Shorthand classes ---

  @Test
  void parse_digitShorthand_returnsRange() throws SyntaxException {
    Expression<IntRange> expr = parser.parse("\\d");
    assertInstanceOf(ExpressionUnit.class, expr);
    assertEquals(new IntRange('0', '9'), ((ExpressionUnit<IntRange>) expr).label());
  }

  @Test
  void parse_nonDigitShorthand_returnsChoice() throws SyntaxException {
    Expression<IntRange> expr = parser.parse("\\D");
    assertInstanceOf(ExpressionChoice.class, expr);
  }

  @Test
  void parse_wordShorthand_returnsChoice() throws SyntaxException {
    Expression<IntRange> expr = parser.parse("\\w");
    assertInstanceOf(ExpressionChoice.class, expr);
  }

  @Test
  void parse_nonWordShorthand_returnsChoice() throws SyntaxException {
    Expression<IntRange> expr = parser.parse("\\W");
    assertInstanceOf(ExpressionChoice.class, expr);
  }

  @Test
  void parse_spaceShorthand_returnsChoice() throws SyntaxException {
    Expression<IntRange> expr = parser.parse("\\s");
    assertInstanceOf(ExpressionChoice.class, expr);
  }

  @Test
  void parse_nonSpaceShorthand_returnsChoice() throws SyntaxException {
    Expression<IntRange> expr = parser.parse("\\S");
    assertInstanceOf(ExpressionChoice.class, expr);
  }

  // --- POSIX classes ---

  @Test
  void parse_posixAlpha_returnsChoice() throws SyntaxException {
    Expression<IntRange> expr = parser.parse("[[:alpha:]]");
    assertInstanceOf(ExpressionChoice.class, expr);
  }

  @Test
  void parse_posixDigit_returnsUnit() throws SyntaxException {
    Expression<IntRange> expr = parser.parse("[[:digit:]]");
    assertInstanceOf(ExpressionUnit.class, expr);
    assertEquals(new IntRange('0', '9'), ((ExpressionUnit<IntRange>) expr).label());
  }

  @Test
  void parse_unknownPosixClass_throws() {
    assertThrows(UnknownPosixClassException.class, () -> parser.parse("[[:foobar:]]"));
  }

  // --- Complex patterns ---

  @Test
  void parse_complexPattern_doesNotThrow() throws SyntaxException {
    // identifier: [a-zA-Z_][a-zA-Z0-9_]*
    Expression<IntRange> expr = parser.parse("[a-zA-Z_][a-zA-Z0-9_]*");
    assertInstanceOf(ExpressionSequence.class, expr);
    assertEquals(2, expr.getChildren().size());
  }

  @Test
  void parse_emptyAlternative_returnsEmptySequence() throws SyntaxException {
    Expression<IntRange> expr = parser.parse("a|");
    assertInstanceOf(ExpressionChoice.class, expr);
    ExpressionChoice<IntRange> choice = (ExpressionChoice<IntRange>) expr;
    assertEquals(2, choice.alternatives().size());
    // Second alternative is empty sequence
    assertInstanceOf(ExpressionSequence.class, choice.alternatives().get(1));
  }

  @Test
  void parse_trailingInput_throws() {
    assertThrows(UnexpectedCharException.class, () -> parser.parse("a)"));
  }

  // --- Escape in character class ---

  @Test
  void parse_escapedCharInClass_returnsRange() throws SyntaxException {
    Expression<IntRange> expr = parser.parse("[\\n]");
    assertInstanceOf(ExpressionUnit.class, expr);
    assertEquals(new IntRange('\n', '\n'), ((ExpressionUnit<IntRange>) expr).label());
  }

  // --- All POSIX classes ---

  @Test
  void parse_posixSpace_doesNotThrow() throws SyntaxException {
    assertNotNull(parser.parse("[[:space:]]"));
  }

  @Test
  void parse_posixUpper_doesNotThrow() throws SyntaxException {
    assertNotNull(parser.parse("[[:upper:]]"));
  }

  @Test
  void parse_posixLower_doesNotThrow() throws SyntaxException {
    assertNotNull(parser.parse("[[:lower:]]"));
  }

  @Test
  void parse_posixPrint_doesNotThrow() throws SyntaxException {
    assertNotNull(parser.parse("[[:print:]]"));
  }

  @Test
  void parse_posixGraph_doesNotThrow() throws SyntaxException {
    assertNotNull(parser.parse("[[:graph:]]"));
  }

  @Test
  void parse_posixPunct_doesNotThrow() throws SyntaxException {
    assertNotNull(parser.parse("[[:punct:]]"));
  }

  @Test
  void parse_posixBlank_doesNotThrow() throws SyntaxException {
    assertNotNull(parser.parse("[[:blank:]]"));
  }

  @Test
  void parse_posixCntrl_doesNotThrow() throws SyntaxException {
    assertNotNull(parser.parse("[[:cntrl:]]"));
  }

  @Test
  void parse_posixXdigit_doesNotThrow() throws SyntaxException {
    assertNotNull(parser.parse("[[:xdigit:]]"));
  }

  @Test
  void parse_posixAscii_doesNotThrow() throws SyntaxException {
    assertNotNull(parser.parse("[[:ascii:]]"));
  }

  @Test
  void parse_posixAlnum_doesNotThrow() throws SyntaxException {
    assertNotNull(parser.parse("[[:alnum:]]"));
  }

  // --- Unicode escape ---

  @Test
  void parse_unicodeEscape_returnsCodepoint() throws SyntaxException {
    Expression<IntRange> expr = parser.parse("\\u0041");
    assertInstanceOf(ExpressionUnit.class, expr);
    assertEquals(new IntRange('A', 'A'), ((ExpressionUnit<IntRange>) expr).label());
  }

  // --- Dash edge cases in character class ---

  @Test
  void parse_leadingDashInClass_includesDash() throws SyntaxException {
    Expression<IntRange> expr = parser.parse("[-a]");
    assertInstanceOf(ExpressionChoice.class, expr);
  }

  @Test
  void parse_dashRangeEndingWithBracket_includesDashAndChar() throws SyntaxException {
    // [a-] -- trailing dash
    Expression<IntRange> expr = parser.parse("[a-]");
    assertInstanceOf(ExpressionChoice.class, expr);
  }

  // --- Negated class ---

  @Test
  void parse_negatedCharClass_producesComplement() throws SyntaxException {
    Expression<IntRange> expr = parser.parse("[^a-z]");
    assertInstanceOf(ExpressionChoice.class, expr);
  }

  // --- POSIX edge cases ---

  @Test
  void parse_bracketNotPosix_treatedAsLiteral() throws SyntaxException {
    // [[] -- bracket inside a class, not followed by ':'
    Expression<IntRange> expr = parser.parse("[[]");
    assertInstanceOf(ExpressionUnit.class, expr);
    assertEquals(new IntRange('[', '['), ((ExpressionUnit<IntRange>) expr).label());
  }

  @Test
  void parse_truncatedPosixClass_throws() {
    assertThrows(UnexpectedEndOfInputException.class, () -> parser.parse("[[:alpha"));
  }

  @Test
  void parse_posixMissingClosingBracket_throws() {
    assertThrows(SyntaxException.class, () -> parser.parse("[[:alpha:x"));
  }

  // --- Empty input ---

  @Test
  void parse_emptyInput_returnsEmptySequence() throws SyntaxException {
    Expression<IntRange> expr = parser.parse("");
    assertInstanceOf(ExpressionSequence.class, expr);
    assertTrue(expr.getChildren().isEmpty());
  }

  // --- Character class dash edge cases ---

  @Test
  void parse_dashAtStartFollowedByChar_treatedAsLiteral() throws SyntaxException {
    // [-a] -- dash at start is literal
    Expression<IntRange> expr = parser.parse("[-a]");
    assertInstanceOf(ExpressionChoice.class, expr);
  }

  @Test
  void parse_charDashBracket_trailingDashIncluded() throws SyntaxException {
    // [a-] -- 'a' then trailing dash: should contain 'a' and '-'
    Expression<IntRange> expr = parser.parse("[a-]");
    assertInstanceOf(ExpressionChoice.class, expr);
    ExpressionChoice<IntRange> choice = (ExpressionChoice<IntRange>) expr;
    // Verify both '-' and 'a' are present as separate units
    boolean hasDash = false;
    boolean hasA = false;
    for (Expression<IntRange> alt : choice.alternatives()) {
      IntRange range = ((ExpressionUnit<IntRange>) alt).label();
      if (range.contains('-')) hasDash = true;
      if (range.contains('a')) hasA = true;
    }
    assertTrue(hasDash, "Should contain dash");
    assertTrue(hasA, "Should contain 'a'");
  }

  @Test
  void parse_negatedClassEmpty_throws() {
    assertThrows(UnexpectedEndOfInputException.class, () -> parser.parse("[^"));
  }

  @Test
  void parse_classWithDashBeforeCloseBracket_includesDash() throws SyntaxException {
    // [a-z-] -- range a-z then trailing dash
    Expression<IntRange> expr = parser.parse("[a-z-]");
    assertInstanceOf(ExpressionChoice.class, expr);
  }

  // --- Error paths ---

  @Test
  void parse_truncatedHexEscape_throws() {
    assertThrows(UnexpectedEndOfInputException.class, () -> parser.parse("\\x4"));
  }

  @Test
  void parse_truncatedUnicodeEscape_throws() {
    assertThrows(UnexpectedEndOfInputException.class, () -> parser.parse("\\u004"));
  }

  @Test
  void parse_classWithEscapedDash_treatsAsRange() throws SyntaxException {
    // [a\-z] -- escaped dash is literal
    Expression<IntRange> expr = parser.parse("[a\\-z]");
    assertNotNull(expr);
  }

  // --- Error paths: EOF in various positions ---

  @Test
  void parse_eofInBase_throws() {
    // readBase called when flow is exhausted (e.g. quantifier after nothing parseable)
    assertThrows(SyntaxException.class, () -> parser.parse("("));
  }

  @Test
  void parse_eofAfterOpenBracket_throws() {
    assertThrows(UnexpectedEndOfInputException.class, () -> parser.parse("["));
  }

  @Test
  void parse_eofInsideClassAfterChar_throws() {
    // [a with no closing bracket
    assertThrows(UnexpectedEndOfInputException.class, () -> parser.parse("[a"));
  }

  @Test
  void parse_eofAfterBackslashInClass_throws() {
    // [\  with no character after backslash
    assertThrows(UnexpectedEndOfInputException.class, () -> parser.parse("[\\"));
  }

  @Test
  void parse_eofAfterDashInClass_throws() {
    // [a- with no closing bracket and no hi char
    assertThrows(UnexpectedEndOfInputException.class, () -> parser.parse("[a-"));
  }

  @Test
  void parse_posixMissingClosingColon_throws() {
    // [[:alpha] -- missing second ':'
    assertThrows(UnexpectedEndOfInputException.class, () -> parser.parse("[[:alpha]"));
  }

  @Test
  void parseCharFlow_ioException_propagates() {
    java.io.Reader broken =
        new java.io.Reader() {
          @Override
          public int read(char[] cbuf, int off, int len) throws java.io.IOException {
            throw new java.io.IOException("test");
          }

          @Override
          public void close() {}
        };
    org.kuneiphorm.runtime.charflow.CharFlow flow =
        new org.kuneiphorm.runtime.charflow.CharFlow(broken);
    assertThrows(java.io.IOException.class, () -> parser.parse(flow));
  }

  @Test
  void parse_groupClosedWithWrongChar_throws() {
    // (a] -- group not closed with ')'
    assertThrows(SyntaxException.class, () -> parser.parse("(a]"));
  }

  @Test
  void parse_unclosedClassWithContent_throws() {
    // [abc -- no closing ']'
    assertThrows(UnexpectedEndOfInputException.class, () -> parser.parse("[abc"));
  }

  @Test
  void parse_bracketInClassNotPosix_treatedAsLiteral() {
    // [[a]] -- '[' inside class, not followed by ':', so literal '['
    assertDoesNotThrow(() -> parser.parse("[[a]]"));
  }

  @Test
  void parse_posixMissingClosingBracketAfterColon_throws() {
    // [[:alpha:a -- ':' present but ']' missing after second ':'
    assertThrows(SyntaxException.class, () -> parser.parse("[[:alpha:a"));
  }

  // --- Repetition bounds ---

  @Test
  void parse_exactRepetition_returnsSequence() throws SyntaxException {
    Expression<IntRange> expr = parser.parse("a{3}");
    assertInstanceOf(ExpressionSequence.class, expr);
    assertEquals(3, expr.getChildren().size());
  }

  @Test
  void parse_exactRepetitionOne_returnsUnit() throws SyntaxException {
    Expression<IntRange> expr = parser.parse("a{1}");
    // {1} is identity — returns the base directly
    assertInstanceOf(ExpressionUnit.class, expr);
  }

  @Test
  void parse_multiDigitRepetition_parsesCorrectly() throws SyntaxException {
    Expression<IntRange> expr = parser.parse("a{12}");
    assertInstanceOf(ExpressionSequence.class, expr);
    assertEquals(12, expr.getChildren().size());
  }

  @Test
  void parse_repetitionWithZeroDigit_works() throws SyntaxException {
    // Ensures '0' is recognized as a digit (boundary test for isDigit)
    Expression<IntRange> expr = parser.parse("a{0,1}");
    assertInstanceOf(ExpressionSequence.class, expr);
    assertEquals(1, expr.getChildren().size());
    assertInstanceOf(ExpressionQuantifier.class, expr.getChildren().get(0));
  }

  @Test
  void parse_repetitionLeadingZero_parsesCorrectly() throws SyntaxException {
    // {03} should parse as 3 (leading zero in multi-digit)
    Expression<IntRange> expr = parser.parse("a{03}");
    assertInstanceOf(ExpressionSequence.class, expr);
    assertEquals(3, expr.getChildren().size());
  }

  @Test
  void parse_repetitionNineDigit_boundaryCheck() throws SyntaxException {
    // Ensures '9' at boundary is recognized as digit
    Expression<IntRange> expr = parser.parse("a{9}");
    assertInstanceOf(ExpressionSequence.class, expr);
    assertEquals(9, expr.getChildren().size());
  }

  @Test
  void parse_exactRepetitionZero_returnsEmptySequence() throws SyntaxException {
    Expression<IntRange> expr = parser.parse("a{0}");
    assertInstanceOf(ExpressionSequence.class, expr);
    assertTrue(expr.getChildren().isEmpty());
  }

  @Test
  void parse_minRepetition_returnsSequenceWithStar() throws SyntaxException {
    // a{2,} → sequence(a, a, a*)
    Expression<IntRange> expr = parser.parse("a{2,}");
    assertInstanceOf(ExpressionSequence.class, expr);
    assertEquals(3, expr.getChildren().size());
    assertInstanceOf(ExpressionQuantifier.class, expr.getChildren().get(2));
    assertEquals(
        ExpressionQuantifier.Kind.STAR,
        ((ExpressionQuantifier<IntRange>) expr.getChildren().get(2)).kind());
  }

  @Test
  void parse_minZeroRepetition_returnsStar() throws SyntaxException {
    // a{0,} → sequence(a*) → just a*
    Expression<IntRange> expr = parser.parse("a{0,}");
    assertInstanceOf(ExpressionSequence.class, expr);
    assertEquals(1, expr.getChildren().size());
    assertInstanceOf(ExpressionQuantifier.class, expr.getChildren().get(0));
  }

  @Test
  void parse_rangeRepetition_returnsSequenceWithOptionals() throws SyntaxException {
    // a{2,4} → sequence(a, a, a?, a?)
    Expression<IntRange> expr = parser.parse("a{2,4}");
    assertInstanceOf(ExpressionSequence.class, expr);
    assertEquals(4, expr.getChildren().size());
    assertInstanceOf(ExpressionUnit.class, expr.getChildren().get(0));
    assertInstanceOf(ExpressionUnit.class, expr.getChildren().get(1));
    assertInstanceOf(ExpressionQuantifier.class, expr.getChildren().get(2));
    assertEquals(
        ExpressionQuantifier.Kind.OPTIONAL,
        ((ExpressionQuantifier<IntRange>) expr.getChildren().get(2)).kind());
  }

  @Test
  void parse_rangeRepetitionSameMinMax_returnsExact() throws SyntaxException {
    // a{3,3} → sequence(a, a, a)
    Expression<IntRange> expr = parser.parse("a{3,3}");
    assertInstanceOf(ExpressionSequence.class, expr);
    assertEquals(3, expr.getChildren().size());
    for (Expression<IntRange> child : expr.getChildren()) {
      assertInstanceOf(ExpressionUnit.class, child);
    }
  }

  @Test
  void parse_rangeRepetitionMaxLessThanMin_throws() {
    assertThrows(IllegalArgumentException.class, () -> parser.parse("a{3,1}"));
  }

  @Test
  void parse_repetitionMissingDigit_throws() {
    assertThrows(SyntaxException.class, () -> parser.parse("a{,}"));
  }

  @Test
  void parse_repetitionOnGroup_works() throws SyntaxException {
    Expression<IntRange> expr = parser.parse("(ab){2}");
    assertInstanceOf(ExpressionSequence.class, expr);
    assertEquals(2, expr.getChildren().size());
  }

  @Test
  void parse_repetitionOnCharClass_works() throws SyntaxException {
    Expression<IntRange> expr = parser.parse("[0-9]{4}");
    assertInstanceOf(ExpressionSequence.class, expr);
    assertEquals(4, expr.getChildren().size());
  }

  // --- defineClass ---

  @Test
  void defineClass_overridesExisting() throws SyntaxException {
    RegexParser custom = new RegexParser();
    custom.defineClass("digit", java.util.List.of(new IntRange('A', 'F')));
    Expression<IntRange> expr = custom.parse("[[:digit:]]");
    assertInstanceOf(ExpressionUnit.class, expr);
    assertEquals(new IntRange('A', 'F'), ((ExpressionUnit<IntRange>) expr).label());
  }
}
