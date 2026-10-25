package com.aurora.launcher;

import android.app.admin.DeviceAdminReceiver;

/**
 * Administrador do dispositivo com uma única política (force-lock), usada só pelo botão
 * "Desligar TV": DevicePolicyManager.lockNow() põe a TV em standby, como o botão de energia.
 * Ativação pelo adb: dpm set-active-admin com.aurora.launcher/.PowerAdmin
 */
public class PowerAdmin extends DeviceAdminReceiver {
}
