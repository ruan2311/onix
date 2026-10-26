package com.onix.controle;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;

import java.net.Inet4Address;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Procura TVs na rede de casa: toda Google TV com o serviço de controle anuncia
 * "_androidtvremote2._tcp" (mDNS). O NsdManager resolve um serviço por vez, por isso a fila.
 */
final class Finder {

    interface Listener {
        /** nome -> IP das TVs achadas até agora. */
        void found(Map<String, String> tvs);
    }

    private static final String TYPE = "_androidtvremote2._tcp.";

    private final NsdManager nsd;
    private final Listener listener;
    private final Map<String, String> tvs = new LinkedHashMap<String, String>();
    private final ArrayDeque<NsdServiceInfo> queue = new ArrayDeque<NsdServiceInfo>();
    private boolean resolving, running;
    private NsdManager.DiscoveryListener discovery;

    Finder(Context ctx, Listener listener) {
        nsd = (NsdManager) ctx.getSystemService(Context.NSD_SERVICE);
        this.listener = listener;
    }

    synchronized void start() {
        if (running || nsd == null) return;
        running = true;
        discovery = new NsdManager.DiscoveryListener() {
            public void onStartDiscoveryFailed(String type, int code) {
                synchronized (Finder.this) {
                    running = false;
                }
            }

            public void onStopDiscoveryFailed(String type, int code) {
            }

            public void onDiscoveryStarted(String type) {
            }

            public void onDiscoveryStopped(String type) {
            }

            public void onServiceFound(NsdServiceInfo info) {
                synchronized (Finder.this) {
                    queue.add(info);
                    next();
                }
            }

            public void onServiceLost(NsdServiceInfo info) {
                synchronized (Finder.this) {
                    tvs.remove(info.getServiceName());
                }
                publish();
            }
        };
        try {
            nsd.discoverServices(TYPE, NsdManager.PROTOCOL_DNS_SD, discovery);
        } catch (Exception e) {
            running = false;
        }
    }

    synchronized void stop() {
        if (!running) return;
        running = false;
        queue.clear();
        try {
            nsd.stopServiceDiscovery(discovery);
        } catch (Exception ignored) {
        }
    }

    private void next() {
        if (resolving || queue.isEmpty()) return;
        resolving = true;
        NsdServiceInfo info = queue.poll();
        try {
            nsd.resolveService(info, new NsdManager.ResolveListener() {
                public void onResolveFailed(NsdServiceInfo i, int code) {
                    done();
                }

                public void onServiceResolved(NsdServiceInfo i) {
                    if (i.getHost() instanceof Inet4Address) {
                        synchronized (Finder.this) {
                            tvs.put(i.getServiceName(), i.getHost().getHostAddress());
                        }
                        publish();
                    }
                    done();
                }
            });
        } catch (Exception e) {
            resolving = false;
        }
    }

    private synchronized void done() {
        resolving = false;
        if (running) next();
    }

    private void publish() {
        Map<String, String> copy;
        synchronized (this) {
            copy = new LinkedHashMap<String, String>(tvs);
        }
        listener.found(copy);
    }
}
