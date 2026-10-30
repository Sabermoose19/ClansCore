package dev.clans.core.integration;

import java.util.*;

/** Event-scoped tokens let PAPI defer clan rendering until LPC supplies the viewer. */
public final class LpcScope {
  public static final class Context {
    final Object event;
    final UUID sender;
    final Map<ClanPlaceholders.Field, String> tokens;
    final String messageToken;

    Context(Object event, UUID sender) {
      this.event = event;
      this.sender = sender;
      String nonce = UUID.randomUUID().toString().replace("-", "");
      Map<ClanPlaceholders.Field, String> values = new EnumMap<>(ClanPlaceholders.Field.class);
      for (var field : ClanPlaceholders.Field.values())
        values.put(field, "\uE000CLANS" + nonce + field.name() + "\uE001");
      tokens = Map.copyOf(values);
      messageToken = "\uE000CLANSMESSAGE" + nonce + "\uE001";
    }

    public String token(ClanPlaceholders.Field field) {
      return tokens.get(field);
    }
  }

  private final ThreadLocal<Deque<Context>> contexts = new ThreadLocal<>();

  public void begin(Object event, UUID sender) {
    var stack = contexts.get();
    if (stack == null) {
      stack = new ArrayDeque<>();
      contexts.set(stack);
    }
    stack.push(new Context(event, sender));
  }

  public Context end(Object event) {
    var stack = contexts.get();
    if (stack == null || stack.isEmpty() || stack.peek().event != event) return null;
    Context result = stack.pop();
    if (stack.isEmpty()) contexts.remove();
    return result;
  }

  public String token(UUID sender, ClanPlaceholders.Field field) {
    var stack = contexts.get();
    return stack == null || stack.isEmpty() || !stack.peek().sender.equals(sender)
        ? null
        : stack.peek().token(field);
  }
}
