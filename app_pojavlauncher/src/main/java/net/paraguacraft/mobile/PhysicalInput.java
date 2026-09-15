package net.paraguacraft.mobile;

import android.content.Context;
import android.hardware.input.InputManager;
import android.os.Build;
import android.view.InputDevice;
import android.view.View;

import net.kdt.pojavlaunch.customcontrols.ControlLayout;

/** Teclado, mouse o mando físico: oculta los controles táctiles. */
public final class PhysicalInput {
    private PhysicalInput() {}

    /** -1 sin averiguar, 0 solo táctil, 1 hay control físico. */
    private static volatile int sCachedExternal = -1;

    /**
     * Recorrer todos los dispositivos de entrada en cada evento de input costaba
     * caro durante la partida. El conjunto solo cambia cuando el InputManager
     * avisa, así que el resultado se guarda hasta entonces.
     */
    public static boolean hasExternalControls() {
        int cached = sCachedExternal;
        if (cached >= 0) return cached == 1;
        boolean found = scanExternalControls();
        sCachedExternal = found ? 1 : 0;
        return found;
    }

    /** Fuerza un nuevo escaneo: se conectó o desconectó un dispositivo. */
    public static void invalidate() {
        sCachedExternal = -1;
    }

    private static boolean scanExternalControls() {
        int[] ids = InputDevice.getDeviceIds();
        if (ids == null) return false;
        for (int id : ids) {
            if (isExternal(InputDevice.getDevice(id))) return true;
        }
        return false;
    }

    public static boolean isExternal(InputDevice device) {
        if (device == null) return false;
        if (Build.VERSION.SDK_INT >= 16 && device.isVirtual()) return false;
        String name = device.getName() == null ? "" : device.getName().toLowerCase();
        if (name.contains("touchscreen") || name.contains("touch_screen") || name.contains("uinput")
                || name.contains("touchkey") || name.contains("fingerprint") || name.contains("gpio-keys")) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= 29 && !device.isExternal()) return false;
        int sources = device.getSources();
        boolean gamepad = (sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
        boolean mouse = (sources & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE
                || (sources & InputDevice.SOURCE_MOUSE_RELATIVE) == InputDevice.SOURCE_MOUSE_RELATIVE;
        boolean keyboard = (sources & InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD
                && device.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC;
        return gamepad || mouse || keyboard;
    }

    public static void applyTouchHud(ControlLayout layout, View drawerPull) {
        boolean hide = hasExternalControls();
        if (layout != null) {
            layout.setControlVisible(!hide);
        }
        if (drawerPull == null) return;
        if (hide) {
            drawerPull.setVisibility(View.GONE);
        } else if (layout != null) {
            drawerPull.setVisibility(layout.hasMenuButton() ? View.GONE : View.VISIBLE);
        }
    }

    public static InputManager.InputDeviceListener watch(Context ctx, Runnable onChange) {
        InputManager im = (InputManager) ctx.getSystemService(Context.INPUT_SERVICE);
        if (im == null) return null;
        InputManager.InputDeviceListener listener = new InputManager.InputDeviceListener() {
            @Override
            public void onInputDeviceAdded(int deviceId) {
                invalidate();
                onChange.run();
            }

            @Override
            public void onInputDeviceRemoved(int deviceId) {
                invalidate();
                onChange.run();
            }

            @Override
            public void onInputDeviceChanged(int deviceId) {
                invalidate();
                onChange.run();
            }
        };
        im.registerInputDeviceListener(listener, null);
        return listener;
    }

    public static void unwatch(Context ctx, InputManager.InputDeviceListener listener) {
        if (listener == null) return;
        InputManager im = (InputManager) ctx.getSystemService(Context.INPUT_SERVICE);
        if (im != null) im.unregisterInputDeviceListener(listener);
    }
}
