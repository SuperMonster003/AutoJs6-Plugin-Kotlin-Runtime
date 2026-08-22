package org.autojs.plugin.jvmsource.kotlin.compat;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;

/** Minimal headless substitutes for desktop-only types retained in IntelliJ compiler signatures. */
public final class HeadlessDesktop {

    private HeadlessDesktop() {
    }

    public static class AWTEvent {
    }

    public static class Color {
    }

    public static class Component {
    }

    public static class Window extends Component {
    }

    public static class Dialog extends Window {
    }

    public static class Graphics {
    }

    public static class InvocationEvent extends AWTEvent {
    }

    public static final class EventQueue {
        public static boolean isDispatchThread() {
            return false;
        }

        public static void invokeLater(Runnable runnable) {
            runnable.run();
        }

        public static void invokeAndWait(Runnable runnable)
                throws InterruptedException, InvocationTargetException {
            runnable.run();
        }

        public AWTEvent peekEvent() {
            return null;
        }
    }

    public static final class Toolkit {
        private static final Toolkit INSTANCE = new Toolkit();
        private final EventQueue eventQueue = new EventQueue();

        private Toolkit() {
        }

        public static Toolkit getDefaultToolkit() {
            return INSTANCE;
        }

        public EventQueue getSystemEventQueue() {
            return eventQueue;
        }
    }

    public static final class Rectangle {
        public final int x;
        public final int y;
        public final int width;
        public final int height;

        public Rectangle(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    public static final class Introspector {
        private Introspector() {
        }

        public static String decapitalize(String value) {
            if (value == null || value.isEmpty()) return value;
            if (value.length() > 1 && Character.isUpperCase(value.charAt(0))
                    && Character.isUpperCase(value.charAt(1))) {
                return value;
            }
            return Character.toLowerCase(value.charAt(0)) + value.substring(1);
        }
    }

    public static final class PropertyChangeSupport {
        public PropertyChangeSupport(Object source) {
        }

        public void firePropertyChange(String name, boolean oldValue, boolean newValue) {
        }
    }

    public interface Icon {
    }

    public static class JComponent extends Component {
    }

    public static class RepaintManager {
    }

    public static final class SwingUtilities {
        private SwingUtilities() {
        }

        public static boolean isEventDispatchThread() {
            return false;
        }

        public static void invokeLater(Runnable runnable) {
            runnable.run();
        }

        public static void invokeAndWait(Runnable runnable)
                throws InterruptedException, InvocationTargetException {
            runnable.run();
        }
    }

    public interface MutableAttributeSet {
    }

    public static final class Html {
        private Html() {
        }

        public static class Tag {
            private final String name;
            private final boolean breaksFlow;

            public Tag() {
                this("", false);
            }

            public Tag(String name, boolean breaksFlow) {
                this.name = name;
                this.breaksFlow = breaksFlow;
            }

            public boolean breaksFlow() {
                return breaksFlow;
            }

            @Override
            public String toString() {
                return name;
            }
        }
    }

    public static final class HtmlEditorKit {
        private HtmlEditorKit() {
        }

        public static class ParserCallback {
            public void handleText(char[] data, int position) {
            }

            public void handleComment(char[] data, int position) {
            }

            public void handleStartTag(Html.Tag tag, MutableAttributeSet attributes, int position) {
            }

            public void handleEndTag(Html.Tag tag, int position) {
            }

            public void handleSimpleTag(Html.Tag tag, MutableAttributeSet attributes, int position) {
            }

            public void handleError(String error, int position) {
            }

            public void handleEndOfLineString(String endOfLine) {
            }
        }
    }

    public static final class ParserDelegator {
        public void parse(Reader reader, HtmlEditorKit.ParserCallback callback, boolean ignoreCharset)
                throws IOException {
            StringBuilder text = new StringBuilder();
            char[] buffer = new char[1024];
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                if (read > 0) text.append(buffer, 0, read);
            }
            String plain = text.toString().replaceAll("<[^>]*>", "");
            callback.handleText(plain.toCharArray(), 0);
        }
    }
}
