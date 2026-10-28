package com.onix.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

/** Resposta do instalador do sistema para a instalação do Onix Game Hub (GameHub.install). */
public class GameHubReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                try {
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    return;
                } catch (Exception ignored) {
                    // cai no erro abaixo
                }
            }
            GameHub.get(context).installFinished(false, "a TV não abriu a confirmação de instalação");
            return;
        }
        if (status == PackageInstaller.STATUS_SUCCESS) {
            GameHub.get(context).installFinished(true, null);
            return;
        }
        String msg = status == PackageInstaller.STATUS_FAILURE_ABORTED ? "instalação cancelada"
                : intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        GameHub.get(context).installFinished(false, msg);
    }
}
