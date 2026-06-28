/*
 * $Id$
 *
 * Copyright (C) 2003-2015 JNode.org
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; If not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package org.jnode.desktop.javafx;

import java.awt.BorderLayout;
import java.awt.Container;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import org.apache.log4j.Logger;
import org.jnode.awt.JNodeAwtContext;
import org.jnode.awt.JNodeToolkit;

/**
 * JavaFX backed desktop shell.
 * <p>
 * This desktop intentionally uses reflection for JavaFX classes. That keeps the
 * JNode GUI project buildable when JavaFX is absent from the compiler classpath,
 * while still using JavaFX at runtime when a JavaFX-capable Java 8 runtime is
 * available. If JavaFX cannot be loaded, the classic Swing desktop is started as
 * a compatibility fallback.
 * </p>
 *
 * @author OpenAI
 */
public final class JavaFxDesktop implements Runnable {

    private static final Logger log = Logger.getLogger(JavaFxDesktop.class);

    private static final String STYLE_ROOT =
        "-fx-background-color: linear-gradient(to bottom right, #07111f, #102a43, #1d4ed8);";

    private static final String STYLE_TOP_BAR =
        "-fx-background-color: rgba(2, 6, 23, 0.48); -fx-padding: 12 18 12 18; " +
        "-fx-spacing: 12; -fx-alignment: center-left;";

    private static final String STYLE_BRAND =
        "-fx-text-fill: white; -fx-font-size: 20px; -fx-font-weight: bold;";

    private static final String STYLE_CENTER =
        "-fx-alignment: center; -fx-padding: 42; -fx-spacing: 24;";

    private static final String STYLE_TITLE =
        "-fx-text-fill: white; -fx-font-size: 38px; -fx-font-weight: bold;";

    private static final String STYLE_SUBTITLE =
        "-fx-text-fill: rgba(255,255,255,0.82); -fx-font-size: 15px;";

    private static final String STYLE_GRID =
        "-fx-hgap: 18; -fx-vgap: 18; -fx-alignment: center;";

    private static final String STYLE_CARD =
        "-fx-background-color: rgba(255,255,255,0.14); -fx-background-radius: 24; " +
        "-fx-padding: 22; -fx-min-width: 170; -fx-min-height: 116; -fx-alignment: center; " +
        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.22), 18, 0.12, 0, 6);";

    private static final String STYLE_CARD_TITLE =
        "-fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold;";

    private static final String STYLE_CARD_TEXT =
        "-fx-text-fill: rgba(255,255,255,0.72); -fx-font-size: 12px;";

    private static final String STYLE_BUTTON =
        "-fx-background-color: rgba(255,255,255,0.16); -fx-text-fill: white; " +
        "-fx-background-radius: 22; -fx-padding: 10 24 10 24; -fx-font-size: 14px;";

    private static final String STYLE_DOCK =
        "-fx-background-color: rgba(15, 23, 42, 0.62); -fx-padding: 14; " +
        "-fx-spacing: 12; -fx-alignment: center;";

    /**
     * Start the JavaFX desktop on top of the JNode AWT context.
     */
    public void run() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                startOnSwingThread();
            }
        });
    }

    private void startOnSwingThread() {
        try {
            final JComponent fxPanel = createJfxPanel();
            final JNodeAwtContext desktopFrame = JNodeToolkit.getJNodeToolkit().getAwtContext();
            final Container awtRoot = desktopFrame.getAwtRoot();
            awtRoot.removeAll();
            awtRoot.setLayout(new BorderLayout());
            awtRoot.add(fxPanel, BorderLayout.CENTER);
            awtRoot.invalidate();
            awtRoot.repaint();

            runLater(new Runnable() {
                public void run() {
                    try {
                        installScene(fxPanel);
                    } catch (Throwable ex) {
                        log.error("Cannot initialize JavaFX scene", ex);
                    }
                }
            });
        } catch (Throwable ex) {
            log.error("JavaFX is not available; falling back to the classic desktop", ex);
            new org.jnode.desktop.classic.Desktop().run();
        }
    }

    private JComponent createJfxPanel() throws Exception {
        final Class panelClass = Class.forName("javafx.embed.swing.JFXPanel");
        return (JComponent) panelClass.newInstance();
    }

    private void runLater(Runnable runnable) throws Exception {
        final Class platformClass = Class.forName("javafx.application.Platform");
        final Method runLater = platformClass.getMethod("runLater", new Class[] {Runnable.class});
        runLater.invoke(null, new Object[] {runnable});
    }

    private void installScene(JComponent fxPanel) throws Exception {
        final Object root = create("javafx.scene.layout.BorderPane");
        setStyle(root, STYLE_ROOT);

        setBorderRegion(root, "setTop", buildTopBar());
        setBorderRegion(root, "setCenter", buildDesktopContent());
        setBorderRegion(root, "setBottom", buildDock());

        final Class parentClass = Class.forName("javafx.scene.Parent");
        final Class sceneClass = Class.forName("javafx.scene.Scene");
        final Constructor sceneConstructor = sceneClass.getConstructor(new Class[] {parentClass});
        final Object scene = sceneConstructor.newInstance(new Object[] {root});
        invoke(fxPanel, "setScene", new Class[] {sceneClass}, new Object[] {scene});

        playFadeIn(root, 360.0d);
    }

    private Object buildTopBar() throws Exception {
        final Object bar = createHBox(12.0d);
        setStyle(bar, STYLE_TOP_BAR);
        addChild(bar, styledLabel("JNode", STYLE_BRAND));
        addChild(bar, styledLabel("JavaFX desktop", STYLE_SUBTITLE));
        return bar;
    }

    private Object buildDesktopContent() throws Exception {
        final Object content = createVBox(24.0d);
        setStyle(content, STYLE_CENTER);
        addChild(content, styledLabel("Welcome to JNode", STYLE_TITLE));
        addChild(content, styledLabel("A modern JavaFX shell running inside the JNode GUI context", STYLE_SUBTITLE));

        final Object grid = create("javafx.scene.layout.TilePane");
        setStyle(grid, STYLE_GRID);
        addChild(grid, buildLauncherCard("Terminal", "Open shell tools"));
        addChild(grid, buildLauncherCard("Files", "Browse mounted devices"));
        addChild(grid, buildLauncherCard("Settings", "Customize the desktop"));
        addChild(grid, buildLauncherCard("Tests", "Run GUI demos"));
        addChild(content, grid);
        return content;
    }

    private Object buildLauncherCard(String title, String description) throws Exception {
        final Object card = createVBox(8.0d);
        setStyle(card, STYLE_CARD);
        addChild(card, styledLabel(title, STYLE_CARD_TITLE));
        addChild(card, styledLabel(description, STYLE_CARD_TEXT));
        return card;
    }

    private Object buildDock() throws Exception {
        final Object dock = createHBox(12.0d);
        setStyle(dock, STYLE_DOCK);
        addChild(dock, styledButton("Applications", null));
        addChild(dock, styledButton("Refresh", new Runnable() {
            public void run() {
                JNodeToolkit.refreshGui();
            }
        }));
        addChild(dock, styledButton("Exit", new Runnable() {
            public void run() {
                JNodeToolkit.stopGui();
            }
        }));
        return dock;
    }

    private Object styledLabel(String text, String style) throws Exception {
        final Object label = createWithString("javafx.scene.control.Label", text);
        setStyle(label, style);
        return label;
    }

    private Object styledButton(String text, Runnable action) throws Exception {
        final Object button = createWithString("javafx.scene.control.Button", text);
        setStyle(button, STYLE_BUTTON);
        if (action != null) {
            setAction(button, action);
        }
        return button;
    }

    private Object createHBox(double spacing) throws Exception {
        return createWithDouble("javafx.scene.layout.HBox", spacing);
    }

    private Object createVBox(double spacing) throws Exception {
        return createWithDouble("javafx.scene.layout.VBox", spacing);
    }

    private Object create(String className) throws Exception {
        return Class.forName(className).newInstance();
    }

    private Object createWithString(String className, String value) throws Exception {
        final Class type = Class.forName(className);
        final Constructor constructor = type.getConstructor(new Class[] {String.class});
        return constructor.newInstance(new Object[] {value});
    }

    private Object createWithDouble(String className, double value) throws Exception {
        final Class type = Class.forName(className);
        final Constructor constructor = type.getConstructor(new Class[] {Double.TYPE});
        return constructor.newInstance(new Object[] {new Double(value)});
    }

    private void setBorderRegion(Object borderPane, String method, Object node) throws Exception {
        final Class nodeClass = Class.forName("javafx.scene.Node");
        invoke(borderPane, method, new Class[] {nodeClass}, new Object[] {node});
    }

    private void setStyle(Object node, String style) throws Exception {
        invoke(node, "setStyle", new Class[] {String.class}, new Object[] {style});
    }

    private void addChild(Object parent, Object child) throws Exception {
        final Object children = invoke(parent, "getChildren", new Class[0], new Object[0]);
        final Method add = children.getClass().getMethod("add", new Class[] {Object.class});
        add.invoke(children, new Object[] {child});
    }

    private void setAction(Object button, final Runnable action) throws Exception {
        final Class handlerClass = Class.forName("javafx.event.EventHandler");
        final Object handler = Proxy.newProxyInstance(handlerClass.getClassLoader(), new Class[] {handlerClass},
            new InvocationHandler() {
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    if ("handle".equals(method.getName())) {
                        action.run();
                    }
                    return null;
                }
            });
        invoke(button, "setOnAction", new Class[] {handlerClass}, new Object[] {handler});
    }

    private void playFadeIn(Object node, double millisValue) throws Exception {
        final Class durationClass = Class.forName("javafx.util.Duration");
        final Method millis = durationClass.getMethod("millis", new Class[] {Double.TYPE});
        final Object duration = millis.invoke(null, new Object[] {new Double(millisValue)});
        final Class nodeClass = Class.forName("javafx.scene.Node");
        final Class fadeClass = Class.forName("javafx.animation.FadeTransition");
        final Constructor constructor = fadeClass.getConstructor(new Class[] {durationClass, nodeClass});
        final Object transition = constructor.newInstance(new Object[] {duration, node});
        invoke(transition, "setFromValue", new Class[] {Double.TYPE}, new Object[] {new Double(0.0d)});
        invoke(transition, "setToValue", new Class[] {Double.TYPE}, new Object[] {new Double(1.0d)});
        invoke(transition, "play", new Class[0], new Object[0]);
    }

    private Object invoke(Object target, String name, Class[] types, Object[] args) throws Exception {
        final Method method = target.getClass().getMethod(name, types);
        return method.invoke(target, args);
    }
}
