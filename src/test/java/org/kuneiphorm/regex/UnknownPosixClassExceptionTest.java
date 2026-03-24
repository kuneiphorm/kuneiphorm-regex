package org.kuneiphorm.regex;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class UnknownPosixClassExceptionTest {

  @Test
  void getClassName_returnsClassName() {
    UnknownPosixClassException ex = new UnknownPosixClassException(null, 1, 5, "foobar");
    assertEquals("foobar", ex.getClassName());
  }

  @Test
  void getMessage_containsClassName() {
    UnknownPosixClassException ex = new UnknownPosixClassException("test.re", 1, 5, "foobar");
    assertTrue(ex.getMessage().contains("foobar"));
    assertTrue(ex.getMessage().contains("test.re"));
  }

  @Test
  void getSource_returnsSource() {
    UnknownPosixClassException ex = new UnknownPosixClassException("test.re", 1, 5, "foobar");
    assertEquals("test.re", ex.getSource());
  }

  @Test
  void getLine_returnsLine() {
    UnknownPosixClassException ex = new UnknownPosixClassException(null, 3, 7, "xyz");
    assertEquals(3, ex.getLine());
  }

  @Test
  void getColumn_returnsColumn() {
    UnknownPosixClassException ex = new UnknownPosixClassException(null, 3, 7, "xyz");
    assertEquals(7, ex.getColumn());
  }
}
