/*
 * Copyright (c) 2008 Sun Microsystems, Inc. All Rights Reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 
 * - Redistribution of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 * 
 * - Redistribution in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 * 
 * Neither the name of Sun Microsystems, Inc. or the names of
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 * 
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES,
 * INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. SUN
 * MICROSYSTEMS, INC. ("SUN") AND ITS LICENSORS SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES. IN NO EVENT WILL SUN OR
 * ITS LICENSORS BE LIABLE FOR ANY LOST REVENUE, PROFIT OR DATA, OR FOR
 * DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE
 * DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY,
 * ARISING OUT OF THE USE OF OR INABILITY TO USE THIS SOFTWARE, EVEN IF
 * SUN HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 * 
 */

package com.sun.javafx.newt;

import javax.media.opengl.*;
import com.sun.opengl.impl.GLDrawableHelper;

/**
 * An implementation of {@link Window} which is customized for OpenGL
 * use, and which implements the {@link
 * javax.media.opengl.GLAutoDrawable} interface. For convenience, this
 * window class guarantees that its OpenGL context is current inside
 * the various EventListeners' callbacks (MouseListener, KeyListener,
 * etc.).
 */
public class GLWindow extends Window implements GLAutoDrawable {
    /**
     * Event handling mode: EVENT_HANDLER_GL_NONE.
     * No GL context is current, while calling the EventListener.
     * This might be inconvenient, but don't impact the performance.
     *
     * @see com.sun.javafx.newt.GLWindow#setEventHandlerMode(int)
     */
    public static final int EVENT_HANDLER_GL_NONE    =       0;

    /**
     * Event handling mode: EVENT_HANDLER_GL_CURRENT.
     * The GL context is made current, while calling the EventListener.
     * This might be convenient, but impacts the performance
     * due to context switches.
     *
     * This is the default setting!
     *
     * @see com.sun.javafx.newt.GLWindow#setEventHandlerMode(int)
     */
    public static final int EVENT_HANDLER_GL_CURRENT = (1 << 0);

    private Window window;

    /** Constructor. Do not call this directly -- use {@link
        create()} instead. */
    protected GLWindow(Window window, GLCapabilities caps) {
        this.window = window;
        this.caps = caps;
        window.addWindowListener(new WindowListener() {
                public void windowResized(WindowEvent e) {
                    sendReshape = true;
                }

                public void windowMoved(WindowEvent e) {
                }
            });
    }

    /** Creates a new GLWindow on the local display, screen 0, with a
        dummy visual ID, and with the default GLCapabilities. */
    public static GLWindow create() {
        return create(null, null);
    }

    /** Creates a new GLWindow referring to the given window. */
    public static GLWindow create(Window window) {
        return create(window, null);
    }

    /** Creates a new GLWindow on the local display, screen 0, with a
        dummy visual ID, and with the given GLCapabilities. */
    public static GLWindow create(GLCapabilities caps) {
        return create(null, caps);
    }

    /** Creates a new GLWindow referring to the given window, and with the given GLCapabilities. */
    public static GLWindow create(Window window, GLCapabilities caps) {
        if (window == null) {
            Display display = NewtFactory.createDisplay(null); // local display
            Screen screen  = NewtFactory.createScreen(display, 0); // screen 0
            window = NewtFactory.createWindow(screen, 0); // dummy VisualID
        }
        if (caps == null) {
            caps = new GLCapabilities();
        }

        return new GLWindow(window, caps);
    }
    
    public boolean isTerminalObject() {
        shouldNotCallThis();
        return false;
    }

    protected void createNative() {
        shouldNotCallThis();
    }

    protected void closeNative() {
        shouldNotCallThis();
    }

    public void close() {
        if (context != null) {
            if (context == GLContext.getCurrent()) {
                context.release();
            }
            context.destroy();
        }
        if (drawable != null) {
            drawable.destroy();
        }

        window.close();
    }

    public int getDisplayWidth() {
        return window.getDisplayWidth();
    }

    public int getDisplayHeight() {
        return window.getDisplayHeight();
    }

    /**
     * Sets the event handling mode.
     *
     * @see com.sun.javafx.newt.GLWindow#EVENT_HANDLER_GL_NONE
     * @see com.sun.javafx.newt.GLWindow#EVENT_HANDLER_GL_CURRENT
     */
    public void setEventHandlerMode(int mode) {
        eventHandlerMode = mode;
    }

    public int getEventHandlerMode() {
        return eventHandlerMode;
    }

    public void pumpMessages(int eventMask) {
        if( 0 == (eventHandlerMode & EVENT_HANDLER_GL_CURRENT) ) {
            window.pumpMessages(eventMask);
        } else {
            pumpMessagesWithEventMaskAction.eventMask = eventMask;
            pumpMessagesImpl(pumpMessagesWithEventMaskAction);
        }
    }

    public void pumpMessages() {
        if( 0 == (eventHandlerMode & EVENT_HANDLER_GL_CURRENT) ) {
            System.err.println("pump direct");
            window.pumpMessages();
        } else {
            System.err.println("pump indirect with GL");
            pumpMessagesImpl(pumpMessagesAction);
        }
    }

    class PumpMessagesWithEventMaskAction implements Runnable {
        private int eventMask;

        public void run() {
            window.pumpMessages(eventMask);
        }
    }
    private PumpMessagesWithEventMaskAction pumpMessagesWithEventMaskAction = new PumpMessagesWithEventMaskAction();

    class PumpMessagesAction implements Runnable {
        public void run() {
            window.pumpMessages();
        }
    }
    private PumpMessagesAction pumpMessagesAction = new PumpMessagesAction();

    private void pumpMessagesImpl(Runnable pumpMessagesAction) {
        //        pumpMessagesAction.run();

        boolean autoSwapBuffer = helper.getAutoSwapBufferMode();
        helper.setAutoSwapBufferMode(false);
        try {
            helper.invokeGL(drawable, context, pumpMessagesAction, initAction);
        } finally {
            helper.setAutoSwapBufferMode(autoSwapBuffer);
        }

    }

    protected void dispatchMessages(int eventMask) {
        shouldNotCallThis();
    }

    public void setVisible(boolean visible) {
        window.setVisible(visible);
        if (visible && context == null) {
            factory = GLDrawableFactory.getFactory(window);
            drawable = factory.createGLDrawable(window, caps, null);
            window.setVisible(true);
            drawable.setRealized(true);
            context = drawable.createContext(null);
        }
    }

    public void setSize(int width, int height) {
        window.setSize(width, height);
    }

    public void setPosition(int x, int y) {
        window.setPosition(x, y);
    }

    public boolean setFullscreen(boolean fullscreen) {
        return window.setFullscreen(fullscreen);
    }

    public boolean isVisible() {
        return window.isVisible();
    }

    public int getX() {
        return window.getX();
    }

    public int getY() {
        return window.getY();
    }

    public int getWidth() {
        return window.getWidth();
    }

    public int getHeight() {
        return window.getHeight();
    }

    public boolean isFullscreen() {
        return window.isFullscreen();
    }

    public void addMouseListener(MouseListener l) {
        window.addMouseListener(l);
    }

    public void removeMouseListener(MouseListener l) {
        window.removeMouseListener(l);
    }

    public MouseListener[] getMouseListeners() {
        return window.getMouseListeners();
    }

    public void addKeyListener(KeyListener l) {
        window.addKeyListener(l);
    }

    public void removeKeyListener(KeyListener l) {
        window.removeKeyListener(l);
    }

    public KeyListener[] getKeyListeners() {
        return window.getKeyListeners();
    }

    public void addWindowListener(WindowListener l) {
        window.addWindowListener(l);
    }

    public void removeWindowListener(WindowListener l) {
        window.removeWindowListener(l);
    }

    public WindowListener[] getWindowListeners() {
        return window.getWindowListeners();
    }

    //----------------------------------------------------------------------
    // OpenGL-related methods and state
    //

    private int eventHandlerMode = EVENT_HANDLER_GL_CURRENT;
    private GLDrawableFactory factory;
    private GLCapabilities caps;
    private GLDrawable drawable;
    private GLContext context;
    private GLDrawableHelper helper = new GLDrawableHelper();
    // To make reshape events be sent immediately before a display event
    private boolean sendReshape;

    public GLDrawableFactory getFactory() {
        return factory;
    }

    public GLContext getContext() {
        return context;
    }

    public GL getGL() {
        GLContext ctx = getContext();
        if (ctx == null) {
            return null;
        }
        return ctx.getGL();
    }

    public void setGL(GL gl) {
        GLContext ctx = getContext();
        if (ctx != null) {
            ctx.setGL(gl);
        }
    }

    public void addGLEventListener(GLEventListener listener) {
        helper.addGLEventListener(listener);
    }

    public void removeGLEventListener(GLEventListener listener) {
        helper.removeGLEventListener(listener);
    }

    public void display() {
        pumpMessages();
        helper.invokeGL(drawable, context, displayAction, initAction);
    }

    public void setAutoSwapBufferMode(boolean onOrOff) {
        helper.setAutoSwapBufferMode(onOrOff);
    }

    public boolean getAutoSwapBufferMode() {
        return helper.getAutoSwapBufferMode();
    }

    public void swapBuffers() {
        drawable.swapBuffers();
    }

    class InitAction implements Runnable {
        public void run() {
            helper.init(GLWindow.this);
        }
    }
    private InitAction initAction = new InitAction();

    class DisplayAction implements Runnable {
        public void run() {
            if (sendReshape) {
                int width = getWidth();
                int height = getHeight();
                getGL().glViewport(0, 0, width, height);
                helper.reshape(GLWindow.this, 0, 0, width, height);
                sendReshape = false;
            }

            helper.display(GLWindow.this);
        }
    }

    private DisplayAction displayAction = new DisplayAction();

    //----------------------------------------------------------------------
    // GLDrawable methods that are not really needed
    //

    public GLContext createContext(GLContext shareWith) {
        return drawable.createContext(shareWith);
    }

    public void setRealized(boolean realized) {
    }

    public void destroy() {
        close();
    }
    
    public GLCapabilities getChosenGLCapabilities() {
        if (drawable == null)
            return null;

        return drawable.getChosenGLCapabilities();
    }

    public void setChosenGLCapabilities(GLCapabilities caps) {
        drawable.setChosenGLCapabilities(caps);
    }

    public NativeWindow getNativeWindow() {
        return drawable.getNativeWindow();
    }

    public int lockSurface() throws GLException {
        return drawable.lockSurface();
    }

    public void unlockSurface() {
        drawable.unlockSurface();
    }

    public boolean isSurfaceLocked() {
        return drawable.isSurfaceLocked();
    }

    //----------------------------------------------------------------------
    // Internals only below this point
    //

    private void shouldNotCallThis() {
        throw new RuntimeException("Should not call this");
    }
}