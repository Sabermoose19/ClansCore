package dev.clans.core;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public record Settings(
    int minName,
    int maxName,
    int mottoMax,
    int memberLimit,
    long inviteMillis,
    long peaceMillis,
    long renameMillis,
    Set<String> reserved,
    String timezone,
    String dateFormat) {
  public static Settings defaults() {
    return new Settings(
        3,
        16,
        100,
        0,
        600000,
        86400000,
        86400000,
        Set.of("admin", "console", "server", "none", "null"),
        "UTC",
        "yyyy-MM-dd HH:mm z");
  }

  public Settings {
    if (minName < 1
        || maxName < minName
        || maxName > 64
        || mottoMax < 0
        || memberLimit < 0
        || inviteMillis < 1
        || peaceMillis < 1
        || renameMillis < 0) throw new IllegalArgumentException("Invalid limits");
    reserved = Set.copyOf(reserved);
    ZoneId.of(timezone);
    DateTimeFormatter.ofPattern(dateFormat);
  }

  void name(String value) {
    if (value.length() < minName
        || value.length() > maxName
        || !value.matches("[A-Za-z0-9_]+")
        || reserved.stream().anyMatch(value::equalsIgnoreCase))
      throw new IllegalArgumentException(
          "Invalid or reserved clan name ("
              + minName
              + "–"
              + maxName
              + " ASCII letters, digits, underscores).");
  }

  void motto(String value) {
    if (value.length() > mottoMax
        || value
            .codePoints()
            .anyMatch(
                c -> Character.isISOControl(c) || c == '§' || c == '&' || c == '<' || c == '>'))
      throw new IllegalArgumentException(
          "Motto must be plain text, at most "
              + mottoMax
              + " characters; formatting and markup are forbidden.");
  }
}
