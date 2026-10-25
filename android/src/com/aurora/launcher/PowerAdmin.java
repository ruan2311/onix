package com.aurora.launcher;

import android.app.admin.DeviceAdminReceiver;

/**
 * Administrador do dispositivo com uma única política (force-lock), usada só pelo botão
 * "Desligar TV": DevicePolicyManager.lockNow() põe a TV em standby, como o botão de energia.
 * Ativação pelo adb: dpm set-active-admin com.aurora.launcher/.PowerAdmin
 *
 * Única classe que ficou com o nome antigo: o Android guarda a permissão de administrador pelo
 * nome completo da classe, e renomeá-la desativaria o "Desligar TV" de quem já autorizou.
 * O pacote do app (com.aurora.launcher) também continua o mesmo, para as atualizações seguirem
 * instalando por cima; todo o resto do código fica em com.onix.launcher.
 */
public class PowerAdmin extends DeviceAdminReceiver {
}
