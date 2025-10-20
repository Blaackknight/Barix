package fr.bck.barix.modules;

import java.util.*;

public final class ModuleManager {
    private final Map<String, Module> modules = new LinkedHashMap<>();
    private boolean started = false;

    public ModuleManager add(Module module) {
        Objects.requireNonNull(module, "module");
        String id = module.id();
        if (modules.containsKey(id)) {
            throw new IllegalArgumentException("Module déjà enregistré: " + id);
        }
        modules.put(id, module);
        return this;
    }

    public synchronized void startAll() {
        if (started) return;
        for (Module m : topoSort()) {
            // Logs de démarrage module
            try {
                fr.bck.barix.BarixConstants.log.info("Modules/" + m.id(), fr.bck.barix.lang.Lang.tr("barix.modules.log.starting", fr.bck.barix.config.BarixServerConfig.CORE_LOCALE.get(), m.id()));
            } catch (Throwable ignored) {
                // en dernier recours, un log brut
                fr.bck.barix.BarixConstants.log.info("Modules/" + m.id(), "Starting module {0}", m.id());
            }
            try {
                m.start();
                try {
                    fr.bck.barix.BarixConstants.log.info("Modules/" + m.id(), fr.bck.barix.lang.Lang.tr("barix.modules.log.started", fr.bck.barix.config.BarixServerConfig.CORE_LOCALE.get(), m.id()));
                } catch (Throwable ignored2) {
                    fr.bck.barix.BarixConstants.log.info("Modules/" + m.id(), "Started module {0}", m.id());
                }
            } catch (Throwable t) {
                // Log erreur démarrage
                fr.bck.barix.BarixConstants.log.error("Modules/" + m.id(), "§cFailed to start module {0}: {1}", m.id(), String.valueOf(t.getMessage()));
            }
        }
        started = true;
    }

    public synchronized void stopAll() {
        if (!started) return;
        List<Module> list = new ArrayList<>(modules.values());
        Collections.reverse(list);
        for (Module m : list) {
            try {
                // Logs d'arrêt module
                try {
                    fr.bck.barix.BarixConstants.log.info("Modules/" + m.id(), fr.bck.barix.lang.Lang.tr("barix.modules.log.stopping", fr.bck.barix.config.BarixServerConfig.CORE_LOCALE.get(), m.id()));
                } catch (Throwable ignored) {
                    fr.bck.barix.BarixConstants.log.info("Modules/" + m.id(), "Stopping module {0}", m.id());
                }
                m.stop();
                try {
                    fr.bck.barix.BarixConstants.log.info("Modules/" + m.id(), fr.bck.barix.lang.Lang.tr("barix.modules.log.stopped", fr.bck.barix.config.BarixServerConfig.CORE_LOCALE.get(), m.id()));
                } catch (Throwable ignored2) {
                    fr.bck.barix.BarixConstants.log.info("Modules/" + m.id(), "Stopped module {0}", m.id());
                }
            } catch (Throwable t) {
                // Log erreur arrêt
                fr.bck.barix.BarixConstants.log.error("Modules/" + m.id(), "§cFailed to stop module {0}: {1}", m.id(), String.valueOf(t.getMessage()));
            }
        }
        started = false;
    }

    public synchronized void clear() {
        modules.clear();
        started = false;
    }

    public synchronized List<String> listIds() {
        return new ArrayList<>(modules.keySet());
    }

    public synchronized boolean isStarted() {
        return started;
    }

    private List<Module> topoSort() {
        // tri simple par dépendances, suffisant pour petite échelle
        Map<String, Module> byId = new LinkedHashMap<>(modules);
        List<Module> out = new ArrayList<>();
        Set<String> temp = new HashSet<>();
        Set<String> perm = new HashSet<>();
        for (Module m : byId.values()) visit(m, byId, temp, perm, out);
        return out;
    }

    private void visit(Module m, Map<String, Module> byId, Set<String> temp, Set<String> perm, List<Module> out) {
        String id = m.id();
        if (perm.contains(id)) return;
        if (temp.contains(id)) throw new IllegalStateException("Cycle de dépendances: " + id);
        temp.add(id);
        for (String dep : m.dependsOn()) {
            Module dm = byId.get(dep);
            if (dm == null) throw new IllegalStateException("Dépendance manquante: " + id + " -> " + dep);
            visit(dm, byId, temp, perm, out);
        }
        temp.remove(id);
        perm.add(id);
        out.add(m);
    }
}
