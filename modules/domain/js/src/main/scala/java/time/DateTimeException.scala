package java.time

class DateTimeException(message: String) extends RuntimeException(message)

package format:
  class DateTimeParseException(message: String, parsed: CharSequence, errorIndex: Int) extends DateTimeException(message)
