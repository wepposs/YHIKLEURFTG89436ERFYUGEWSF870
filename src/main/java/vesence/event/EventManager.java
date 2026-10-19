package vesence.event;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class EventManager {
   private static final Map<Class<? extends Event>, EventManager.HandlerBucket> REGISTRY_MAP = new ConcurrentHashMap<>();
   private static final Object REGISTRY_LOCK = new Object();

   public static void register(Object object) {
      for (Method method : object.getClass().getDeclaredMethods()) {
         if (!isMethodBad(method)) {
            register(method, object);
         }
      }
   }

   public static void unregister(Object object) {
      synchronized (REGISTRY_LOCK) {
         for (Entry<Class<? extends Event>, EventManager.HandlerBucket> entry : REGISTRY_MAP.entrySet()) {
            EventManager.HandlerBucket updated = entry.getValue().withoutSource(object);
            if (updated.isEmpty()) {
               REGISTRY_MAP.remove(entry.getKey(), entry.getValue());
            } else if (updated != entry.getValue()) {
               REGISTRY_MAP.put(entry.getKey(), updated);
            }
         }
      }
   }

   private static void register(Method method, Object object) {
      try {
         Class<? extends Event> indexClass = (Class<? extends Event>)method.getParameterTypes()[0];
         if (!method.canAccess(object)) {
            method.setAccessible(true);
         }

         final EventManager.MethodData data = new EventManager.MethodData(object, method, method.getAnnotation(EventInit.class).value());
         synchronized (REGISTRY_LOCK) {
            EventManager.HandlerBucket bucket = REGISTRY_MAP.get(indexClass);
            if (bucket == null) {
               REGISTRY_MAP.put(indexClass, EventManager.HandlerBucket.of(data));
            } else if (!bucket.contains(data)) {
               REGISTRY_MAP.put(indexClass, bucket.with(data));
            }
         }
      } catch (Exception var4) {
         System.err.println("Failed to register event handler: " + method.getName() + " in " + object.getClass().getName());
         var4.printStackTrace();
      }
   }

   public static void cleanMap(boolean onlyEmptyEntries) {
      synchronized (REGISTRY_LOCK) {
         for (Entry<Class<? extends Event>, EventManager.HandlerBucket> entry : REGISTRY_MAP.entrySet()) {
            if (!onlyEmptyEntries || entry.getValue().isEmpty()) {
               REGISTRY_MAP.remove(entry.getKey(), entry.getValue());
            }
         }
      }
   }

   private static void sortListValue(Class<? extends Event> indexClass) {
      synchronized (REGISTRY_LOCK) {
         EventManager.HandlerBucket bucket = REGISTRY_MAP.get(indexClass);
         if (bucket != null) {
            REGISTRY_MAP.put(indexClass, bucket.sorted());
         }
      }
   }

   private static boolean isMethodBad(Method method) {
      return method.getParameterTypes().length != 1 || !method.isAnnotationPresent(EventInit.class);
   }

   private static boolean isMethodBad(Method method, Class<? extends Event> eventClass) {
      return isMethodBad(method) || !method.getParameterTypes()[0].equals(eventClass);
   }

   public static void printRegistryStatus() {
   }

   public static Event call(Event event) {
      EventManager.HandlerBucket bucket = REGISTRY_MAP.get(event.getClass());
      if (bucket != null) {
         EventManager.MethodData[] handlers = bucket.handlers;
         final boolean track = vesence.utils.debug.FreezeWatchdog.TRACKING;
         if (track) {
            vesence.utils.debug.FreezeWatchdog.onEventStart(event.getClass());
         }
         try {
            if (event instanceof EventStoppable stoppable) {
               for (EventManager.MethodData data : handlers) {
                  invoke(data, event, track);
                  if (stoppable.isStopped()) {
                     break;
                  }
               }
            } else {
               for (EventManager.MethodData data : handlers) {
                  invoke(data, event, track);
               }
            }
         } finally {
            if (track) {
               vesence.utils.debug.FreezeWatchdog.onEventEnd();
            }
         }
      }

      return event;
   }

   private static void invoke(EventManager.MethodData data, Event argument, boolean track) {
      long handlerStart = 0L;
      if (track) {
         vesence.utils.debug.FreezeWatchdog.onHandlerEnter(data.getSource(), data.getTarget());
         handlerStart = System.nanoTime();
      }
      try {
         data.invoke(argument);
      } catch (IllegalArgumentException var4) {
         System.err
            .println(
               "[EventManager] Failed to invoke "
                  + data.getTarget().getName()
                  + " on "
                  + data.getSource().getClass().getSimpleName()
                  + ": "
                  + var4.getMessage()
            );
      } catch (InvocationTargetException var5) {
         Throwable cause = var5.getCause();
         System.err
            .println(
               "[EventManager] Exception in handler "
                  + data.getTarget().getName()
                  + " on "
                  + data.getSource().getClass().getSimpleName()
                  + ": "
                  + (cause != null ? cause.getMessage() : var5.getMessage())
            );
         if (cause != null) {
            cause.printStackTrace();
         }
      } catch (Throwable t) {
         System.err.println("[EventManager] Unexpected error in " + data.getTarget().getName() + ": " + t.getMessage());
         t.printStackTrace();
      } finally {
         if (track) {
            vesence.utils.debug.FreezeWatchdog.onHandlerExit(
               data.getSource(), data.getTarget(), System.nanoTime() - handlerStart);
         }
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class HandlerBucket {
      private static final EventManager.MethodData[] EMPTY = new EventManager.MethodData[0];
      private final EventManager.MethodData[] handlers;

      private HandlerBucket(EventManager.MethodData[] handlers) {
         this.handlers = handlers;
      }

      static EventManager.HandlerBucket of(EventManager.MethodData data) {
         return new EventManager.HandlerBucket(new EventManager.MethodData[] {data}).sorted();
      }

      boolean isEmpty() {
         return this.handlers.length == 0;
      }

      boolean contains(EventManager.MethodData data) {
         for (EventManager.MethodData handler : this.handlers) {
            if (handler.equals(data)) {
               return true;
            }
         }
         return false;
      }

      EventManager.HandlerBucket with(EventManager.MethodData data) {
         EventManager.MethodData[] next = new EventManager.MethodData[this.handlers.length + 1];
         System.arraycopy(this.handlers, 0, next, 0, this.handlers.length);
         next[this.handlers.length] = data;
         return new EventManager.HandlerBucket(next).sorted();
      }

      EventManager.HandlerBucket withoutSource(Object source) {
         int kept = 0;
         for (EventManager.MethodData handler : this.handlers) {
            if (!handler.getSource().equals(source)) {
               kept++;
            }
         }
         if (kept == this.handlers.length) {
            return this;
         }
         if (kept == 0) {
            return new EventManager.HandlerBucket(EMPTY);
         }
         EventManager.MethodData[] next = new EventManager.MethodData[kept];
         int index = 0;
         for (EventManager.MethodData handler : this.handlers) {
            if (!handler.getSource().equals(source)) {
               next[index++] = handler;
            }
         }
         return new EventManager.HandlerBucket(next);
      }

      EventManager.HandlerBucket sorted() {
         if (this.handlers.length <= 1) {
            return this;
         }
         EventManager.MethodData[] sorted = new EventManager.MethodData[this.handlers.length];
         int index = 0;
         for (byte priority : Priority.VALUE_ARRAY) {
            for (EventManager.MethodData data : this.handlers) {
               if (data.getPriority() == priority) {
                  sorted[index++] = data;
               }
            }
         }
         if (index < sorted.length) {
            for (EventManager.MethodData data : this.handlers) {
               boolean alreadyAdded = false;
               for (int i = 0; i < index; i++) {
                  if (sorted[i] == data) {
                     alreadyAdded = true;
                     break;
                  }
               }
               if (!alreadyAdded) {
                  sorted[index++] = data;
               }
            }
         }
         return new EventManager.HandlerBucket(sorted);
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class MethodData {
      private final Object source;
      private final Method target;
      private final MethodHandle invoker;
      private final byte priority;

      public MethodData(Object source, Method target, byte priority) throws IllegalAccessException {
         this.source = source;
         this.target = target;
         this.invoker = MethodHandles.lookup().unreflect(target).bindTo(source);
         this.priority = priority;
      }

      public void invoke(Event event) throws Throwable {
         this.invoker.invoke(event);
      }

      public Object getSource() {
         return this.source;
      }

      public Method getTarget() {
         return this.target;
      }

      public byte getPriority() {
         return this.priority;
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) {
            return true;
         } else if (o != null && this.getClass() == o.getClass()) {
            EventManager.MethodData that = (EventManager.MethodData)o;
            return this.priority == that.priority && this.source.equals(that.source) && this.target.equals(that.target);
         } else {
            return false;
         }
      }

      @Override
      public int hashCode() {
         return Objects.hash(this.source, this.target, this.priority);
      }
   }
}
