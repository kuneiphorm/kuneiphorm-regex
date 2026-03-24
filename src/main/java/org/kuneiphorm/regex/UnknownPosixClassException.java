package org.kuneiphorm.regex;

import org.kuneiphorm.runtime.exception.SyntaxException;

/**
 * Thrown when the regex parser encounters an unrecognized POSIX character class name inside a
 * {@code [[:name:]]} construct.
 *
 * @author Florent Guille
 * @since 0.1.0
 */
public class UnknownPosixClassException extends SyntaxException {

  private final String className;

  /**
   * Creates a new exception for the given unrecognized POSIX class name.
   *
   * @param source the source name, or {@code null}
   * @param line the line number where the error occurred
   * @param column the column number where the error occurred
   * @param className the unrecognized POSIX class name
   */
  public UnknownPosixClassException(String source, int line, int column, String className) {
    super(source, line, column, className.length());
    this.className = className;
  }

  /**
   * Returns the unrecognized POSIX class name.
   *
   * @return the class name
   */
  public String getClassName() {
    return className;
  }

  @Override
  protected String getSyntaxMessage() {
    return "Unknown POSIX class: " + className;
  }
}
