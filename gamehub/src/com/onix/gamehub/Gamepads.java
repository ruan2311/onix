package com.onix.gamehub;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.InputDevice;
import android.view.KeyEvent;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Controles de videogame conectados. O Android avisa na hora quando um controle entra ou sai
 * (sem precisar apertar botão, como o navegador exigiria). Só conta como controle o aparelho que
 * se diz gamepad/joystick E tem os botões A e B: alguns controles remotos se apresentam como
 * "gamepad" e não devem contar.
 */
final class Gamepads implements InputManager.InputDeviceListener {

    interface Listener {
        void gamepadsChanged(int added, int removed);
    }

    private final InputManager im;
    private final Listener listener;

    Gamepads(Context c, Listener l) {
        im = (InputManager) c.getSystemService(Context.INPUT_SERVICE);
        listener = l;
    }

    void start() {
        if (im != null) im.registerInputDeviceListener(this, new Handler(Looper.getMainLooper()));
    }

    void stop() {
        if (im != null) im.unregisterInputDeviceListener(this);
    }

    static boolean isGamepad(InputDevice d) {
        if (d == null || d.isVirtual() || !external(d)) return false;
        int s = d.getSources();
        boolean pad = (s & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (s & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
        if (!pad) return false;
        boolean[] keys = d.hasKeys(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B);
        return keys.length == 2 && keys[0] && keys[1];
    }

    /**
     * Controle de verdade vem de fora (Bluetooth/USB). Algumas TVs têm aparelhos internos que se
     * dizem gamepad com A e B, como o "virtual-search" da Google TV. isExternal() é pública só a
     * partir do Android 10; antes, por reflexão (sem ela, aceita).
     */
    private static boolean external(InputDevice d) {
        try {
            return (Boolean) InputDevice.class.getMethod("isExternal").invoke(d);
        } catch (Exception e) {
            return !d.getName().toLowerCase(java.util.Locale.ROOT).startsWith("virtual");
        }
    }

    /** O Android junta as partes do controle num aparelho só e às vezes fica o nome de uma
     *  delas ("DualSense Wireless Controller Touchpad"): mostra só o nome do controle. */
    static String name(InputDevice d) {
        return d.getName().replaceAll("(?i)\\s+(touchpad|motion sensors)$", "");
    }

    /** Nome curto da tecla para a tela de trocar botões: BUTTON_X → X, DPAD_UP → ▲. */
    static String keyName(int code) {
        String n = KeyEvent.keyCodeToString(code).replace("KEYCODE_", "").replace("BUTTON_", "");
        switch (n) {
            case "DPAD_UP": return "▲";
            case "DPAD_DOWN": return "▼";
            case "DPAD_LEFT": return "◀";
            case "DPAD_RIGHT": return "▶";
            case "DPAD_CENTER": return "OK";
            default: return n.replace('_', ' ');
        }
    }

    static JSONArray list() {
        JSONArray out = new JSONArray();
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice d = InputDevice.getDevice(id);
            if (!isGamepad(d)) continue;
            try {
                out.put(new JSONObject().put("id", id).put("name", name(d)));
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    /** A mesma lista com o controle (1 ou 2) que a pessoa definiu para cada aparelho. */
    static JSONArray list(Context c) {
        JSONArray out = list();
        JSONObject players = Players.all(c);
        for (int i = 0; i < out.length(); i++) {
            JSONObject o = out.optJSONObject(i);
            int port = Players.portOf(players, InputDevice.getDevice(o.optInt("id")));
            try {
                if (port >= 0) o.put("player", port + 1);
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    static int count() {
        return list().length();
    }

    @Override
    public void onInputDeviceAdded(int id) {
        if (isGamepad(InputDevice.getDevice(id)) && listener != null) listener.gamepadsChanged(id, -1);
    }

    @Override
    public void onInputDeviceRemoved(int id) {
        if (listener != null) listener.gamepadsChanged(-1, id);
    }

    @Override
    public void onInputDeviceChanged(int id) {
    }

    /**
     * Abre a tela de parear controles da TV. Cada fabricante esconde num lugar, então tenta em
     * ordem: a de acessórios da Google TV/Android TV, a de Bluetooth do Android e as gerais.
     */
    static boolean openBluetooth(Activity a) {
        Intent[] tries = {
                new Intent("com.google.android.intent.action.CONNECT_INPUT"),
                new Intent().setComponent(new ComponentName("com.android.tv.settings", "com.android.tv.settings.accessories.AddAccessoryActivity")),
                new Intent().setComponent(new ComponentName("com.android.tv.settings", "com.android.tv.settings.connectivity.BluetoothActivity")),
                new Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
                new Intent(Settings.ACTION_SETTINGS),
        };
        for (Intent i : tries) {
            try {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (i.resolveActivity(a.getPackageManager()) == null) continue;
                a.startActivity(i);
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }
}
