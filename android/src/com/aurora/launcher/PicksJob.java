package com.aurora.launcher;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/**
 * Verifica em segundo plano (a cada 6 h, com rede) se a semana virou e baixa as
 * novas indicações, para elas já estarem prontas quando o launcher abrir.
 * Só há download de fato uma vez por semana; nas outras execuções é só uma comparação de data.
 */
public class PicksJob extends JobService {

    static final int ID = 4301;

    static void schedule(Context ctx) {
        JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        for (JobInfo j : js.getAllPendingJobs()) {
            if (j.getId() == ID) return;
        }
        js.schedule(new JobInfo.Builder(ID, new ComponentName(ctx, PicksJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(6 * 60 * 60 * 1000L)
                .setPersisted(true)
                .build());
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                WeeklyPicks.get(PicksJob.this).refreshIfStale();
                jobFinished(params, false);
            }
        }, "onix-picks-job").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
